#include "chat_session.h"

#include <algorithm>
#include <chrono>
#include <cstdint>
#include <cstring>
#include <thread>

#include "common.h"

namespace pocketmind {

namespace {

// Tokens reserved for the answer when checking whether a prompt fits.
constexpr int kAnswerReserve = 512;

bool abort_callback(void *data) {
    return static_cast<std::atomic<bool> *>(data)->load();
}

// Length of the longest prefix of `s` that is complete UTF-8.
size_t valid_utf8_prefix(const std::string &s) {
    size_t i = 0;
    const size_t n = s.size();
    while (i < n) {
        const auto c = static_cast<unsigned char>(s[i]);
        size_t len;
        if (c < 0x80) len = 1;
        else if ((c & 0xE0) == 0xC0) len = 2;
        else if ((c & 0xF0) == 0xE0) len = 3;
        else if ((c & 0xF8) == 0xF0) len = 4;
        else { i += 1; continue; }  // stray byte: let it through
        if (i + len > n) break;
        i += len;
    }
    return i;
}

}  // namespace

ChatSession *ChatSession::load(const std::string &path, const SessionParams &params) {
    llama_model_params mparams = llama_model_default_params();
    llama_model *model = llama_model_load_from_file(path.c_str(), mparams);
    if (!model) return nullptr;

    llama_context_params cparams = llama_context_default_params();
    const int n_ctx_train = llama_model_n_ctx_train(model);
    cparams.n_ctx = n_ctx_train > 0 ? std::min(params.n_ctx, n_ctx_train) : params.n_ctx;
    cparams.n_batch = params.n_batch;
    cparams.n_ubatch = params.n_batch;
    cparams.n_threads = params.n_threads;
    cparams.n_threads_batch = params.n_threads;
    cparams.no_perf = true;

    llama_context *ctx = llama_init_from_model(model, cparams);
    if (!ctx) {
        llama_model_free(model);
        return nullptr;
    }

    auto *session = new ChatSession();
    session->model_ = model;
    session->ctx_ = ctx;
    session->params_ = params;
    session->n_ctx_ = (int) llama_n_ctx(ctx);
    session->templates_ = common_chat_templates_init(model, "");
    llama_set_abort_callback(ctx, abort_callback, &session->cancelled_);
    session->reset_sampler();
    return session;
}

ChatSession::~ChatSession() {
    if (sampler_) llama_sampler_free(sampler_);
    templates_.reset();
    if (ctx_) llama_free(ctx_);
    if (model_) llama_model_free(model_);
}

void ChatSession::reset_sampler() {
    if (sampler_) llama_sampler_free(sampler_);
    auto sparams = llama_sampler_chain_default_params();
    sampler_ = llama_sampler_chain_init(sparams);
    const int n_vocab = llama_vocab_n_tokens(llama_model_get_vocab(model_));
    llama_sampler_chain_add(sampler_, llama_sampler_init_penalties(n_vocab, 64, params_.repeat_penalty, 0.0f, 0.0f));
    llama_sampler_chain_add(sampler_, llama_sampler_init_top_k(params_.top_k));
    llama_sampler_chain_add(sampler_, llama_sampler_init_top_p(params_.top_p, 1));
    llama_sampler_chain_add(sampler_, llama_sampler_init_min_p(params_.min_p, 1));
    llama_sampler_chain_add(sampler_, llama_sampler_init_temp(params_.temperature));
    llama_sampler_chain_add(sampler_, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
}

std::string ChatSession::description() const {
    char buf[256];
    llama_model_desc(model_, buf, sizeof(buf));
    return buf;
}

bool ChatSession::decode(const llama_token *tokens, int count, int start_pos) {
    llama_batch batch = llama_batch_init(params_.n_batch, 0, 1);
    bool ok = true;
    for (int i = 0; i < count && ok; i += params_.n_batch) {
        const int n = std::min(params_.n_batch, count - i);
        batch.n_tokens = n;
        for (int j = 0; j < n; ++j) {
            batch.token[j] = tokens[i + j];
            batch.pos[j] = start_pos + i + j;
            batch.n_seq_id[j] = 1;
            batch.seq_id[j][0] = 0;
            batch.logits[j] = (i + j == count - 1);
        }
        ok = llama_decode(ctx_, batch) == 0;
    }
    llama_batch_free(batch);
    return ok;
}

int ChatSession::begin_turn(const std::vector<ChatMessage> &messages, bool enable_thinking) {
    cancelled_.store(false);
    context_full_ = false;
    pending_utf8_.clear();

    common_chat_templates_inputs inputs;
    for (const auto &m : messages) {
        common_chat_msg msg;
        msg.role = m.role;
        msg.content = m.content;
        inputs.messages.push_back(std::move(msg));
    }
    inputs.add_generation_prompt = true;
    inputs.enable_thinking = enable_thinking;

    std::string prompt;
    try {
        inputs.use_jinja = true;
        prompt = common_chat_templates_apply(templates_.get(), inputs).prompt.str();
    } catch (...) {
        try {
            inputs.use_jinja = false;
            prompt = common_chat_templates_apply(templates_.get(), inputs).prompt.str();
        } catch (...) {
            return TURN_TEMPLATE_FAILED;
        }
    }

    const llama_vocab *vocab = llama_model_get_vocab(model_);
    std::vector<llama_token> tokens = common_tokenize(vocab, prompt, /*add_special*/ true, /*parse_special*/ true);
    if ((int) tokens.size() + kAnswerReserve > n_ctx_) return TURN_CONTEXT_OVERFLOW;

    // Reuse the KV cache for the common prefix; always re-decode at least one
    // token so that fresh logits are available for sampling.
    size_t prefix = 0;
    while (prefix < cached_.size() && prefix < tokens.size() && cached_[prefix] == tokens[prefix]) ++prefix;
    if (prefix == tokens.size()) --prefix;

    llama_memory_t mem = llama_get_memory(ctx_);
    if (!llama_memory_seq_rm(mem, 0, (llama_pos) prefix, -1)) {
        // Recurrent / hybrid models cannot drop a partial sequence.
        llama_memory_clear(mem, true);
        prefix = 0;
    }
    cached_.resize(prefix);

    if (!decode(tokens.data() + prefix, (int) (tokens.size() - prefix), (int) prefix)) {
        llama_memory_clear(mem, true);
        cached_.clear();
        return cancelled_.load() ? TURN_CANCELLED : TURN_DECODE_FAILED;
    }
    cached_ = std::move(tokens);
    reset_sampler();
    return (int) cached_.size();
}

bool ChatSession::next_token(std::string &piece) {
    piece.clear();
    if (cancelled_.load()) return false;
    if ((int) cached_.size() >= n_ctx_ - 1) {
        context_full_ = true;
        return false;
    }

    const llama_token token = llama_sampler_sample(sampler_, ctx_, -1);
    const llama_vocab *vocab = llama_model_get_vocab(model_);
    if (llama_vocab_is_eog(vocab, token)) {
        // Keep the end-of-turn token in the cache: the template renders it too,
        // so the next turn can reuse the whole history.
        if (decode(&token, 1, (int) cached_.size())) cached_.push_back(token);
        return false;
    }

    if (!decode(&token, 1, (int) cached_.size())) return false;
    cached_.push_back(token);

    pending_utf8_ += common_token_to_piece(vocab, token, /*special*/ false);
    const size_t valid = valid_utf8_prefix(pending_utf8_);
    piece = pending_utf8_.substr(0, valid);
    pending_utf8_.erase(0, valid);
    return true;
}

double benchmark_memory_bandwidth(int megabytes, int threads) {
    threads = std::max(1, threads);
    const size_t words = (size_t) megabytes * 1024 * 1024 / sizeof(uint64_t);
    std::vector<uint64_t> buffer(words);
    for (size_t i = 0; i < words; ++i) buffer[i] = i;

    std::vector<uint64_t> sums(threads, 0);
    const int passes = 4;
    const auto start = std::chrono::steady_clock::now();
    std::vector<std::thread> workers;
    for (int t = 0; t < threads; ++t) {
        workers.emplace_back([&, t] {
            const size_t begin = words * t / threads;
            const size_t end = words * (t + 1) / threads;
            uint64_t a = 0, b = 0, c = 0, d = 0;
            for (int p = 0; p < passes; ++p) {
                size_t i = begin;
                for (; i + 4 <= end; i += 4) {
                    a += buffer[i];
                    b += buffer[i + 1];
                    c += buffer[i + 2];
                    d += buffer[i + 3];
                }
                for (; i < end; ++i) a += buffer[i];
            }
            sums[t] = a + b + c + d;
        });
    }
    for (auto &w : workers) w.join();
    const double seconds = std::chrono::duration<double>(std::chrono::steady_clock::now() - start).count();

    volatile uint64_t sink = 0;
    for (auto s : sums) sink = sink + s;
    (void) sink;

    const double bytes = (double) words * sizeof(uint64_t) * passes;
    return seconds > 0 ? bytes / seconds / 1e9 : 0.0;
}

}  // namespace pocketmind
