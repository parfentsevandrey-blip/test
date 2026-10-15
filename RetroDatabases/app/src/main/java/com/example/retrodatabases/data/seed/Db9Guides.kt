package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 2026 to 2036: the techniques the forecast relies on, each with a small program that runs today. */
val db9Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 9,
        kind = Kind.GUIDE,
        section = "Security",
        title = "How a capability token limits an agent",
        originalTitle = "Capability-based security (Dennis and Van Horn, 1966)",
        author = "Jack Dennis and Earl Van Horn",
        year = 2027,
        summary = "A capability is a token that names what its holder may do, so an agent is given only the tools it needs and nothing more.",
        details = "The token carries a scope and an expiry time, and every tool call checks the token before it runs. The idea dates from 1966. The example runs four calls against one token and prints which are allowed and why the others are refused.",
        instructions = """
            1. Save the example as capability_token.py.
            2. Run it: python3 capability_token.py
        """,
        example = """
            class Token:
                def __init__(self, holder, scopes, expires_at):
                    self.holder = holder
                    self.scopes = set(scopes)
                    self.expires_at = expires_at

            def check(token, scope, now):
                # A call is allowed only if the token has not expired and names the scope.
                if now >= token.expires_at:
                    return False, "expired"
                if scope not in token.scopes:
                    return False, "scope not granted"
                return True, "ok"

            token = Token("research agent", ["search", "read"], expires_at=100)
            for now, scope in [(10, "search"), (20, "read"), (30, "send"), (100, "read")]:
                allowed, reason = check(token, scope, now)
                print(f"t={now} {scope}: " + ("allowed" if allowed else f"refused ({reason})"))
        """,
    ),

    entry(
        databaseId = 9,
        kind = Kind.GUIDE,
        section = "Local-first",
        title = "How a G-counter merges replicas",
        originalTitle = "Conflict-free replicated data types (Shapiro and collaborators, 2011)",
        author = "Marc Shapiro and collaborators",
        year = 2030,
        summary = "Copies of a counter change offline and merge without conflict, because each copy counts in its own slot and the merge takes the larger value.",
        details = "This is the grow-only counter. Each replica increments only its own slot, and a merge takes the maximum of each slot, so merges in any order give the same total. The example merges three replicas in two orders and checks that the results match.",
        instructions = """
            1. Save the example as gcounter.py.
            2. Run it: python3 gcounter.py
        """,
        example = """
            def increment(counter, replica):
                counter = dict(counter)
                counter[replica] = counter.get(replica, 0) + 1
                return counter

            def merge(a, b):
                # Each replica's slot takes the larger value, so the order of merges does not matter.
                return {r: max(a.get(r, 0), b.get(r, 0)) for r in set(a) | set(b)}

            def total(counter):
                return sum(counter.values())

            # Three replicas count while they are apart.
            a = increment(increment({}, "a"), "a")                       # a: 2
            b = increment({}, "b")                                       # b: 1
            c = increment(increment(increment({}, "c"), "c"), "c")       # c: 3

            left = merge(merge(a, b), c)
            right = merge(c, merge(b, a))
            print(sorted(left.items()), total(left))
            print(sorted(right.items()), total(right))
            print("same result:", left == right)
        """,
    ),

    entry(
        databaseId = 9,
        kind = Kind.GUIDE,
        section = "Runtimes",
        title = "How a WebAssembly module is loaded and called",
        originalTitle = "WebAssembly Core Specification (W3C, 2019)",
        author = "W3C WebAssembly Working Group",
        year = 2028,
        summary = "A WebAssembly module is a byte format with typed functions. The host checks the bytes and then calls an exported function.",
        details = "The example writes a 41-byte module by hand: one function that adds two 32-bit integers. Node validates the module and runs it, and the sum wraps at 32 bits, so no compiler is needed.",
        instructions = """
            1. Save the example as wasm.js.
            2. Run it: node wasm.js
        """,
        example = """
            // A WebAssembly module that adds two 32-bit integers, written as raw bytes.
            const bytes = new Uint8Array([
              0x00, 0x61, 0x73, 0x6d, 0x01, 0x00, 0x00, 0x00,              // magic and version
              0x01, 0x07, 0x01, 0x60, 0x02, 0x7f, 0x7f, 0x01, 0x7f,        // type: (i32, i32) -> i32
              0x03, 0x02, 0x01, 0x00,                                      // one function of type 0
              0x07, 0x07, 0x01, 0x03, 0x61, 0x64, 0x64, 0x00, 0x00,        // export "add"
              0x0a, 0x09, 0x01, 0x07, 0x00, 0x20, 0x00, 0x20, 0x01, 0x6a, 0x0b // local.get 0, local.get 1, i32.add
            ]);

            console.log("valid module:", WebAssembly.validate(bytes));
            const instance = new WebAssembly.Instance(new WebAssembly.Module(bytes));
            console.log("add(2, 3) =", instance.exports.add(2, 3));
            console.log("add(40, 2) =", instance.exports.add(40, 2));
            console.log("add wraps at 32 bits:", instance.exports.add(2147483647, 1));
        """,
    ),

    entry(
        databaseId = 9,
        kind = Kind.GUIDE,
        section = "Security",
        title = "How hybrid key exchange combines two secrets",
        originalTitle = "HKDF, RFC 5869 (Krawczyk and Eronen, 2010)",
        author = "Hugo Krawczyk and Pasi Eronen",
        year = 2028,
        summary = "A hybrid key exchange runs a classic and a post-quantum exchange and feeds both secrets into one key derivation, so the key holds if either one does.",
        details = "The example implements HKDF with the standard library and checks it against test case 1 of RFC 5869. Then it derives one 16-byte key from two made-up shared secrets, the way a hybrid handshake combines its two exchanges.",
        instructions = """
            1. Save the example as hybrid_key.py.
            2. Run it: python3 hybrid_key.py
        """,
        example = """
            import hashlib
            import hmac

            def hkdf(ikm, salt, info, length):
                # Extract a fixed-length key from the input, then expand it to the length asked for (RFC 5869).
                prk = hmac.new(salt, ikm, hashlib.sha256).digest()
                okm = b""
                block = b""
                counter = 1
                while len(okm) < length:
                    block = hmac.new(prk, block + info + bytes([counter]), hashlib.sha256).digest()
                    okm += block
                    counter += 1
                return okm[:length]

            # Test case 1 of RFC 5869: the output must match the published value.
            okm = hkdf(bytes([0x0B] * 22), bytes(range(0x00, 0x0D)), bytes(range(0xF0, 0xFA)), 42)
            expected = "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865"
            print("RFC 5869 test case 1 matches:", okm.hex() == expected)

            # Two made-up shared secrets, one from each exchange, combined into one key.
            classic = hashlib.sha256(b"classic exchange").digest()
            post_quantum = hashlib.sha256(b"post-quantum exchange").digest()
            key = hkdf(classic + post_quantum, b"hybrid example", b"tls traffic key", 16)
            print("hybrid key:", key.hex())
        """,
    ),

    entry(
        databaseId = 9,
        kind = Kind.GUIDE,
        section = "Agents",
        title = "How a token bucket limits tool calls",
        originalTitle = "Token bucket rate limiting, a classic network technique",
        author = "Written as a teaching example",
        year = 2029,
        summary = "A bucket refills at a fixed rate and each call takes a token, so bursts are allowed up to the bucket size and the average rate stays fixed.",
        details = "The example runs ten calls at given times and prints which are allowed. Agent platforms use the same idea to cap tool calls per minute, so a runaway loop stops by itself.",
        instructions = """
            1. Save the example as token_bucket.py.
            2. Run it: python3 token_bucket.py
        """,
        example = """
            class TokenBucket:
                def __init__(self, capacity, refill_per_second):
                    self.capacity = capacity
                    self.refill = refill_per_second
                    self.tokens = capacity
                    self.last = 0.0

                def allow(self, now):
                    # Refill for the time since the last call, up to the bucket size, then spend one token.
                    self.tokens = min(self.capacity, self.tokens + (now - self.last) * self.refill)
                    self.last = now
                    if self.tokens >= 1:
                        self.tokens -= 1
                        return True
                    return False

            bucket = TokenBucket(capacity=3, refill_per_second=1.0)
            calls = [0.0, 0.1, 0.2, 0.3, 1.2, 1.3, 2.5, 2.6, 2.7, 2.8]
            print(" ".join("yes" if bucket.allow(t) else "no" for t in calls))
        """,
    ),
)
