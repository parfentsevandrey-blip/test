package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1540 to 1545: Etxepare's poems tallied by theme, and Stifel's exponents, each in Python and C. */
val db50Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 50,
        kind = Kind.CROSS,
        section = "Poetry",
        title = "Etxepare's fifteen poems tallied by theme in Python and C",
        originalTitle = "Linguae Vasconum Primitiae",
        author = "Bernard Etxepare",
        year = 1545,
        summary = "A Python program and a C program tally the fifteen poems of Etxepare's book by theme, and a script checks that the two outputs agree.",
        details = "The book's fifteen poems are two religious pieces, ten love poems, one about a prison stay and two closing pieces that urge the language into the world. The two programs count them from fixed lists, and the run script compares their outputs line by line.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### poems.py
            from collections import Counter

            themes = ["religious"] * 2 + ["love"] * 10 + ["prison"] + ["closing"] * 2
            tally = Counter(themes)
            print("poems:", len(themes))
            for theme in ("religious", "love", "prison", "closing"):
                print(f"{theme}: {tally[theme]}")
            ### poems.c
            #include <stdio.h>

            int main(void) {
                const char *names[4] = {"religious", "love", "prison", "closing"};
                int themes[15] = {0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 2, 3, 3};
                int counts[4] = {0, 0, 0, 0};
                int total = 0;
                for (int i = 0; i < 15; i++) {
                    counts[themes[i]]++;
                }
                for (int t = 0; t < 4; t++) {
                    total += counts[t];
                }
                printf("poems: %d\n", total);
                for (int t = 0; t < 4; t++) {
                    printf("%s: %d\n", names[t], counts[t]);
                }
                return 0;
            }
            ### run.sh
            python3 poems.py > python.txt
            cc -o poems_c poems.c
            ./poems_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

    entry(
        databaseId = 50,
        kind = Kind.CROSS,
        section = "Powers",
        title = "Stifel's exponents in Python and C",
        originalTitle = "Arithmetica integra",
        author = "Michael Stifel",
        year = 1544,
        summary = "A Python program and a C program multiply powers of two and add their exponents, and a script checks that the two outputs are the same.",
        details = "Stifel's Arithmetica integra of 1544 set out powers of two against their exponents, and it noted that multiplying powers adds the exponents. The two programs check three products, and the run script compares their printed lines.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### exponents.py
            pairs = ((3, 4), (2, 5), (6, 3))
            for a, b in pairs:
                print(f"2^{a} times 2^{b} = {2 ** a * 2 ** b}, which is 2^{a + b}")
            ### exponents.c
            #include <stdio.h>

            int main(void) {
                int pairs[3][2] = {{3, 4}, {2, 5}, {6, 3}};
                for (int i = 0; i < 3; i++) {
                    int a = pairs[i][0];
                    int b = pairs[i][1];
                    long product = (1L << a) * (1L << b);
                    printf("2^%d times 2^%d = %ld, which is 2^%d\n", a, b, product, a + b);
                }
                return 0;
            }
            ### run.sh
            python3 exponents.py > python.txt
            cc -o exponents_c exponents.c
            ./exponents_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
