package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1352 and 1355: the bows of the Three Kings in one day of the Strasbourg clock, and the years Ibn Battuta spent on the road, each in Python and C. */
val db69Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 69,
        kind = Kind.CROSS,
        section = "Time",
        title = "The Three Kings' bows in one day of a clock, in Python and C",
        originalTitle = "The Three Kings' bows in one day of a clock, in Python and C",
        author = "Clockmakers of Strasbourg Cathedral",
        year = 1352,
        summary = "The Three Kings of an early Strasbourg clock bow once an hour, and a script checks that the Python and C programs count the same bows in a day.",
        details = "The count is illustrative: three figures bow at each of the twenty-four hours, so the programs print seventy-two bows for a day. The run script compares the two outputs line by line.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### kings.py
            KINGS = 3        # the three figures of the procession
            HOURS = 24       # one showing at each hour, as an illustration
            bows = KINGS * HOURS
            print("kings", KINGS)
            print("hours in a day", HOURS)
            print("bows in a day", bows)
            ### kings.c
            #include <stdio.h>

            int main(void) {
                int kings = 3;   /* the three figures of the procession */
                int hours = 24;  /* one showing at each hour, as an illustration */
                int bows = kings * hours;
                printf("kings %d\n", kings);
                printf("hours in a day %d\n", hours);
                printf("bows in a day %d\n", bows);
                return 0;
            }
            ### run.sh
            python3 kings.py > python.txt
            cc -o kings_c kings.c
            ./kings_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

    entry(
        databaseId = 69,
        kind = Kind.CROSS,
        section = "Travel",
        title = "The years Ibn Battuta spent on the road, in Python and C",
        originalTitle = "The years Ibn Battuta spent on the road, in Python and C",
        author = "Ibn Battuta",
        year = 1355,
        summary = "Ibn Battuta set out in 1325 and was home by 1349, and a script checks that the Python and C programs count the same years before his account was dictated.",
        details = "The years are taken as commonly given, and the programs print the years on the road, the years from his return to the dictation of his account in 1355, and the years from his departure to the dictation. The run script compares the two outputs line by line.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### travel.py
            DEPARTURE = 1325
            HOME = 1349
            DICTATION = 1355
            print("years on the road", HOME - DEPARTURE)
            print("years from return to dictation", DICTATION - HOME)
            print("years from departure to dictation", DICTATION - DEPARTURE)
            ### travel.c
            #include <stdio.h>

            int main(void) {
                int departure = 1325;
                int home = 1349;
                int dictation = 1355;
                printf("years on the road %d\n", home - departure);
                printf("years from return to dictation %d\n", dictation - home);
                printf("years from departure to dictation %d\n", dictation - departure);
                return 0;
            }
            ### run.sh
            python3 travel.py > python.txt
            cc -o travel_c travel.c
            ./travel_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
