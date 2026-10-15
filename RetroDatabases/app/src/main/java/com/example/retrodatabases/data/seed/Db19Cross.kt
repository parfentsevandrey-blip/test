package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1850 to 1860: the eight queens count and a digraph cipher, each in Python and C. */
val db19Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 19,
        kind = Kind.CROSS,
        section = "Backtracking",
        title = "Eight queens counted by backtracking, in Python and C",
        originalTitle = "Solutions of the eight queens problem (1850)",
        author = "Franz Nauck",
        year = 1850,
        summary = "Backtracking places eight queens row by row, and the search finds the 92 placements of the classic puzzle in both Python and C.",
        details = "Max Bezzel posed the problem in 1848, and Nauck published solutions in 1850, reporting the total of 92. Gauss also took up the question in 1850, but his tallies of 72 and 76 fell short of 92, and Nauck's count is the one usually credited.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### queens.py
            def solve(n):
                solutions = []
                queens = []

                def safe(col):
                    row = len(queens)
                    for r, c in enumerate(queens):
                        if c == col or abs(c - col) == row - r:
                            return False
                    return True

                def place():
                    if len(queens) == n:
                        solutions.append(tuple(c + 1 for c in queens))
                        return
                    for col in range(n):
                        if safe(col):
                            queens.append(col)
                            place()
                            queens.pop()

                place()
                return solutions

            found = solve(8)
            print("solutions:", len(found))
            print("first solution:", " ".join(str(c) for c in found[0]))
            ### queens.c
            #include <stdio.h>
            #include <stdlib.h>

            #define N 8

            static int cols[N];
            static long count = 0;
            static int first[N];
            static int have_first = 0;

            static int safe(int row, int col) {
                for (int r = 0; r < row; r++) {
                    int c = cols[r];
                    if (c == col || abs(c - col) == row - r) {
                        return 0;
                    }
                }
                return 1;
            }

            static void place(int row) {
                if (row == N) {
                    if (!have_first) {
                        for (int r = 0; r < N; r++) {
                            first[r] = cols[r] + 1;
                        }
                        have_first = 1;
                    }
                    count++;
                    return;
                }
                for (int col = 0; col < N; col++) {
                    if (safe(row, col)) {
                        cols[row] = col;
                        place(row + 1);
                    }
                }
            }

            int main(void) {
                place(0);
                printf("solutions: %ld\n", count);
                printf("first solution:");
                for (int r = 0; r < N; r++) {
                    printf(" %d", first[r]);
                }
                printf("\n");
                return 0;
            }
            ### run.sh
            python3 queens.py > python.txt
            cc -o queens_c queens.c
            ./queens_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

    entry(
        databaseId = 19,
        kind = Kind.CROSS,
        section = "Ciphers",
        title = "Playfair digraph cipher with the MONARCHY key, in Python and C",
        originalTitle = "Cipher of Wheatstone, popularised by Playfair (1854)",
        author = "Charles Wheatstone, promoted by Lyon Playfair",
        year = 1854,
        summary = "Playfair's digraph cipher encrypts pairs of letters with a 5 by 5 key square, and the example turns HELLO into CFSUPM in Python and C.",
        details = "Wheatstone devised the cipher in 1854, and Lyon Playfair promoted it, which gave it the name. The example uses the MONARCHY key square, inserts an X between doubled letters, and decrypts the result to HELXLO.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### playfair.py
            ALPHABET = "ABCDEFGHIKLMNOPQRSTUVWXYZ"   # J is merged into I

            def key_square(key):
                letters = []
                for ch in (key.upper() + ALPHABET).replace("J", "I"):
                    if ch in ALPHABET and ch not in letters:
                        letters.append(ch)
                return letters

            def digraphs(text):
                clean = [c for c in text.upper().replace("J", "I") if c.isalpha()]
                pairs = []
                i = 0
                while i < len(clean):
                    a = clean[i]
                    b = clean[i + 1] if i + 1 < len(clean) else "X"
                    if a == b:
                        pairs.append((a, "X"))
                        i += 1
                    else:
                        pairs.append((a, b))
                        i += 2
                return pairs

            def transform(square, pairs, step):
                out = []
                for a, b in pairs:
                    ra, ca = divmod(square.index(a), 5)
                    rb, cb = divmod(square.index(b), 5)
                    if ra == rb:
                        out.append(square[ra * 5 + (ca + step) % 5] + square[rb * 5 + (cb + step) % 5])
                    elif ca == cb:
                        out.append(square[((ra + step) % 5) * 5 + ca] + square[((rb + step) % 5) * 5 + cb])
                    else:
                        out.append(square[ra * 5 + cb] + square[rb * 5 + ca])
                return "".join(out)

            square = key_square("MONARCHY")
            cipher = transform(square, digraphs("HELLO"), 1)
            plain = transform(square, [(cipher[i], cipher[i + 1]) for i in range(0, len(cipher), 2)], -1)
            print("encrypted:", cipher)
            print("decrypted:", plain)
            ### playfair.c
            #include <stdio.h>
            #include <string.h>
            #include <ctype.h>

            static const char *ALPHA = "ABCDEFGHIKLMNOPQRSTUVWXYZ";

            static int find(const char *square, char ch) {
                for (int i = 0; i < 25; i++) {
                    if (square[i] == ch) {
                        return i;
                    }
                }
                return -1;
            }

            static void key_square(const char *key, char *square) {
                int used[26] = {0};
                int n = 0;
                for (int pass = 0; pass < 2; pass++) {
                    const char *src = (pass == 0) ? key : ALPHA;
                    for (size_t i = 0; i < strlen(src); i++) {
                        char ch = (char)toupper((unsigned char)src[i]);
                        if (ch == 'J') {
                            ch = 'I';
                        }
                        if (ch >= 'A' && ch <= 'Z' && !used[ch - 'A']) {
                            used[ch - 'A'] = 1;
                            square[n++] = ch;
                        }
                    }
                }
            }

            static void prepare(const char *text, char *out) {
                char clean[64];
                size_t n = 0;
                for (size_t i = 0; text[i] != '\0'; i++) {
                    if (isalpha((unsigned char)text[i])) {
                        char ch = (char)toupper((unsigned char)text[i]);
                        if (ch == 'J') {
                            ch = 'I';
                        }
                        clean[n++] = ch;
                    }
                }
                size_t k = 0;
                size_t i = 0;
                while (i < n) {
                    char a = clean[i];
                    char b = (i + 1 < n) ? clean[i + 1] : 'X';
                    out[k++] = a;
                    if (a == b) {
                        out[k++] = 'X';
                        i += 1;
                    } else {
                        out[k++] = b;
                        i += 2;
                    }
                }
                out[k] = '\0';
            }

            static void transform(const char *square, const char *text, int step, char *out) {
                size_t len = strlen(text);
                size_t k = 0;
                for (size_t i = 0; i < len; i += 2) {
                    int p = find(square, text[i]);
                    int q = find(square, text[i + 1]);
                    int ra = p / 5, ca = p % 5, rb = q / 5, cb = q % 5;
                    if (ra == rb) {
                        out[k++] = square[ra * 5 + (ca + step + 5) % 5];
                        out[k++] = square[rb * 5 + (cb + step + 5) % 5];
                    } else if (ca == cb) {
                        out[k++] = square[((ra + step + 5) % 5) * 5 + ca];
                        out[k++] = square[((rb + step + 5) % 5) * 5 + cb];
                    } else {
                        out[k++] = square[ra * 5 + cb];
                        out[k++] = square[rb * 5 + ca];
                    }
                }
                out[k] = '\0';
            }

            int main(void) {
                char square[26];
                char prepared[64];
                char cipher[64];
                char plain[64];
                key_square("MONARCHY", square);
                prepare("HELLO", prepared);
                transform(square, prepared, 1, cipher);
                transform(square, cipher, -1, plain);
                printf("encrypted: %s\n", cipher);
                printf("decrypted: %s\n", plain);
                return 0;
            }
            ### run.sh
            python3 playfair.py > python.txt
            cc -o playfair_c playfair.c
            ./playfair_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),
)
