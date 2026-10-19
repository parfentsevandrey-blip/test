package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiment of 883: the gaps between three Byzantine law books of the 880s, counted in Python and in C. */
val db116Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 116,
        kind = Kind.CROSS,
        section = "Byzantium",
        title = "The gaps between three Byzantine law books of the 880s, in Python and C",
        originalTitle = "The gaps between three Byzantine law books of the 880s, in Python and C",
        author = "Photius, Basil I and Leo VI",
        year = 883,
        summary = "Python and C programs count the years from the Nomocanon of 883 to the Epanagoge of 886 and then to the Basilika of 888, and a script checks that both give the same result.",
        details = "Both programs use plain integer subtraction on the same dates, so the run tests the two languages on the same arithmetic rather than on the history. The dates are the years usually given for each book, and the two gaps add up to five years.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### gaps.py
            NOMOCANON = 883
            EPANAGOGE = 886
            BASILIKA = 888
            gaps = [EPANAGOGE - NOMOCANON, BASILIKA - EPANAGOGE]
            print("Nomocanon to Epanagoge:", gaps[0], "years")
            print("Epanagoge to Basilika:", gaps[1], "years")
            print("together:", sum(gaps), "years")
            ### gaps.c
            #include <stdio.h>

            int main(void) {
                int nomocanon = 883;
                int epanagoge = 886;
                int basilika = 888;
                printf("Nomocanon to Epanagoge: %d years\n", epanagoge - nomocanon);
                printf("Epanagoge to Basilika: %d years\n", basilika - epanagoge);
                printf("together: %d years\n", (epanagoge - nomocanon) + (basilika - epanagoge));
                return 0;
            }
            ### run.sh
            python3 gaps.py > python.txt
            cc -o gaps_c gaps.c
            ./gaps_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
