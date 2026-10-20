package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One cross-code piece of 540 to 550: the chapters of Benedict's Rule and of the Qimin yaoshu, added in Python and in C. */
val db150Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 150,
        kind = Kind.CROSS,
        section = "Latin and Chinese",
        title = "The 73 chapters of the Rule of Benedict and the 92 chapters of the Qimin yaoshu, added in Python and in C",
        originalTitle = "Chapters of two works",
        author = "Benedict of Nursia and Jia Sixie",
        year = 540,
        summary = "The chapters of Benedict's Rule and Jia Sixie's Qimin yaoshu are added by a Python program and by a C program that print the same total.",
        details = "The Rule has a prologue and 73 chapters, and the Qimin yaoshu has 92 chapters in ten volumes; the two programs print one line, and a script checks that the outputs agree. The total counts chapters of two different works, so it says nothing about their length or content.",
        instructions = """
            1. Save the example as chapters.py, chapters.c and run.sh in one folder.
            2. Run it: sh run.sh
        """,
        example = """
            ### chapters.py
            RULE = 73   # chapters of the Rule of Benedict, besides the prologue
            QIMIN = 92  # chapters of the Qimin yaoshu in its ten volumes
            print("chapters in the two works:", RULE + QIMIN)
            ### chapters.c
            #include <stdio.h>

            int main(void) {
                int rule = 73, qimin = 92;
                printf("chapters in the two works: %d\n", rule + qimin);
                return 0;
            }
            ### run.sh
            python3 chapters.py > python.txt
            cc -o chapters_c chapters.c
            ./chapters_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
