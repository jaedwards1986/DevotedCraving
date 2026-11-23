#include <jni.h>
#include <cstdint>
#include <string>
#include <vector>

namespace {

std::string utf16ToUtf8(JNIEnv* env, jstring value) {
    if (value == nullptr) return {};

    const jsize length = env->GetStringLength(value);
    const jchar* chars = env->GetStringChars(value, nullptr);
    if (chars == nullptr) return {};

    std::string output;
    output.reserve(static_cast<size_t>(length) * 3);

    for (jsize i = 0; i < length; ++i) {
        uint32_t codepoint = chars[i];
        if (codepoint >= 0xD800 && codepoint <= 0xDBFF && i + 1 < length) {
            const uint32_t low = chars[i + 1];
            if (low >= 0xDC00 && low <= 0xDFFF) {
                codepoint = 0x10000 + ((codepoint - 0xD800) << 10) + (low - 0xDC00);
                ++i;
            } else {
                codepoint = 0xFFFD;
            }
        } else if (codepoint >= 0xD800 && codepoint <= 0xDFFF) {
            codepoint = 0xFFFD;
        }

        if (codepoint <= 0x7F) {
            output.push_back(static_cast<char>(codepoint));
        } else if (codepoint <= 0x7FF) {
            output.push_back(static_cast<char>(0xC0 | (codepoint >> 6)));
            output.push_back(static_cast<char>(0x80 | (codepoint & 0x3F)));
        } else if (codepoint <= 0xFFFF) {
            output.push_back(static_cast<char>(0xE0 | (codepoint >> 12)));
            output.push_back(static_cast<char>(0x80 | ((codepoint >> 6) & 0x3F)));
            output.push_back(static_cast<char>(0x80 | (codepoint & 0x3F)));
        } else {
            output.push_back(static_cast<char>(0xF0 | (codepoint >> 18)));
            output.push_back(static_cast<char>(0x80 | ((codepoint >> 12) & 0x3F)));
            output.push_back(static_cast<char>(0x80 | ((codepoint >> 6) & 0x3F)));
            output.push_back(static_cast<char>(0x80 | (codepoint & 0x3F)));
        }
    }

    env->ReleaseStringChars(value, chars);
    return output;
}

std::u16string utf8ToUtf16(const std::string& input) {
    std::u16string output;
    for (size_t i = 0; i < input.size();) {
        const uint8_t first = static_cast<uint8_t>(input[i]);
        uint32_t codepoint = 0xFFFD;
        size_t count = 1;

        if (first <= 0x7F) {
            codepoint = first;
        } else if ((first & 0xE0) == 0xC0 && i + 1 < input.size()) {
            codepoint = ((first & 0x1F) << 6) |
                (static_cast<uint8_t>(input[i + 1]) & 0x3F);
            count = 2;
        } else if ((first & 0xF0) == 0xE0 && i + 2 < input.size()) {
            codepoint = ((first & 0x0F) << 12) |
                ((static_cast<uint8_t>(input[i + 1]) & 0x3F) << 6) |
                (static_cast<uint8_t>(input[i + 2]) & 0x3F);
            count = 3;
        } else if ((first & 0xF8) == 0xF0 && i + 3 < input.size()) {
            codepoint = ((first & 0x07) << 18) |
                ((static_cast<uint8_t>(input[i + 1]) & 0x3F) << 12) |
                ((static_cast<uint8_t>(input[i + 2]) & 0x3F) << 6) |
                (static_cast<uint8_t>(input[i + 3]) & 0x3F);
            count = 4;
        }

        i += count;
        if (codepoint > 0x10FFFF || (codepoint >= 0xD800 && codepoint <= 0xDFFF)) {
            codepoint = 0xFFFD;
        }

        if (codepoint <= 0xFFFF) {
            output.push_back(static_cast<char16_t>(codepoint));
        } else {
            codepoint -= 0x10000;
            output.push_back(static_cast<char16_t>(0xD800 + (codepoint >> 10)));
            output.push_back(static_cast<char16_t>(0xDC00 + (codepoint & 0x3FF)));
        }
    }
    return output;
}

jstring toJavaString(JNIEnv* env, const std::u16string& value) {
    return env->NewString(
        reinterpret_cast<const jchar*>(value.data()),
        static_cast<jsize>(value.size())
    );
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
    const std::string info = std::string("ForgeAI JNI bridge OK; ABI=") + kAbi;
    return toJavaString(env, utf8ToUtf16(info));
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_forgeai_chat_nativebridge_NativeBridge_nativeUtf8RoundTrip(
    JNIEnv* env,
    jobject,
    jstring input
) {
    if (input == nullptr) return nullptr;
    const std::string utf8 = utf16ToUtf8(env, input);
    return toJavaString(env, utf8ToUtf16(utf8));
}

extern "C" JNIEXPORT jintArray JNICALL
Java_com_forgeai_chat_nativebridge_NativeBridge_nativeTokenArrayRoundTrip(
    JNIEnv* env,
    jobject,
    jintArray input
) {
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
