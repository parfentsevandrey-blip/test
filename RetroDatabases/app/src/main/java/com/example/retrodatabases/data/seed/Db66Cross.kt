package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1380 to 1390: the hour strikes of a tower clock and the words and letters of a verse line, each in Python and C. */
val db66Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 66,
        kind = Kind.CROSS,
        section = "Time",
        title = "Hour strikes of a tower clock in a day, in Python and C",
        originalTitle = "Hour strike",
        author = "Makers of medieval tower clocks",
        year = 1386,
        summary = "A tower clock strikes each hour as many times as the hour, and a script checks that the Python and C programs count the same strikes in a day.",
        details = "The count is illustrative: the hour bell strikes one stroke for each hour, so the twelve hours of a half-day give 78 strikes. The run script compares the two outputs line by line.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### clock.py
            total = sum(range(1, 13))
            print("strikes in 12 hours:", total)
            print("strikes in 24 hours:", 2 * total)
            print("strikes at the twelfth hour:", 12)
            ### clock.c
            #include <stdio.h>

            int main(void) {
                int total = 0;
                for (int hour = 1; hour <= 12; hour++) {
                    total += hour;   /* the hour bell strikes once for each hour */
                }
                printf("strikes in 12 hours: %d\n", total);
                printf("strikes in 24 hours: %d\n", 2 * total);
                printf("strikes at the twelfth hour: %d\n", 12);
                return 0;
            }
            ### run.sh
            python3 clock.py > python.txt
            cc -o clock_c clock.c
            ./clock_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

    entry(
        databaseId = 66,
        kind = Kind.CROSS,
        section = "Verse",
        title = "Letters and words of an opening verse line in Python and C",
        originalTitle = "Verse line",
        author = "Makers of Middle English verse",
        year = 1385,
        summary = "The opening line of a Middle English poem is counted for its words and letters, and a script checks that the Python and C programs count the same letters and words.",
        details = "The line is the opening of Langland's Piers Plowman, whose C text is dated to about 1385, and the example counts its words, its letters and its letter s. The run script compares the two outputs line by line.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### letters.py
            LINE = "In a somer seson, whan softe was the sonne"
            print("words:", len(LINE.split()))
            print("letters:", sum(ch.isalpha() for ch in LINE))
            print("letter s:", LINE.lower().count("s"))
            ### letters.c
            #include <ctype.h>
            #include <stdio.h>

            int main(void) {
                const char *line = "In a somer seson, whan softe was the sonne";
                int letters = 0, s_count = 0, spaces = 0;
                for (const char *p = line; *p; p++) {
                    if (isalpha((unsigned char)*p)) letters++;
                    if (tolower((unsigned char)*p) == 's') s_count++;
                    if (*p == ' ') spaces++;
                }
                printf("words: %d\n", spaces + 1);
                printf("letters: %d\n", letters);
                printf("letter s: %d\n", s_count);
                return 0;
            }
            ### run.sh
            python3 letters.py > python.txt
            cc -o letters_c letters.c
            ./letters_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
