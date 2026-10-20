package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 460 to 470: the 1469 days from the killing of Majorian to the death of Libius Severus, in Python and in C. */
val db158Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 158,
        kind = Kind.CROSS,
        section = "Intervals",
        title = "Days from Majorian's killing in 461 to Severus's death in 465, in Python and C",
        originalTitle = "Days between two dates",
        author = "Majorian and Libius Severus",
        year = 465,
        summary = "Two programs, one in Python and one in C, count the 1469 days from the killing of Majorian to the death of Severus, and a script checks that they agree.",
        details = "The count runs from 7 August 461, the date one account gives for Majorian's killing, to 15 August 465, the date another account gives for Severus's death. The Python version uses the date library and the C version a Julian day number, and the run script compares the two outputs line by line.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### majorian_severus.py
            from datetime import date

            killed = date(461, 8, 7)     # Majorian's killing, as one account dates it
            dead = date(465, 8, 15)      # Severus's death, as another account dates it
            print("days from Majorian's killing to Severus's death:", (dead - killed).days)
            ### majorian_severus.c
            #include <stdio.h>

            /* Julian day number of a date in the Julian calendar */
            static long julian_day(int year, int month, int day) {
                int a = (14 - month) / 12;
                long y = year + 4800 - a;
                long m = month + 12 * a - 3;
                return day + (153 * m + 2) / 5 + 365 * y + y / 4 - 32083;
            }

            int main(void) {
                long killed = julian_day(461, 8, 7);
                long dead = julian_day(465, 8, 15);
                printf("days from Majorian's killing to Severus's death: %ld\n", dead - killed);
                return 0;
            }
            ### run.sh
            python3 majorian_severus.py > python.txt
            cc -o majorian_severus_c majorian_severus.c
            ./majorian_severus_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
