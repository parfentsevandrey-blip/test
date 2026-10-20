package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One cross-code piece of 560 to 570: the 168 numbered Novels of the Greek text, counted by emperor in Python and in C, placed at 565. */
val db148Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 148,
        kind = Kind.CROSS,
        section = "Byzantine",
        title = "The 168 Novels of the Greek text, counted by emperor in Python and C",
        originalTitle = "Novellae (Greek text)",
        author = "Justinian, Justin II and Tiberius",
        year = 565,
        summary = "The later laws of the Byzantine emperors, the Novels of the Greek text, number 168, and two programs split them by emperor and print the same result.",
        details = "The Greek text has 168 numbered laws, 159 of them from Justinian's reign and the rest from Justin II and Tiberius. This record is placed: 565, the year Justin II came to the throne, is the anchor, not the date of any law.",
        rare = true,
        instructions = """
            1. Save the example as novels.py, novels.c and run.sh in one folder.
            2. Run it: sh run.sh
        """,
        example = """
            ### novels.py
            GREEK_TOTAL = 168   # numbered laws of the Novels in the Greek text
            JUSTINIAN = 159     # laws from the reign of Justinian
            print("laws of Justinian:", JUSTINIAN)
            print("laws of Justin II and Tiberius:", GREEK_TOTAL - JUSTINIAN)
            print("total:", GREEK_TOTAL)
            ### novels.c
            #include <stdio.h>

            int main(void) {
                int total = 168, justinian = 159;
                printf("laws of Justinian: %d\n", justinian);
                printf("laws of Justin II and Tiberius: %d\n", total - justinian);
                printf("total: %d\n", total);
                return 0;
            }
            ### run.sh
            python3 novels.py > python.txt
            cc -o novels_c novels.c
            ./novels_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
