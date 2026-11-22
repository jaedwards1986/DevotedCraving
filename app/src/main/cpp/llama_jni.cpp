#include <jni.h>
#include <atomic>
#include <algorithm>
#include <cstring>
#include <string>
#include <vector>
#include <mutex>
#include "llama.h"

static llama_model * g_model = nullptr;
static llama_context * g_ctx = nullptr;
static std::atomic<bool> g_cancel{false};
static std::mutex g_mutex;

extern "C" JNIEXPORT jboolean JNICALL
Java_com_natasha_local_llm_LlamaBridge_loadModel(JNIEnv * env, jclass, jstring jpath, jint nctx, jint threads) {
    const char * path = env->GetStringUTFChars(jpath, nullptr);
    if (!path) return JNI_FALSE;

    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_ctx) { llama_free(g_ctx); g_ctx = nullptr; }
    if (g_model) { llama_model_free(g_model); g_model = nullptr; }

    llama_backend_init();

    auto mp = llama_model_default_params();
    mp.n_gpu_layers = 0;
    g_model = llama_model_load_from_file(path, mp);
    env->ReleaseStringUTFChars(jpath, path);
    if (!g_model) return JNI_FALSE;

    auto cp = llama_context_default_params();
    cp.n_ctx = nctx;
    cp.n_threads = threads;
    cp.n_threads_batch = threads;
    cp.n_batch = std::min<uint32_t>(nctx, 2048u);

    g_ctx = llama_init_from_model(g_model, cp);
    if (!g_ctx) {
        llama_model_free(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_natasha_local_llm_LlamaBridge_generate(
        JNIEnv * env, jclass, jstring jprompt, jint max_tokens,
        jfloat temperature, jfloat top_p, jobject callback) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_model || !g_ctx || !callback) return JNI_FALSE;

    // Every prompt starts a fresh sequence. Reusing the KV cache here leaks
    // tokens from the previous chat into the next request.
    llama_memory_clear(llama_get_memory(g_ctx), true);
    g_cancel.store(false);
    jclass cb_class = env->GetObjectClass(callback);
    jmethodID on_token = env->GetMethodID(cb_class, "onToken", "(Ljava/lang/String;)V");
    jmethodID on_done = env->GetMethodID(cb_class, "onDone", "()V");
    jmethodID on_error = env->GetMethodID(cb_class, "onError", "(Ljava/lang/String;)V");
    if (!on_token || !on_done || !on_error) return JNI_FALSE;

    const char * prompt = env->GetStringUTFChars(jprompt, nullptr);
    if (!prompt) return JNI_FALSE;

    const llama_vocab * vocab = llama_model_get_vocab(g_model);
    int32_t n_prompt = -llama_tokenize(vocab, prompt, (int32_t)strlen(prompt), nullptr, 0, true, true);
    if (n_prompt <= 0) {
        env->CallVoidMethod(callback, on_error, env->NewStringUTF("Prompt tokenization failed"));
        env->ReleaseStringUTFChars(jprompt, prompt);
        return JNI_TRUE;
    }

    const int32_t context_limit = static_cast<int32_t>(llama_n_ctx(g_ctx));
    if (n_prompt >= context_limit) {
        jstring message = env->NewStringUTF("Prompt exceeds the loaded model context window");
        env->CallVoidMethod(callback, on_error, message);
        env->DeleteLocalRef(message);
        env->ReleaseStringUTFChars(jprompt, prompt);
        return JNI_TRUE;
    }
    const int32_t generation_limit = std::max<int32_t>(
        0, std::min<int32_t>(max_tokens, context_limit - n_prompt));

    std::vector<llama_token> prompt_tokens(n_prompt);
    if (llama_tokenize(vocab, prompt, (int32_t)strlen(prompt), prompt_tokens.data(), n_prompt, true, true) < 0) {
        env->CallVoidMethod(callback, on_error, env->NewStringUTF("Prompt tokenization failed"));
        env->ReleaseStringUTFChars(jprompt, prompt);
        return JNI_TRUE;
    }

    llama_batch_ext * batch = llama_batch_ext_init(g_ctx);
    if (!batch) {
        env->CallVoidMethod(callback, on_error, env->NewStringUTF("Unable to allocate inference batch"));
        env->ReleaseStringUTFChars(jprompt, prompt);
        return JNI_TRUE;
    }

    auto set_batch = [&](const llama_token * tokens, int32_t count, llama_pos start_pos) -> bool {
        llama_batch_ext_clear(batch);
        for (int32_t i = 0; i < count; ++i) {
            const int32_t idx = llama_batch_ext_add_token(batch, 0, tokens[i]);
            if (idx < 0) return false;
            const llama_pos pos = start_pos + i;
            if (!llama_batch_ext_set_pos(batch, idx, &pos)) return false;
        }
        return llama_batch_ext_set_output_logits(batch, count - 1, true);
    };

    if (!set_batch(prompt_tokens.data(), n_prompt, 0) ||
        llama_process(g_ctx, LLAMA_PROCESS_TYPE_DECODE, batch) != 0) {
        env->CallVoidMethod(callback, on_error, env->NewStringUTF("Prompt decode failed"));
        llama_batch_ext_free(batch);
        env->ReleaseStringUTFChars(jprompt, prompt);
        return JNI_TRUE;
    }

    auto sp = llama_sampler_chain_default_params();
    llama_sampler * sampler = llama_sampler_chain_init(sp);
    if (!sampler) {
        env->CallVoidMethod(callback, on_error, env->NewStringUTF("Unable to create sampler"));
        llama_batch_ext_free(batch);
        env->ReleaseStringUTFChars(jprompt, prompt);
        return JNI_TRUE;
    }

    float temp = temperature;
    if (temp <= 0.0f) {
        llama_sampler_chain_add(sampler, llama_sampler_init_greedy());
    } else {
        llama_sampler_chain_add(sampler, llama_sampler_init_temp(temp));
        llama_sampler_chain_add(sampler, llama_sampler_init_top_p(top_p > 0.0f ? top_p : 0.9f, 1));
        llama_sampler_chain_add(sampler, llama_sampler_init_dist(1234));
    }

    llama_pos pos = n_prompt;
    for (int i = 0; i < generation_limit && !g_cancel.load(); ++i) {
        llama_token next = llama_sampler_sample(sampler, g_ctx, -1);
        if (llama_vocab_is_eog(vocab, next)) break;

        char piece[512];
        int32_t len = llama_token_to_piece(vocab, next, piece, sizeof(piece), 0, true);
        if (len > 0) {
            std::string out(piece, len);
            jstring token_text = env->NewStringUTF(out.c_str());
            if (token_text) {
                env->CallVoidMethod(callback, on_token, token_text);
                env->DeleteLocalRef(token_text);
            }
        }

        if (!set_batch(&next, 1, pos++) ||
            llama_process(g_ctx, LLAMA_PROCESS_TYPE_DECODE, batch) != 0) {
            env->CallVoidMethod(callback, on_error, env->NewStringUTF("Token decode failed"));
            llama_sampler_free(sampler);
            llama_batch_ext_free(batch);
            env->ReleaseStringUTFChars(jprompt, prompt);
            return JNI_TRUE;
        }
    }

    llama_sampler_free(sampler);
    llama_batch_ext_free(batch);
    env->ReleaseStringUTFChars(jprompt, prompt);
    env->CallVoidMethod(callback, on_done);
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_natasha_local_llm_LlamaBridge_cancel(JNIEnv *, jclass) {
    g_cancel.store(true);
}

extern "C" JNIEXPORT void JNICALL
Java_com_natasha_local_llm_LlamaBridge_unload(JNIEnv *, jclass) {
    std::lock_guard<std::mutex> lock(g_mutex);
    g_cancel.store(true);
    if (g_ctx) llama_free(g_ctx);
    if (g_model) llama_model_free(g_model);
    g_ctx = nullptr;
    g_model = nullptr;
}