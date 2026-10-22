#include <android/log.h>
#include <jni.h>

#include <string>
#include <vector>

#include "chat_session.h"
#include "ggml-backend.h"
#include "llama.h"

using pocketmind::ChatMessage;
using pocketmind::ChatSession;
using pocketmind::SessionParams;

namespace {

constexpr const char *kTag = "PocketMind";

void log_callback(ggml_log_level level, const char *text, void * /*user_data*/) {
    int priority;
    switch (level) {
        case GGML_LOG_LEVEL_ERROR: priority = ANDROID_LOG_ERROR; break;
        case GGML_LOG_LEVEL_WARN: priority = ANDROID_LOG_WARN; break;
        case GGML_LOG_LEVEL_INFO: priority = ANDROID_LOG_INFO; break;
        default: priority = ANDROID_LOG_DEBUG; break;
    }
    __android_log_write(priority, kTag, text);
}

std::string to_string(JNIEnv *env, jstring value) {
    const char *chars = env->GetStringUTFChars(value, nullptr);
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

ChatSession *session(jlong handle) {
    return reinterpret_cast<ChatSession *>(handle);
}

}  // namespace

extern "C" {

JNIEXPORT void JNICALL
Java_io_pocketmind_engine_LlamaNative_init(JNIEnv *env, jobject, jstring native_lib_dir) {
    llama_log_set(log_callback, nullptr);
    ggml_backend_load_all_from_path(to_string(env, native_lib_dir).c_str());
    llama_backend_init();
}

JNIEXPORT jstring JNICALL
Java_io_pocketmind_engine_LlamaNative_systemInfo(JNIEnv *env, jobject) {
    return env->NewStringUTF(llama_print_system_info());
}

JNIEXPORT jlong JNICALL
Java_io_pocketmind_engine_LlamaNative_load(JNIEnv *env, jobject, jstring path, jint n_ctx, jint n_threads) {
    SessionParams params;
    params.n_ctx = n_ctx;
    params.n_threads = n_threads;
    return reinterpret_cast<jlong>(ChatSession::load(to_string(env, path), params));
}

JNIEXPORT void JNICALL
Java_io_pocketmind_engine_LlamaNative_free(JNIEnv *, jobject, jlong handle) {
    delete session(handle);
}

JNIEXPORT jint JNICALL
Java_io_pocketmind_engine_LlamaNative_beginTurn(JNIEnv *env, jobject, jlong handle,
                                                jobjectArray roles, jobjectArray contents,
                                                jboolean enable_thinking) {
    const jsize count = env->GetArrayLength(roles);
    std::vector<ChatMessage> messages;
    messages.reserve(count);
    for (jsize i = 0; i < count; ++i) {
        auto role = (jstring) env->GetObjectArrayElement(roles, i);
        auto content = (jstring) env->GetObjectArrayElement(contents, i);
        messages.push_back({to_string(env, role), to_string(env, content)});
        env->DeleteLocalRef(role);
        env->DeleteLocalRef(content);
    }
    return session(handle)->begin_turn(messages, enable_thinking);
}

// Returns raw UTF-8 bytes (JNI's modified UTF-8 cannot carry emoji), or null
// when generation has finished.
JNIEXPORT jbyteArray JNICALL
Java_io_pocketmind_engine_LlamaNative_nextToken(JNIEnv *env, jobject, jlong handle) {
    std::string piece;
    if (!session(handle)->next_token(piece)) return nullptr;
    jbyteArray bytes = env->NewByteArray((jsize) piece.size());
    env->SetByteArrayRegion(bytes, 0, (jsize) piece.size(), reinterpret_cast<const jbyte *>(piece.data()));
    return bytes;
}

JNIEXPORT void JNICALL
Java_io_pocketmind_engine_LlamaNative_cancel(JNIEnv *, jobject, jlong handle) {
    session(handle)->cancel();
}

JNIEXPORT jboolean JNICALL
Java_io_pocketmind_engine_LlamaNative_contextFull(JNIEnv *, jobject, jlong handle) {
    return session(handle)->context_full();
}

JNIEXPORT jint JNICALL
Java_io_pocketmind_engine_LlamaNative_contextSize(JNIEnv *, jobject, jlong handle) {
    return session(handle)->n_ctx();
}

JNIEXPORT jstring JNICALL
Java_io_pocketmind_engine_LlamaNative_describe(JNIEnv *env, jobject, jlong handle) {
    return env->NewStringUTF(session(handle)->description().c_str());
}

JNIEXPORT jdouble JNICALL
Java_io_pocketmind_engine_LlamaNative_benchmarkMemory(JNIEnv *, jobject, jint megabytes, jint threads) {
    return pocketmind::benchmark_memory_bandwidth(megabytes, threads);
}

}  // extern "C"
