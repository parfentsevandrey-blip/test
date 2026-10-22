#pragma once

#include <atomic>
#include <string>
#include <vector>

#include "chat.h"
#include "llama.h"

namespace pocketmind {

struct ChatMessage {
    std::string role;
    std::string content;
};

struct SessionParams {
    int n_ctx = 4096;
    int n_threads = 4;
    int n_batch = 256;
    float temperature = 0.7f;
    float top_p = 0.95f;
    float min_p = 0.05f;
    int top_k = 40;
    float repeat_penalty = 1.1f;
};

// Result codes of ChatSession::begin_turn().
enum TurnResult : int {
    TURN_OK = 0,
    TURN_CONTEXT_OVERFLOW = -2,
    TURN_DECODE_FAILED = -3,
    TURN_CANCELLED = -4,
    TURN_TEMPLATE_FAILED = -5,
};

// One loaded model + context. The whole conversation is re-rendered through the
// model's own chat template on every turn; only the part that differs from the
// tokens already in the KV cache is decoded, so long chats stay cheap.
class ChatSession {
public:
    static ChatSession *load(const std::string &path, const SessionParams &params);
    ~ChatSession();

    // Renders the conversation, decodes the new suffix and prepares for sampling.
    // Returns the prompt token count (>= 0) or a TurnResult error code.
    int begin_turn(const std::vector<ChatMessage> &messages, bool enable_thinking);

    // Samples and decodes one token. Returns false when generation is finished
    // (end-of-generation token, context full or cancellation). `piece` receives
    // the next chunk of valid UTF-8 text (may be empty when a multibyte
    // character is split across tokens).
    bool next_token(std::string &piece);

    void cancel() { cancelled_.store(true); }
    bool context_full() const { return context_full_; }
    int n_ctx() const { return n_ctx_; }
    int n_past() const { return (int) cached_.size(); }
    std::string description() const;

private:
    ChatSession() = default;
    bool decode(const llama_token *tokens, int count, int start_pos);
    void reset_sampler();

    llama_model *model_ = nullptr;
    llama_context *ctx_ = nullptr;
    llama_sampler *sampler_ = nullptr;
    common_chat_templates_ptr templates_;
    SessionParams params_;
    int n_ctx_ = 0;

    std::vector<llama_token> cached_;   // tokens currently stored in the KV cache
    std::string pending_utf8_;          // bytes of an incomplete UTF-8 sequence
    std::atomic<bool> cancelled_{false};
    bool context_full_ = false;
};

// Multi-threaded sequential read benchmark; returns GB/s.
double benchmark_memory_bandwidth(int megabytes, int threads);

}  // namespace pocketmind
