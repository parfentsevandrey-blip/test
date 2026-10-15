package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 2020 to 2026: step-by-step explanations of how a mechanism works, with a small program to try it where the sandbox allows. */
val db5Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 5,
        kind = Kind.GUIDE,
        section = "Networking",
        title = "How HTTP/3 runs over QUIC",
        originalTitle = "QUIC (RFC 9000) and HTTP/3 (RFC 9114)",
        author = "The IETF QUIC and HTTP working groups",
        year = 2022,
        summary = "HTTP/3 carries requests over QUIC streams on top of UDP, so one lost packet stalls only the stream it belongs to.",
        details = "QUIC was published as RFC 9000 in May 2021, and HTTP/3 as RFC 9114 in June 2022. Each stream has its own ordering, so a lost packet delays only its own stream. " +
            "The handshake combines transport setup with TLS 1.3, so a connection can start sooner than with older stacks.",
        instructions = """
            1. Use a curl build that supports HTTP/3.
            2. Run: curl -I --http3 https://example.com
            3. Look for the HTTP/3 status line in the output.
        """,
    ),

    entry(
        databaseId = 5,
        kind = Kind.GUIDE,
        section = "Machine learning",
        title = "How a language model picks the next token",
        originalTitle = "Next-token sampling",
        author = "Written as a teaching example",
        year = 2023,
        summary = "A model scores every possible next token, turns the scores into probabilities and samples one, step after step.",
        details = "A language model outputs one score per token in its vocabulary. A softmax turns the scores into probabilities, and the next token is drawn from them, " +
            "so the same prompt can give different answers. The program below uses a tiny table of scores to show the loop.",
        instructions = """
            1. Save the code as next_token.py.
            2. Run it: python3 next_token.py
            3. Change the seed in random.seed to see other sentences.
        """,
        example = """
            import math
            import random

            vocab = ["the", "cat", "sat", "on", "mat", "."]

            # Scores (logits) for the next token after each word; higher means more likely.
            logits = {
                "the": [0.0, 2.0, 0.0, 0.0, 1.5, 0.0],
                "cat": [0.0, 0.0, 2.5, 0.0, 0.0, 0.5],
                "sat": [0.0, 0.0, 0.0, 2.0, 0.0, 0.0],
                "on": [2.0, 0.0, 0.0, 0.0, 2.0, 0.0],
                "mat": [0.0, 0.0, 0.0, 0.0, 0.0, 2.5],
            }

            def softmax(scores):
                top = max(scores)
                exps = [math.exp(s - top) for s in scores]
                total = sum(exps)
                return [e / total for e in exps]

            random.seed(7)
            word = "the"
            sentence = [word]
            for _ in range(6):
                probs = softmax(logits.get(word, [0.0] * len(vocab)))
                word = random.choices(vocab, weights=probs)[0]
                sentence.append(word)
                if word == ".":
                    break
            print(" ".join(sentence))
        """,
    ),

    entry(
        databaseId = 5,
        kind = Kind.GUIDE,
        section = "Supply chain",
        title = "How a signed container image is checked",
        originalTitle = "Sigstore and cosign signatures",
        author = "The Sigstore project",
        year = 2021,
        summary = "A signature stored with the image lets a registry or cluster check who built it, using short-lived certificates tied to an identity.",
        details = "Sigstore began in 2021, and its cosign tool signs container images with a key or with a short-lived certificate tied to an identity provider. " +
            "The signature is stored in the registry next to the image, and a policy engine in the cluster checks it before a pod starts.",
        instructions = """
            1. Sign an image after pushing it: cosign sign <image>
            2. Verify it, naming the identity that should have signed it: cosign verify <image>
            3. Refuse to run any image whose verification fails.
        """,
        rare = true,
    ),

    entry(
        databaseId = 5,
        kind = Kind.GUIDE,
        section = "Version control",
        title = "How Git signs commits with SSH keys",
        originalTitle = "Git SSH signing (Git 2.34)",
        author = "The Git project",
        year = 2021,
        summary = "Git can sign commits with an SSH key, so the key that logs in to servers can also prove who made a commit.",
        details = "Git 2.34 added SSH signing in November 2021. The signature is stored in the commit header, and git verify-commit checks it against a list of allowed signers. " +
            "Teams that already had SSH keys avoided setting up GPG.",
        instructions = """
            1. Point Git at SSH signing: git config gpg.format ssh
            2. Name the key: git config user.signingkey ~/.ssh/id_ed25519.pub
            3. Commit with git commit -S, then check it with git log --show-signature.
        """,
        rare = true,
    ),

    entry(
        databaseId = 5,
        kind = Kind.GUIDE,
        section = "Web layout",
        title = "How a container query picks a layout",
        originalTitle = "CSS container queries",
        author = "The W3C CSS Working Group",
        year = 2023,
        summary = "A container query styles an element by the width of its nearest container, not by the width of the whole window.",
        details = "Container queries shipped in Chrome and Safari in 2022 and in Firefox in early 2023. A card can change its layout when the column it sits in gets narrower, " +
            "so components no longer need to know where on the page they are placed.",
        example = """
            .card-list {
              container-type: inline-size;
            }

            @container (min-width: 30em) {
              .card {
                display: grid;
                grid-template-columns: 8em 1fr;
              }
            }
        """,
    ),
)
