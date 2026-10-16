package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1365 and 1369: the one bits of the year 1365 and the triangular numbers below 1369, each in Python and C. */
val db68Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 68,
        kind = Kind.CROSS,
        section = "Bits",
        title = "The one bits of the year 1365 in Python and C",
        originalTitle = "Binary digits",
        author = "No single author",
        year = 1365,
        summary = "The year 1365 has six one bits in binary, and a Python program and a C program print the same binary form and count, which a script checks.",
        details = "The number 1365 is 10101010101 in binary, and the example prints that form and the count of one bits. The run script compares the two outputs line by line, so the example checks that the two languages agree.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### bits.py
            year = 1365
            print("binary:", bin(year)[2:])
            print("one bits:", bin(year).count("1"))
            ### bits.c
            #include <stdio.h>

            int main(void) {
                unsigned int year = 1365;
                int ones = 0;
                for (unsigned int v = year; v; v >>= 1) {
                    ones += v & 1u;
                }
                printf("binary: ");
                for (int bit = 10; bit >= 0; bit--) {
                    printf("%u", (year >> bit) & 1u);
                }
                printf("\n");
                printf("one bits: %d\n", ones);
                return 0;
            }
            ### run.sh
            python3 bits.py > python.txt
            cc -o bits_c bits.c
            ./bits_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

    entry(
        databaseId = 68,
        kind = Kind.CROSS,
        section = "Series",
        title = "The triangular numbers that stay below 1369 in Python and C",
        originalTitle = "Triangular numbers",
        author = "No single author",
        year = 1369,
        summary = "The triangular numbers below 1369 are counted up to the last one, 1326, by a Python program and a C program that must print the same two lines.",
        details = "Triangular numbers are sums of the first whole numbers, and the example stops at the last one that does not pass 1369. The run script compares the two outputs line by line.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### triangles.py
            LIMIT = 1369
            n = 0
            last = 0
            while (n + 1) * (n + 2) // 2 <= LIMIT:
                n += 1
                last = n * (n + 1) // 2
            print("count:", n)
            print("largest:", last)
            ### triangles.c
            #include <stdio.h>

            int main(void) {
                const int limit = 1369;
                int n = 0;
                int last = 0;
                while ((n + 1) * (n + 2) / 2 <= limit) {
                    n++;
                    last = n * (n + 1) / 2;
                }
                printf("count: %d\n", n);
                printf("largest: %d\n", last);
                return 0;
            }
            ### run.sh
            python3 triangles.py > python.txt
            cc -o triangles_c triangles.c
            ./triangles_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
