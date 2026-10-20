package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One cross-code piece of 500 to 510: the arithmetic, geometric and harmonic means of 2 and 8, computed in Python and in C. */
val db154Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 154,
        kind = Kind.CROSS,
        section = "Greek",
        title = "The arithmetic, geometric and harmonic means of 2 and 8, computed in Python and in C",
        originalTitle = "Introductio arithmetica",
        author = "Nicomachus of Gerasa, with Boethius",
        year = 500,
        summary = "The three means of 2 and 8 are computed by a Python program and by a C program that print the same three lines.",
        details = "Nicomachus defines the arithmetic, geometric and harmonic means of two numbers, and Boethius repeats them in Latin. For 2 and 8 the means are 5, 4 and 3.2, and a script checks that the two programs print the same lines.",
        instructions = """
            1. Save the example as means.py, means.c and run.sh in one folder.
            2. Run it: sh run.sh
        """,
        example = """
            ### means.py
            a, b = 2, 8
            print(f"arithmetic mean: {(a + b) / 2:.1f}")
            print(f"geometric mean: {(a * b) ** 0.5:.1f}")
            print(f"harmonic mean: {2 * a * b / (a + b):.1f}")
            ### means.c
            #include <math.h>
            #include <stdio.h>

            int main(void) {
                double a = 2, b = 8;
                printf("arithmetic mean: %.1f\n", (a + b) / 2);
                printf("geometric mean: %.1f\n", sqrt(a * b));
                printf("harmonic mean: %.1f\n", 2 * a * b / (a + b));
                return 0;
            }
            ### run.sh
            python3 means.py > python.txt
            cc -o means_c means.c -lm
            ./means_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
