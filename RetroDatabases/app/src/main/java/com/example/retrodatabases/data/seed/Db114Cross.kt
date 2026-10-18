package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiment of 900: the spans of Later Baekje and Later Goguryeo, counted in Python and in C. */
val db114Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 114,
        kind = Kind.CROSS,
        section = "Korea",
        title = "The spans of Later Baekje and Later Goguryeo in Python and C",
        originalTitle = "The spans of Later Baekje and Later Goguryeo in Python and C",
        author = "Gyeon Hwon and Gung Ye",
        year = 900,
        summary = "Python and C programs count the years of Later Baekje from 900 to 936 and of Later Goguryeo from 901 to 918, and a script checks that both give the same total of 53 years.",
        details = "Both programs use plain integer subtraction on the same dates, so the run tests the two languages on the same arithmetic rather than on the history. The dates are the years usually given for each state.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### spans.py
            BAEKJE = (900, 936)
            GOGURYEO = (901, 918)
            spans = [BAEKJE[1] - BAEKJE[0], GOGURYEO[1] - GOGURYEO[0]]
            print("Later Baekje:", spans[0], "years")
            print("Later Goguryeo:", spans[1], "years")
            print("together:", sum(spans), "years")
            ### spans.c
            #include <stdio.h>

            int main(void) {
                int baekje = 936 - 900;
                int goguryeo = 918 - 901;
                printf("Later Baekje: %d years\n", baekje);
                printf("Later Goguryeo: %d years\n", goguryeo);
                printf("together: %d years\n", baekje + goguryeo);
                return 0;
            }
            ### run.sh
            python3 spans.py > python.txt
            cc -o spans_c spans.c
            ./spans_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
