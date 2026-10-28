#include <jni.h>
#include <android/log.h>

#include <algorithm>
#include <cstring>
#include <memory>
#include <mutex>
#include <string>
#include <vector>

#include "llama.h"

#define LOG_TAG "QwenNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

std::string toString(JNIEnv * env, jstring value) {
    if (value == nullptr) return {};
    const char * chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) return {};
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

struct ModelDeleter {
    void operator()(llama_model * model) const { llama_model_free(model); }
};

struct ContextDeleter {
    void operator()(llama_context * ctx) const { llama_free(ctx); }
};

struct SamplerDeleter {
    void operator()(llama_sampler * sampler) const { llama_sampler_free(sampler); }
};

void initBackend() {
    static std::once_flag once;
    std::call_once(once, [] {
        llama_backend_init();
    });
}

std::vector<llama_token> tokenize(
        const llama_vocab * vocab,
        const std::string & text,
        bool addSpecial,
        bool parseSpecial) {
    int32_t needed = llama_tokenize(
            vocab, text.c_str(), static_cast<int32_t>(text.size()),
            nullptr, 0, addSpecial, parseSpecial);
    if (needed == 0) return {};
    if (needed < 0) needed = -needed;
    if (needed > 4096) return {};

    std::vector<llama_token> tokens(static_cast<size_t>(needed));
    int32_t written = llama_tokenize(
            vocab, text.c_str(), static_cast<int32_t>(text.size()),
            tokens.data(), static_cast<int32_t>(tokens.size()), addSpecial, parseSpecial);
    if (written < 0) return {};
    tokens.resize(static_cast<size_t>(written));
    return tokens;
}

std::string tokenPiece(const llama_vocab * vocab, llama_token token) {
    char buffer[256];
    int32_t size = llama_token_to_piece(vocab, token, buffer, sizeof(buffer), 0, false);
    if (size >= 0) return std::string(buffer, static_cast<size_t>(size));
    std::vector<char> large(static_cast<size_t>(-size));
    int32_t written = llama_token_to_piece(
            vocab, token, large.data(), static_cast<int32_t>(large.size()), 0, false);
    if (written <= 0) return {};
    return std::string(large.data(), static_cast<size_t>(written));
}

}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_voiceassistant_hindi_QwenNative_generate(
        JNIEnv * env,
        jclass,
        jstring modelPathValue,
        jstring promptValue,
        jstring grammarValue,
        jint maxTokensValue,
        jint nCtxValue,
        jint nThreadsValue) {
    const std::string modelPath = toString(env, modelPathValue);
    const std::string prompt = toString(env, promptValue);
    const std::string grammar = toString(env, grammarValue);
    const int maxTokens = std::clamp(static_cast<int>(maxTokensValue), 16, 256);
    const int requestedCtx = std::clamp(static_cast<int>(nCtxValue), 256, 2048);
    const int nThreads = std::clamp(static_cast<int>(nThreadsValue), 1, 8);

    if (modelPath.empty() || prompt.empty() || grammar.empty()) {
        LOGE("Missing model, prompt, or grammar");
        return env->NewStringUTF("");
    }

    initBackend();

    llama_model_params modelParams = llama_model_default_params();
    modelParams.n_gpu_layers = 0;
    modelParams.load_mode = LLAMA_LOAD_MODE_MMAP;

    std::unique_ptr<llama_model, ModelDeleter> model(
            llama_model_load_from_file(modelPath.c_str(), modelParams));
    if (!model) {
        LOGE("Failed to load model: %s", modelPath.c_str());
        return env->NewStringUTF("");
    }

    const llama_vocab * vocab = llama_model_get_vocab(model.get());
    if (vocab == nullptr) {
        LOGE("Model has no vocabulary");
        return env->NewStringUTF("");
    }

    std::vector<llama_token> promptTokens = tokenize(vocab, prompt, false, true);
    if (promptTokens.empty()) {
        LOGE("Tokenization failed");
        return env->NewStringUTF("");
    }

    const int promptCount = static_cast<int>(promptTokens.size());
    const int neededCtx = promptCount + maxTokens + 8;
    const int neededBatch = std::min(2048, std::max(128, promptCount + 8));
    llama_context_params contextParams = llama_context_default_params();
    contextParams.n_ctx = static_cast<uint32_t>(std::max(requestedCtx, neededCtx));
    contextParams.n_batch = static_cast<uint32_t>(neededBatch);
    contextParams.n_ubatch = static_cast<uint32_t>(std::min(64, neededBatch));
    contextParams.n_threads = nThreads;
    contextParams.n_threads_batch = nThreads;
    contextParams.no_perf = true;

    std::unique_ptr<llama_context, ContextDeleter> context(
            llama_init_from_model(model.get(), contextParams));
    if (!context) {
        LOGE("Failed to create context");
        return env->NewStringUTF("");
    }

    llama_batch promptBatch = llama_batch_get_one(
            promptTokens.data(), static_cast<int32_t>(promptTokens.size()));
    if (llama_decode(context.get(), promptBatch) != 0) {
        LOGE("Prompt decode failed");
        return env->NewStringUTF("");
    }

    llama_sampler_chain_params samplerParams = llama_sampler_chain_default_params();
    samplerParams.no_perf = true;
    std::unique_ptr<llama_sampler, SamplerDeleter> sampler(
            llama_sampler_chain_init(samplerParams));
    if (!sampler) {
        LOGE("Failed to create sampler chain");
        return env->NewStringUTF("");
    }

    llama_sampler * grammarSampler = llama_sampler_init_grammar(vocab, grammar.c_str(), "root");
    if (grammarSampler == nullptr) {
        LOGE("Invalid grammar");
        return env->NewStringUTF("");
    }
    llama_sampler_chain_add(sampler.get(), grammarSampler);
    llama_sampler_chain_add(sampler.get(), llama_sampler_init_greedy());

    std::string output;
    output.reserve(512);

    for (int generated = 0; generated < maxTokens; ++generated) {
        const llama_token token = llama_sampler_sample(sampler.get(), context.get(), -1);
        if (llama_vocab_is_eog(vocab, token)) break;

        output += tokenPiece(vocab, token);

        if (generated + 1 >= maxTokens) break;
        llama_token next = token;
        llama_batch batch = llama_batch_get_one(&next, 1);
        if (llama_decode(context.get(), batch) != 0) {
            LOGE("Generation decode failed");
            break;
        }
    }

    LOGI("Generated %zu bytes", output.size());
    return env->NewStringUTF(output.c_str());
}
