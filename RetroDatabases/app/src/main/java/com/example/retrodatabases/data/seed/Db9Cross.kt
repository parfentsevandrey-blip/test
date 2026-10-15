package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 2026 to 2036: the same result from two languages, checked by a run script. */
val db9Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 9,
        kind = Kind.CROSS,
        section = "Python and JavaScript",
        title = "Python and JavaScript merge the same counters",
        originalTitle = "Merging G-counters in two languages",
        author = "Written as a teaching example",
        year = 2030,
        summary = "The same merge is written in Python and in JavaScript, and the two outputs are compared, as a conformance test does.",
        details = "The two programs share no code. They merge the same counters and print the same line, and the run script checks that the lines are identical.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### merge.py
            def merge(a, b):
                # Each slot takes the larger count, so the merge is the same in either order.
                return {r: max(a.get(r, 0), b.get(r, 0)) for r in set(a) | set(b)}

            a = {"a": 2, "b": 0}
            b = {"a": 1, "c": 3}
            merged = merge(a, b)
            slots = " ".join(f"{r}={v}" for r, v in sorted(merged.items()))
            print(slots + " total=" + str(sum(merged.values())))
            ### merge.js
            function merge(a, b) {
              const out = {};
              for (const r of new Set([...Object.keys(a), ...Object.keys(b)])) {
                out[r] = Math.max(a[r] || 0, b[r] || 0);
              }
              return out;
            }

            const a = { a: 2, b: 0 };
            const b = { a: 1, c: 3 };
            const merged = merge(a, b);
            const slots = Object.keys(merged).sort().map((r) => r + "=" + merged[r]);
            const total = Object.values(merged).reduce((x, y) => x + y, 0);
            console.log(slots.join(" ") + " total=" + total);
            ### run.sh
            python3 merge.py > python.txt
            node merge.js > node.txt
            cat python.txt node.txt
            diff python.txt node.txt && echo "same result"
        """,
    ),

    entry(
        databaseId = 9,
        kind = Kind.CROSS,
        section = "C and JavaScript",
        title = "C and JavaScript: the same FNV-1a hashes",
        originalTitle = "FNV-1a hash, by Fowler, Noll and Vo",
        author = "Glenn Fowler, Landon Curt Noll and Phong Vo",
        year = 2029,
        summary = "A hash function written in C and in JavaScript gives the same values for the same words, which is how cross-language conformance tests work.",
        details = "The C program prints the hash of three words, the JavaScript program prints the same, and the run script compares the two outputs. Known test values anchor the check.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### fnv.c
            #include <stdint.h>
            #include <stdio.h>

            /* FNV-1a, 32 bits: xor each byte in, then multiply by the FNV prime. */
            static uint32_t fnv1a(const char *s) {
                uint32_t h = 2166136261u;
                for (; *s; s++) {
                    h ^= (unsigned char)*s;
                    h *= 16777619u;
                }
                return h;
            }

            int main(void) {
                const char *words[] = {"hello", "world", "agent"};
                for (int i = 0; i < 3; i++) {
                    printf("%s %08x\n", words[i], fnv1a(words[i]));
                }
                return 0;
            }
            ### fnv.js
            // FNV-1a, 32 bits, with 32-bit multiplication so the arithmetic wraps like the C version.
            function fnv1a(s) {
              let h = 2166136261;
              for (const byte of Buffer.from(s, "utf8")) {
                h ^= byte;
                h = Math.imul(h, 16777619) >>> 0;
              }
              return h >>> 0;
            }

            for (const word of ["hello", "world", "agent"]) {
              console.log(word + " " + fnv1a(word).toString(16).padStart(8, "0"));
            }
            ### run.sh
            cc -o fnv fnv.c
            ./fnv > c.txt
            node fnv.js > js.txt
            cat c.txt
            diff c.txt js.txt && echo "same result"
        """,
    ),
)
