package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1520 to 1530: a hundred birds for a hundred coins, and Heron's steps to a square root, each in Python and C. */
val db52Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 52,
        kind = Kind.CROSS,
        section = "Puzzles",
        title = "Birds for a hundred coins in Python and C",
        originalTitle = "The hundred birds for a hundred coins",
        author = "Merchant arithmetic puzzle",
        year = 1527,
        summary = "A hundred birds bought for a hundred coins have four solutions, and a script checks that the Python and C programs print the same four.",
        details = "A cock costs five coins, a hen three and three chicks one coin, and the puzzle is the kind that merchant arithmetic books of the 1520s set. Both programs search every count of cocks and hens, and the run script compares their outputs line by line.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### hundred.py
            for cocks in range(101):
                for hens in range(101 - cocks):
                    chicks = 100 - cocks - hens
                    if 15 * cocks + 9 * hens + chicks == 300:   # 100 coins: 15 cocks, 9 hens and chicks, in thirds
                        print(f"cocks {cocks} hens {hens} chicks {chicks}")
            ### hundred.c
            #include <stdio.h>

            int main(void) {
                for (int cocks = 0; cocks <= 100; cocks++) {
                    for (int hens = 0; hens <= 100 - cocks; hens++) {
                        int chicks = 100 - cocks - hens;
                        if (15 * cocks + 9 * hens + chicks == 300) {
                            printf("cocks %d hens %d chicks %d\n", cocks, hens, chicks);
                        }
                    }
                }
                return 0;
            }
            ### run.sh
            python3 hundred.py > python.txt
            cc -o hundred_c hundred.c
            ./hundred_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

    entry(
        databaseId = 52,
        kind = Kind.CROSS,
        section = "Roots",
        title = "Heron's steps to a square root in Python and C",
        originalTitle = "Heron's rule for a square root",
        author = "Heron of Alexandria, with Christoff Rudolff's root sign",
        year = 1525,
        summary = "Heron's rule finds a square root by averaging a guess with two over the guess, and a script checks that the Python and C programs print the same four steps.",
        details = "Heron's rule is an ancient method, and Rudolff's Coss of 1525 wrote the root sign that such roots are marked with. Both programs use the same double-precision steps for the square root of 2, so their printed decimals match, and the run script compares the two outputs.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### heron.py
            x = 1.0
            for step in range(1, 5):
                x = (x + 2.0 / x) / 2.0
                print(f"step {step}: {x:.6f}")
            ### heron.c
            #include <stdio.h>

            int main(void) {
                double x = 1.0;
                for (int step = 1; step <= 4; step++) {
                    x = (x + 2.0 / x) / 2.0;
                    printf("step %d: %.6f\n", step, x);
                }
                return 0;
            }
            ### run.sh
            python3 heron.py > python.txt
            cc -o heron_c heron.c
            ./heron_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
