package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 2010 to 2020: step-by-step explanations of how a mechanism works, with a small program to try it where the sandbox allows. */
val db4Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 4,
        kind = Kind.GUIDE,
        section = "Web security",
        title = "How JWT tokens are signed",
        originalTitle = "JSON Web Token (RFC 7519)",
        author = "Michael Jones, John Bradley and Nat Sakimura",
        year = 2015,
        summary = "A JSON Web Token is three base64url parts, and its signature is an HMAC over the first two.",
        details = "JSON Web Tokens were standardised in May 2015 as RFC 7519. The header and the payload are JSON, encoded with base64url. " +
            "The signature is computed over the two encoded parts with a secret key, so a server can detect any change to the payload.",
        instructions = """
            1. Save the code as jwt_demo.py.
            2. Run it: python3 jwt_demo.py
            3. The first line is the token, and the second line says whether the signature checks out.
        """,
        example = """
            import base64
            import hashlib
            import hmac
            import json

            def b64url(data):
                return base64.urlsafe_b64encode(data).rstrip(b"=").decode("ascii")

            secret = b"demo-secret"
            header = b64url(json.dumps({"alg": "HS256", "typ": "JWT"}, separators=(",", ":")).encode())
            payload = b64url(json.dumps({"sub": "ann", "admin": False}, separators=(",", ":")).encode())
            signing_input = (header + "." + payload).encode("ascii")
            signature = b64url(hmac.new(secret, signing_input, hashlib.sha256).digest())
            print(header + "." + payload + "." + signature)

            expected = b64url(hmac.new(secret, signing_input, hashlib.sha256).digest())
            print("valid" if hmac.compare_digest(expected, signature) else "invalid")
        """,
        rare = true,
    ),

    entry(
        databaseId = 4,
        kind = Kind.GUIDE,
        section = "JavaScript",
        title = "How a promise chain runs",
        originalTitle = "Promises and the job queue",
        author = "ECMAScript committee (ES2015)",
        year = 2015,
        summary = "A promise runs its callbacks after the current code finishes, and a timer waits behind them in the queue.",
        details = "ES2015 standardised promises. Their callbacks run as microtasks as soon as the current script ends, before any timer callback. " +
            "Knowing this order explains why a log line from a then callback can appear after code that was written later.",
        instructions = """
            1. Save the code as order.js.
            2. Run it: node order.js
            3. Compare the order of the output lines with the order of the calls in the code.
        """,
        example = """
            console.log("start");
            Promise.resolve(1)
              .then(function (value) { console.log("then", value); return value + 1; })
              .then(function (value) { console.log("then again", value); });
            setTimeout(function () { console.log("timeout"); }, 0);
            console.log("end");
        """,
    ),

    entry(
        databaseId = 4,
        kind = Kind.GUIDE,
        section = "Containers",
        title = "How Docker layers an image",
        originalTitle = "Image layers and the writable layer",
        author = "Docker documentation",
        year = 2013,
        summary = "Each instruction in a Dockerfile adds a read-only layer, and a running container adds one writable layer on top.",
        details = "Docker builds an image as a stack of layers, one for each instruction that changes the file system. Layers are cached and shared between images, " +
            "so a rebuild reuses everything below the first change. A running container adds a thin writable layer, which is discarded when the container is removed.",
        instructions = """
            1. Save the Dockerfile below in an empty folder, next to a file called hello.txt.
            2. Build it: docker build -t layers-demo .
            3. Look at the layers: docker history layers-demo
        """,
        example = """
            FROM alpine:3.8
            COPY hello.txt /hello.txt
            RUN echo "built once" > /built.txt
            CMD ["cat", "/hello.txt"]
        """,
    ),

    entry(
        databaseId = 4,
        kind = Kind.GUIDE,
        section = "Web layout",
        title = "How a CSS grid places its items",
        originalTitle = "CSS Grid Layout",
        author = "W3C CSS Working Group",
        year = 2017,
        summary = "A grid container divides space into rows and columns, and each item fills a cell by line number or by auto-placement.",
        details = "CSS Grid Layout reached all major browsers in 2017. A container declares its tracks with fr units or fixed sizes, and its items are placed in cells by line numbers. " +
            "Items that name no cell fill the next free one, so the layout can change with a media query while the markup stays the same.",
        instructions = """
            1. Put the CSS below in a page with a container and four child elements: header, menu, main and footer.
            2. Open the page in a browser and resize the window.
        """,
        example = """
            .page {
              display: grid;
              grid-template-columns: 200px 1fr;
              grid-template-rows: auto 1fr auto;
            }
            .header { grid-column: 1 / -1; }
            .menu   { grid-row: 2; }
            .main   { grid-column: 2; grid-row: 2; }
            .footer { grid-column: 1 / -1; }
        """,
    ),

    entry(
        databaseId = 4,
        kind = Kind.GUIDE,
        section = "Web security",
        title = "How a TLS 1.3 handshake takes one round trip",
        originalTitle = "TLS 1.3 (RFC 8446)",
        author = "Eric Rescorla and the IETF TLS working group",
        year = 2018,
        summary = "TLS 1.3 sends the client's key share in its first message, so the server can answer with its own key share.",
        details = "RFC 8446 was published in August 2018. The client sends its key share together with the cipher suites it supports. " +
            "The server replies with its key share and certificate, and both sides derive the keys from that exchange, so the rest of the handshake is encrypted.",
        instructions = """
            1. Open a terminal with OpenSSL installed.
            2. Run: openssl s_client -connect example.com:443 -tls1_3
            3. Read the handshake messages in the output.
        """,
        rare = true,
    ),
)
