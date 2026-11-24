#include <jni.h>
#include <cstdint>
#include <mutex>
#include <string>
#include <vector>

#include "llama.h"

namespace {

std::mutex g_model_mutex;
llama_model* g_model = nullptr;
llama_context* g_context = nullptr;
std::once_flag g_backend_once;

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
    model_params.n_gpu_layers = 0; // Explicit CPU-only baseline.
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
        "|threads=" + std::to_string(threads) +
        "|load=mmap|backend=cpu");
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_forgeai_chat_nativebridge_NativeBridge_nativeUnloadModel(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(g_model_mutex);
    if (g_context != nullptr) {
        llama_free(g_context);
        g_context = nullptr;
    }
    if (g_model != nullptr) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_forgeai_chat_nativebridge_NativeBridge_nativeIsModelLoaded(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(g_model_mutex);
    return g_model != nullptr && g_context != nullptr ? JNI_TRUE : JNI_FALSE;
}
