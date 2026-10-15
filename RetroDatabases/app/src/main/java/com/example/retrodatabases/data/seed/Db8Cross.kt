package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1970 to 1980: a C encoder checked by Python, and a C factoring program reported by a shell pipeline. */
val db8Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 8,
        kind = Kind.CROSS,
        section = "C and Python",
        title = "C and Python: an LZ78 round trip",
        originalTitle = "LZ78 encoding in C, decoding in Python (Ziv and Lempel, 1978)",
        author = "Written as a teaching example",
        year = 1978,
        summary = "A C program encodes text with LZ78, and a Python script decodes the tokens it prints and checks that the original text comes back.",
        details = "The encoder keeps its dictionary as a trie, a table of child nodes for each character, and prints one token per phrase. The decoder rebuilds the phrases from the tokens. The two programs share only the format of the tokens.",
        instructions = """
            1. Save the files below in one folder.
            2. Compile the encoder: cc -o lz78 lz78.c
            3. Run the round trip: python3 roundtrip.py
        """,
        example = """
            ### lz78.c
            #include <stdio.h>
            #include <string.h>

            #define MAX_NODES 256

            /* LZ78 encoder: one "index character" line per phrase, with a trie of the phrases. */
            static int child[MAX_NODES][256];

            int main(void) {
                int next_index = 1;   /* index 0 is the empty phrase */
                int node = 0;
                int c;
                memset(child, 0, sizeof(child));
                while ((c = getchar()) != EOF) {
                    if (child[node][c] != 0) {
                        node = child[node][c];
                    } else {
                        printf("%d %c\n", node, c);
                        child[node][c] = next_index++;
                        node = 0;
                    }
                }
                return 0;
            }
            ### roundtrip.py
            import subprocess

            text = "abababababab"
            result = subprocess.run(["./lz78"], input=text, capture_output=True, text=True, check=True)
            tokens = []
            for line in result.stdout.splitlines():
                index, ch = line.split()
                tokens.append((int(index), ch))

            entries = [""]
            pieces = []
            for index, ch in tokens:
                phrase = entries[index] + ch
                entries.append(phrase)
                pieces.append(phrase)

            print("tokens:", tokens)
            print("round trip ok:", "".join(pieces) == text)
        """,
        rare = true,
    ),

    entry(
        databaseId = 8,
        kind = Kind.CROSS,
        section = "C and awk",
        title = "A factoring pipeline: C and awk",
        originalTitle = "Pollard's rho in C, reported by awk (1975)",
        author = "Written as a teaching example, after Pollard's method",
        year = 1975,
        summary = "A C program factors numbers with Pollard's rho method, and awk turns its output into a report, all in one shell pipeline.",
        details = "The factoring runs in C with 64-bit arithmetic, and each line of its output is a number with its two factors, smallest first. awk only formats the report, so the work is split between two tools that each do what they are good at.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the pipeline: sh run.sh
        """,
        example = """
            ### factor.c
            #include <stdio.h>

            static unsigned long long gcd(unsigned long long a, unsigned long long b) {
                while (b) {
                    unsigned long long t = a % b;
                    a = b;
                    b = t;
                }
                return a;
            }

            static unsigned long long rho(unsigned long long n) {
                unsigned long long c;
                for (c = 1; ; c++) {
                    unsigned long long x = 2, y = 2, d = 1;
                    while (d == 1) {
                        x = (x * x + c) % n;
                        y = (y * y + c) % n;
                        y = (y * y + c) % n;
                        d = gcd(x > y ? x - y : y - x, n);
                    }
                    if (d != n) {
                        return d;
                    }
                }
            }

            int main(void) {
                unsigned long long n;
                while (scanf("%llu", &n) == 1) {
                    unsigned long long f = rho(n);
                    unsigned long long g = n / f;
                    if (f > g) {
                        unsigned long long t = f;
                        f = g;
                        g = t;
                    }
                    printf("%llu %llu %llu\n", n, f, g);
                }
                return 0;
            }
            ### run.sh
            printf '8051\n10403\n1000001\n' > numbers.txt
            cc -O2 -o factor factor.c
            ./factor < numbers.txt | awk '{ printf "%s = %s x %s\n", $1, $2, $3 }'
        """,
        rare = true,
    ),
)
