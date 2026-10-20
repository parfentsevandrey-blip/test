package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One cross-code piece of 570 to 580: the years of the Byzantine war with Persia from 572 to 591, in Python and in C. */
val db147Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 147,
        kind = Kind.CROSS,
        section = "Byzantine",
        title = "The Byzantine war with Persia from 572 to 591, counted in years in Python and C",
        originalTitle = "Byzantine-Persian war of 572 to 591",
        author = "Justin II, Khosrow I and Maurice",
        year = 572,
        summary = "The war between Byzantium and Persia that began in 572 and ended in 591 lasted 19 years, a count that a Python program and a C program print the same way.",
        details = "Justin II stopped the yearly payments to Persia and the war began in 572, and the peace made under Maurice in 591 ended it. The two programs print the same line, and a script checks that their outputs agree.",
        rare = true,
        instructions = """
            1. Save the example as war_span.py, war_span.c and run.sh in one folder.
            2. Run it: sh run.sh
        """,
        example = """
            ### war_span.py
            START = 572  # the war begins when Justin II stops the payments to Persia
            END = 591    # the peace made under Maurice ends it
            print("war years:", END - START)
            ### war_span.c
            #include <stdio.h>

            int main(void) {
                int start = 572, end = 591;
                printf("war years: %d\n", end - start);
                return 0;
            }
            ### run.sh
            python3 war_span.py > python.txt
            cc -o war_span_c war_span.c
            ./war_span_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
