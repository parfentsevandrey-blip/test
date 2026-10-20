package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One cross-code piece of 610 to 620: three dates of the early 610s subtracted in Python and in C. */
val db143Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 143,
        kind = Kind.CROSS,
        section = "Japanese",
        title = "The gaps from Docho's entry in 610 to the Hokke gisho of 615 and the Wuyin calendar of 618, counted in Python and C",
        originalTitle = "Hokke gisho",
        author = "Compilers of the Nihon Shoki",
        year = 610,
        summary = "Three dates of the 610s are subtracted by a Python program and by a C program, and a script checks that both print the same gaps.",
        details = "The gaps are 5 years from 610 to 615, 8 years from 610 to 618 and 3 years from 615 to 618, and the two programs print the same lines, so the check confirms the arithmetic. The dates are the usual ones: 610 for the Nihon Shoki entry, 615 for the traditional date of the Hokke gisho, and 618 for the Wuyin calendar, whose rollout some accounts give as 619.",
        rare = true,
        instructions = """
            1. Save the example as docho_gaps.py, docho_gaps.c and run.sh in one folder.
            2. Run it: sh run.sh
        """,
        example = """
            ### docho_gaps.py
            PAPER = 610  # the Nihon Shoki entry for the monk Docho, 610
            HOKKE = 615  # the Hokke gisho, traditionally dated 615
            WUYIN = 618  # the Wuyin calendar, adopted 618
            print("gap from 610 to 615:", HOKKE - PAPER)
            print("gap from 610 to 618:", WUYIN - PAPER)
            print("gap from 615 to 618:", WUYIN - HOKKE)
            ### docho_gaps.c
            #include <stdio.h>

            int main(void) {
                int paper = 610, hokke = 615, wuyin = 618;
                printf("gap from 610 to 615: %d\n", hokke - paper);
                printf("gap from 610 to 618: %d\n", wuyin - paper);
                printf("gap from 615 to 618: %d\n", wuyin - hokke);
                return 0;
            }
            ### run.sh
            python3 docho_gaps.py > python.txt
            cc -o docho_gaps_c docho_gaps.c
            ./docho_gaps_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
