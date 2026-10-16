package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1310 and 1313: right triangles with whole sides, and the ways to pay a sum of denari with three coins, each in Python and C. */
val db73Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 73,
        kind = Kind.CROSS,
        section = "Geometry",
        title = "Pythagorean triples with a hypotenuse up to thirty in Python and C",
        originalTitle = "Right triangles with whole sides",
        author = "Medieval arithmeticians",
        year = 1310,
        summary = "Right triangles with whole sides and a hypotenuse of at most thirty can be found by testing every pair of shorter sides, and a script checks that the Python and C programs print the same eleven triples.",
        details = "The example tests every pair of sides below the hypotenuse, so each triple is printed once with the smaller leg first, in order of the hypotenuse. The run script compares the two outputs line by line.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### triples.py
            count = 0
            for c in range(1, 31):
                for a in range(1, c):
                    for b in range(a + 1, c):
                        if a * a + b * b == c * c:
                            print(a, b, c)
                            count += 1
            print("triples:", count)
            ### triples.c
            #include <stdio.h>

            int main(void) {
                int count = 0;
                for (int c = 1; c <= 30; c++) {
                    for (int a = 1; a < c; a++) {
                        for (int b = a + 1; b < c; b++) {
                            if (a * a + b * b == c * c) {
                                printf("%d %d %d\n", a, b, c);
                                count++;
                            }
                        }
                    }
                }
                printf("triples: %d\n", count);
                return 0;
            }
            ### run.sh
            python3 triples.py > python.txt
            cc -o triples_c triples.c
            ./triples_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

    entry(
        databaseId = 73,
        kind = Kind.CROSS,
        section = "Money",
        title = "Ways to pay twelve denari with coins of one, two and four in Python and C",
        originalTitle = "Ways to pay with coins",
        author = "Merchants of the Italian towns",
        year = 1313,
        summary = "A merchant could pay a sum in many ways with coins of several values, and a script checks that the Python and C programs count the same sixteen ways to pay twelve denari.",
        details = "The count uses coins of one, two and four denari, and each way is a choice of how many coins of each value are used. The run script compares the two outputs line by line.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### coins.py
            count = 0
            for c4 in range(0, 12 // 4 + 1):
                for c2 in range(0, (12 - 4 * c4) // 2 + 1):
                    c1 = 12 - 4 * c4 - 2 * c2
                    if c1 >= 0:
                        count += 1
            print("ways to pay 12 denari:", count)
            ### coins.c
            #include <stdio.h>

            int main(void) {
                int count = 0;
                for (int c4 = 0; c4 <= 12 / 4; c4++) {
                    for (int c2 = 0; c2 <= (12 - 4 * c4) / 2; c2++) {
                        int c1 = 12 - 4 * c4 - 2 * c2;
                        if (c1 >= 0) {
                            count++;
                        }
                    }
                }
                printf("ways to pay 12 denari: %d\n", count);
                return 0;
            }
            ### run.sh
            python3 coins.py > python.txt
            cc -o coins_c coins.c
            ./coins_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
