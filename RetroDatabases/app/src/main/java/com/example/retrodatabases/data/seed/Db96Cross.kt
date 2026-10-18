package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiment of 1080 to 1090: the years of Sima Guang's chronicle, from 403 BC to 959 AD, counted in Python and in C. */
val db96Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 96,
        kind = Kind.CROSS,
        section = "Dates",
        title = "The years from 403 BC to 959 AD covered by Sima Guang's chronicle, in Python and C",
        originalTitle = "Zizhi tongjian",
        author = "Sima Guang",
        year = 1084,
        summary = "Python and C programs count the years of Sima Guang's chronicle from 403 BC to 959 AD, and a script checks that both give 1362.",
        details = "The programs use astronomical year numbers, with 403 BC written as -402, so both ends are counted and no date library is needed. The total of 1362 is the span usually given for the chronicle, and the run tests the arithmetic rather than the history.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### zizhi_span.py
            first = -402    # 403 BC, with year 0 counted
            last = 959      # 959 AD
            print("years covered by the chronicle:", last - first + 1)
            ### zizhi_span.c
            #include <stdio.h>

            int main(void) {
                int first = -402;   /* 403 BC, with year 0 counted */
                int last = 959;     /* 959 AD */
                printf("years covered by the chronicle: %d\n", last - first + 1);
                return 0;
            }
            ### run.sh
            python3 zizhi_span.py > python.txt
            cc -o zizhi_span_c zizhi_span.c
            ./zizhi_span_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
