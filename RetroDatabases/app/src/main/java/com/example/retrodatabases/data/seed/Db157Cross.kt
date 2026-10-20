package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiment of 471 to 479: the years from the Inariyama sword to the founding of the Southern Qi, in Python and C. */
val db157Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 157,
        kind = Kind.CROSS,
        section = "Years",
        title = "The years from the Inariyama sword to the Southern Qi in Python and C",
        originalTitle = "The years from the Inariyama sword to the Southern Qi in Python and C",
        author = "Chroniclers of Japan and China",
        year = 471,
        summary = "A short program counts the years from the Inariyama sword's date to the founding of the Southern Qi in Python and in C, and a run script checks that both print the same result.",
        details = "The dates are the commonly given ones, 471 for the sword and 479 for the Southern Qi, and the answer is eight years. The run script compares the two outputs line by line.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### sword.py
            inariyama = 471     # the Inariyama sword's date, as most Japanese scholars give it
            southern_qi = 479   # the founding of the Southern Qi dynasty
            print("years from the Inariyama sword to the Southern Qi:", southern_qi - inariyama)
            ### sword.c
            #include <stdio.h>

            int main(void) {
                int inariyama = 471;     /* the Inariyama sword's date, as most Japanese scholars give it */
                int southern_qi = 479;   /* the founding of the Southern Qi dynasty */
                printf("years from the Inariyama sword to the Southern Qi: %d\n", southern_qi - inariyama);
                return 0;
            }
            ### run.sh
            python3 sword.py > python.txt
            cc -o sword_c sword.c
            ./sword_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
