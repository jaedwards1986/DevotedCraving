#include <jni.h>
#include <cstdint>
#include <atomic>
#include <chrono>
#include <cmath>
#include <iomanip>
#include <sstream>
#include <mutex>
#include <string>
#include <vector>

#include "llama.h"

namespace {

std::mutex g_model_mutex;
llama_model* g_model = nullptr;
llama_context* g_context = nullptr;
std::once_flag g_backend_once;
std::atomic<int64_t> g_decode_deadline_ns{0};

int64_t monotonicNanos() {
    return std::chrono::duration_cast<std::chrono::nanoseconds>(
        std::chrono::steady_clock::now().time_since_epoch()).count();
}

bool decodeAbortCallback(void*) {
    const int64_t deadline = g_decode_deadline_ns.load(std::memory_order_relaxed);
    return deadline > 0 && monotonicNanos() >= deadline;
}

std::string jsonEscape(const std::string& value) {
    std::ostringstream out;
    for (unsigned char ch : value) {
        switch (ch) {
            case '"': out << "\\\""; break;
            case '\\': out << "\\\\"; break;
            case '\b': out << "\\b"; break;
            case '\f': out << "\\f"; break;
            case '\n': out << "\\n"; break;
            case '\r': out << "\\r"; break;
            case '\t': out << "\\t"; break;
            default:
                if (ch < 0x20) out << "\\u" << std::hex << std::setw(4) << std::setfill('0') << static_cast<int>(ch) << std::dec;
                else out << static_cast<char>(ch);
        }
    }
    return out.str();
}

std::string tokenPiece(const llama_vocab* vocab, llama_token token) {
    std::vector<char> buffer(256);
    int32_t count = llama_token_to_piece(vocab, token, buffer.data(), static_cast<int32_t>(buffer.size()), 0, false);
    if (count < 0) {
        buffer.resize(static_cast<size_t>(-count));
        count = llama_token_to_piece(vocab, token, buffer.data(), static_cast<int32_t>(buffer.size()), 0, false);
    }
    return count > 0 ? std::string(buffer.data(), static_cast<size_t>(count)) : std::string();
}

std::string utf16ToUtf8(JNIEnv* env, jstring value) {
    if (value == nullptr) return {};
    const jsize length = env->GetStringLength(value);
    const jchar* chars = env->GetStringChars(value, nullptr);
    if (chars == nullptr) return {};
    std::string output;
    output.reserve(static_cast<size_t>(length) * 3);
    for (jsize i = 0; i < length; ++i) {
        uint32_t cp = chars[i];
        if (cp >= 0xD800 && cp <= 0xDBFF && i + 1 < length) {
            const uint32_t low = chars[i + 1];
            if (low >= 0xDC00 && low <= 0xDFFF) {
                cp = 0x10000 + ((cp - 0xD800) << 10) + (low - 0xDC00);
                ++i;
            } else cp = 0xFFFD;
        } else if (cp >= 0xD800 && cp <= 0xDFFF) cp = 0xFFFD;
        if (cp <= 0x7F) output.push_back(static_cast<char>(cp));
        else if (cp <= 0x7FF) {
            output.push_back(static_cast<char>(0xC0 | (cp >> 6)));
            output.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
        } else if (cp <= 0xFFFF) {
            output.push_back(static_cast<char>(0xE0 | (cp >> 12)));
            output.push_back(static_cast<char>(0x80 | ((cp >> 6) & 0x3F)));
            output.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
        } else {
            output.push_back(static_cast<char>(0xF0 | (cp >> 18)));
            output.push_back(static_cast<char>(0x80 | ((cp >> 12) & 0x3F)));
            output.push_back(static_cast<char>(0x80 | ((cp >> 6) & 0x3F)));
            output.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
        }
    }
    env->ReleaseStringChars(value, chars);
    return output;
}

std::u16string utf8ToUtf16(const std::string& input) {
    std::u16string output;
    for (size_t i = 0; i < input.size();) {
        const uint8_t first = static_cast<uint8_t>(input[i]);
        uint32_t cp = 0xFFFD;
        size_t count = 1;
        if (first <= 0x7F) cp = first;
        else if ((first & 0xE0) == 0xC0 && i + 1 < input.size()) {
            cp = ((first & 0x1F) << 6) | (static_cast<uint8_t>(input[i + 1]) & 0x3F); count = 2;
        } else if ((first & 0xF0) == 0xE0 && i + 2 < input.size()) {
            cp = ((first & 0x0F) << 12) | ((static_cast<uint8_t>(input[i + 1]) & 0x3F) << 6) |
                (static_cast<uint8_t>(input[i + 2]) & 0x3F); count = 3;
        } else if ((first & 0xF8) == 0xF0 && i + 3 < input.size()) {
            cp = ((first & 0x07) << 18) | ((static_cast<uint8_t>(input[i + 1]) & 0x3F) << 12) |
                ((static_cast<uint8_t>(input[i + 2]) & 0x3F) << 6) |
                (static_cast<uint8_t>(input[i + 3]) & 0x3F); count = 4;
        }
        i += count;
        if (cp > 0x10FFFF || (cp >= 0xD800 && cp <= 0xDFFF)) cp = 0xFFFD;
        if (cp <= 0xFFFF) output.push_back(static_cast<char16_t>(cp));
        else {
            cp -= 0x10000;
            output.push_back(static_cast<char16_t>(0xD800 + (cp >> 10)));
            output.push_back(static_cast<char16_t>(0xDC00 + (cp & 0x3FF)));
        }
    }
    return output;
}

jstring toJavaString(JNIEnv* env, const std::string& value) {
    const std::u16string utf16 = utf8ToUtf16(value);
    return env->NewString(reinterpret_cast<const jchar*>(utf16.data()), static_cast<jsize>(utf16.size()));
}

} // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_forgeai_chat_nativebridge_NativeBridge_nativeRuntimeInfo(JNIEnv* env, jobject) {
#if defined(__aarch64__)
    constexpr char kAbi[] = "arm64-v8a";
#elif defined(__x86_64__)
    constexpr char kAbi[] = "x86_64";
#else
    constexpr char kAbi[] = "unknown";
#endif
    return toJavaString(env, std::string("ForgeAI JNI + llama.cpp; ABI=") + kAbi);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_forgeai_chat_nativebridge_NativeBridge_nativeUtf8RoundTrip(JNIEnv* env, jobject, jstring input) {
    if (input == nullptr) return nullptr;
    return toJavaString(env, utf16ToUtf8(env, input));
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_forgeai_chat_nativebridge_NativeBridge_nativeTokenArrayRoundTrip(JNIEnv* env, jobject, jintArray input) {
    if (input == nullptr) return nullptr;
    const jsize length = env->GetArrayLength(input);
    std::vector<jint> values(static_cast<size_t>(length));
    if (length > 0) {
        env->GetIntArrayRegion(input, 0, length, values.data());
        if (env->ExceptionCheck()) return nullptr;
    }
    jintArray output = env->NewIntArray(length);
    if (output == nullptr) return nullptr;
    if (length > 0) {
        env->SetIntArrayRegion(output, 0, length, values.data());
        if (env->ExceptionCheck()) return nullptr;
    }
    return output;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_forgeai_chat_nativebridge_NativeBridge_nativeLoadModel(
    JNIEnv* env, jobject, jstring model_path, jint context_size, jint threads) {
    if (model_path == nullptr) return toJavaString(env, "ERROR|Model path is null");
    if (context_size < 512 || context_size > 32768) return toJavaString(env, "ERROR|Context size must be 512..32768");
    if (threads < 1 || threads > 32) return toJavaString(env, "ERROR|Thread count must be 1..32");
    const std::string path = utf16ToUtf8(env, model_path);
    std::lock_guard<std::mutex> lock(g_model_mutex);
    if (g_model != nullptr || g_context != nullptr) return toJavaString(env, "ERROR|A model is already loaded");
    std::call_once(g_backend_once, [] { llama_backend_init(); });
    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;
    model_params.load_mode = LLAMA_LOAD_MODE_MMAP;
    llama_model* model = llama_model_load_from_file(path.c_str(), model_params);
    if (model == nullptr) return toJavaString(env, "ERROR|llama_model_load_from_file failed");
    llama_context_params context_params = llama_context_default_params();
    context_params.n_ctx = static_cast<uint32_t>(context_size);
    context_params.n_batch = 512;
    context_params.n_ubatch = 128;
    context_params.n_threads = threads;
    context_params.n_threads_batch = threads;
    context_params.offload_kqv = false;
    context_params.op_offload = false;
    context_params.abort_callback = decodeAbortCallback;
    context_params.abort_callback_data = nullptr;
    llama_context* context = llama_init_from_model(model, context_params);
    if (context == nullptr) {
        llama_model_free(model);
        return toJavaString(env, "ERROR|llama_init_from_model failed");
    }
    g_model = model;
    g_context = context;
    const char* quant = llama_ftype_name(llama_model_ftype(model));
    const uint64_t model_bytes = llama_model_size(model);
    return toJavaString(env, std::string("OK|quant=") + (quant ? quant : "unknown") +
        "|tensor_bytes=" + std::to_string(model_bytes) +
        "|context=" + std::to_string(llama_n_ctx(context)) +
        "|threads=" + std::to_string(threads) + "|load=mmap|backend=cpu");
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_forgeai_chat_nativebridge_NativeBridge_nativeTokenize(JNIEnv* env, jobject, jstring text, jboolean add_bos) {
    if (text == nullptr) return nullptr;
    std::lock_guard<std::mutex> lock(g_model_mutex);
    if (g_model == nullptr) return nullptr;
    const std::string input = utf16ToUtf8(env, text);
    const llama_vocab* vocab = llama_model_get_vocab(g_model);
    int32_t capacity = static_cast<int32_t>(input.size()) + 16;
    std::vector<llama_token> tokens(static_cast<size_t>(capacity));
    int32_t count = llama_tokenize(vocab, input.data(), static_cast<int32_t>(input.size()),
        tokens.data(), capacity, add_bos == JNI_TRUE, true);
    if (count < 0) {
        capacity = -count;
        tokens.resize(static_cast<size_t>(capacity));
        count = llama_tokenize(vocab, input.data(), static_cast<int32_t>(input.size()),
            tokens.data(), capacity, add_bos == JNI_TRUE, true);
    }
    if (count < 0) return nullptr;
    jintArray result = env->NewIntArray(count);
    if (result == nullptr) return nullptr;
    if (count > 0) {
        std::vector<jint> values(tokens.begin(), tokens.begin() + count);
        env->SetIntArrayRegion(result, 0, count, values.data());
    }
    return result;
}


extern "C" JNIEXPORT jstring JNICALL
Java_com_forgeai_chat_nativebridge_NativeBridge_nativeGenerate(
    JNIEnv* env, jobject, jstring prompt_text, jfloat temperature, jfloat top_p,
    jfloat frequency_penalty, jfloat presence_penalty, jint max_tokens,
    jint timeout_ms, jint seed) {
    const auto errorJson = [&](const char* message, const char* status = "ERROR") {
        return toJavaString(env, std::string("{\"status\":\"") + status +
            "\",\"message\":\"" + jsonEscape(message) + "\"}");
    };
    if (prompt_text == nullptr) return errorJson("Prompt is null");
    if (!std::isfinite(temperature) || temperature < 0.0f || temperature > 2.0f ||
        !std::isfinite(top_p) || top_p <= 0.0f || top_p > 1.0f ||
        !std::isfinite(frequency_penalty) || frequency_penalty < -2.0f || frequency_penalty > 2.0f ||
        !std::isfinite(presence_penalty) || presence_penalty < -2.0f || presence_penalty > 2.0f ||
        max_tokens < 1 || max_tokens > 128 || timeout_ms < 100 || timeout_ms > 600000) {
        return errorJson("Invalid generation parameters");
    }

    const std::string prompt = utf16ToUtf8(env, prompt_text);
    std::lock_guard<std::mutex> lock(g_model_mutex);
    if (g_model == nullptr || g_context == nullptr) return errorJson("No GGUF model is loaded");

    const auto started = std::chrono::steady_clock::now();
    const int64_t deadline = monotonicNanos() + static_cast<int64_t>(timeout_ms) * 1000000LL;
    g_decode_deadline_ns.store(deadline, std::memory_order_relaxed);
    struct DeadlineReset { ~DeadlineReset() { g_decode_deadline_ns.store(0, std::memory_order_relaxed); } } reset;

    const llama_vocab* vocab = llama_model_get_vocab(g_model);
    int32_t capacity = static_cast<int32_t>(prompt.size()) + 16;
    std::vector<llama_token> prompt_tokens(static_cast<size_t>(capacity));
    int32_t prompt_count = llama_tokenize(vocab, prompt.data(), static_cast<int32_t>(prompt.size()),
        prompt_tokens.data(), capacity, false, true);
    if (prompt_count < 0) {
        capacity = -prompt_count;
        prompt_tokens.resize(static_cast<size_t>(capacity));
        prompt_count = llama_tokenize(vocab, prompt.data(), static_cast<int32_t>(prompt.size()),
            prompt_tokens.data(), capacity, false, true);
    }
    if (prompt_count <= 0) return errorJson("Prompt tokenization failed");
    prompt_tokens.resize(static_cast<size_t>(prompt_count));
    if (static_cast<uint32_t>(prompt_count + max_tokens) > llama_n_ctx(g_context))
        return errorJson("Prompt plus output exceeds context window");

    llama_batch_ext* batch = llama_batch_ext_init(g_context);
    if (batch == nullptr) return errorJson("Could not allocate prompt batch");
    bool batch_ok = true;
    for (int32_t i = 0; i < prompt_count; ++i) {
        const int32_t idx = llama_batch_ext_add_token(batch, 0, prompt_tokens[static_cast<size_t>(i)]);
        const llama_pos pos = i;
        if (idx < 0 || !llama_batch_ext_set_pos(batch, idx, &pos) ||
            !llama_batch_ext_set_output_logits(batch, idx, i == prompt_count - 1)) {
            batch_ok = false;
            break;
        }
    }
    const int32_t prompt_result = batch_ok ? llama_process(g_context, LLAMA_PROCESS_TYPE_DECODE, batch) : -1;
    llama_batch_ext_free(batch);
    if (!batch_ok || prompt_result != 0) {
        const bool timeout = monotonicNanos() >= deadline;
        return errorJson(timeout ? "Timed out during prompt evaluation" : "Prompt evaluation failed",
                         timeout ? "TIMEOUT" : "ERROR");
    }

    llama_sampler_chain_params params = llama_sampler_chain_default_params();
    llama_sampler* sampler = llama_sampler_chain_init(params);
    if (sampler == nullptr) return errorJson("Sampler initialization failed");
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(top_p, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_penalties(
        llama_vocab_n_tokens(vocab), 64, 1.0f, frequency_penalty, presence_penalty));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(seed < 0 ? LLAMA_DEFAULT_SEED : static_cast<uint32_t>(seed)));

    std::string output;
    std::vector<llama_token> output_ids;
    bool timeout = false;
    bool eog = false;
    int32_t decode_result = 0;
    for (int32_t i = 0; i < max_tokens; ++i) {
        if (monotonicNanos() >= deadline) { timeout = true; break; }
        const llama_token token = llama_sampler_sample(sampler, g_context, -1);
        if (llama_vocab_is_eog(vocab, token)) { eog = true; break; }
        output_ids.push_back(token);
        output += tokenPiece(vocab, token);

        llama_batch_ext* next = llama_batch_ext_init(g_context);
        if (next == nullptr) { decode_result = -1; break; }
        const int32_t idx = llama_batch_ext_add_token(next, 0, token);
        const llama_pos pos = prompt_count + i;
        const bool valid = idx >= 0 && llama_batch_ext_set_pos(next, idx, &pos) &&
            llama_batch_ext_set_output_logits(next, idx, true);
        decode_result = valid ? llama_process(g_context, LLAMA_PROCESS_TYPE_DECODE, next) : -1;
        llama_batch_ext_free(next);
        if (!valid || decode_result != 0) {
            timeout = monotonicNanos() >= deadline;
            break;
        }
    }
    llama_sampler_free(sampler);
    const auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now() - started).count();
    const double rate = elapsed > 0 ? output_ids.size() * 1000.0 / elapsed : 0.0;

    std::ostringstream json;
    json << "{\"status\":\"" << (timeout ? "TIMEOUT" : (decode_result != 0 ? "ERROR" : "OK"))
         << "\",\"message\":\"" << (timeout ? "Generation deadline exceeded" :
             (decode_result != 0 ? "Token decode failed" : ""))
         << "\",\"text\":\"" << jsonEscape(output) << "\",\"prompt_tokens\":" << prompt_count
         << ",\"output_tokens\":" << output_ids.size() << ",\"elapsed_ms\":" << elapsed
         << ",\"tokens_per_sec\":" << std::fixed << std::setprecision(3) << rate
         << ",\"eog\":" << (eog ? "true" : "false") << ",\"token_ids\":[";
    for (size_t i = 0; i < output_ids.size(); ++i) { if (i) json << ","; json << output_ids[i]; }
    json << "]}";
    return toJavaString(env, json.str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_forgeai_chat_nativebridge_NativeBridge_nativeUnloadModel(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(g_model_mutex);
    if (g_context != nullptr) { llama_free(g_context); g_context = nullptr; }
    if (g_model != nullptr) { llama_model_free(g_model); g_model = nullptr; }
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_forgeai_chat_nativebridge_NativeBridge_nativeIsModelLoaded(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(g_model_mutex);
    return g_model != nullptr && g_context != nullptr ? JNI_TRUE : JNI_FALSE;
}
