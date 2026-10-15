package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1720 to 1730: a plough's furrow and the Easter date of 1722, each checked in Python and C. */
val db32Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 32,
        kind = Kind.CROSS,
        section = "Ploughing",
        title = "Rotherham plough furrow checked in Python and C",
        originalTitle = "Rotherham swing plough",
        author = "Joseph Foljambe",
        year = 1730,
        summary = "The length and soil volume of one acre of furrow are computed in Python and in C, and the run script checks that the two programs print the same numbers.",
        details = "The Rotherham plough of 1730 was an iron-clad swing plough. The example takes an illustrative furrow of eight inches by six inches, finds the furrow length and the soil turned for an acre, and gives the time at three feet a second, and the two programs must print the same figures.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### rotherham.py
            from fractions import Fraction as F

            ACRE = 43560            # square feet in an acre
            width = F(2, 3)         # furrow width in feet, eight inches (illustrative)
            depth = F(1, 2)         # furrow depth in feet, six inches (illustrative)
            length = ACRE / width   # furrow length that covers one acre
            print("furrow length per acre (ft):", length)
            print("soil turned per acre (cubic ft):", length * width * depth)
            print("ploughing time at 3 ft per second (min):", length / 3 / 60)
            ### rotherham.c
            #include <stdio.h>

            int main(void) {
                /* Furrow of eight inches (2/3 foot) by six inches (1/2 foot); one acre is 43560 square feet. */
                long acre = 43560;
                long length = acre * 3 / 2;
                long soil = length * 2 * 1 / (3 * 2);
                long minutes = length / 3 / 60;
                printf("furrow length per acre (ft): %ld\n", length);
                printf("soil turned per acre (cubic ft): %ld\n", soil);
                printf("ploughing time at 3 ft per second (min): %ld\n", minutes);
                return 0;
            }
            ### run.sh
            python3 rotherham.py > python.txt
            gcc -o rotherham_c rotherham.c && ./rotherham_c > c.txt
            diff python.txt c.txt && cat python.txt && echo "same result"
        """,
    ),

    entry(
        databaseId = 32,
        kind = Kind.CROSS,
        section = "Calendar",
        title = "Roggeveen's Easter landing checked in Python and C",
        originalTitle = "Landing on Easter Island",
        author = "Jacob Roggeveen",
        year = 1722,
        summary = "The date of Easter Sunday in 1722 is computed in Python and in C with the Gregorian rule, and the run script checks that both give April 5, the day of Roggeveen's landing on Easter Island.",
        details = "Roggeveen reached Easter Island on Easter Sunday in 1722, and the island took its name from that day. The example uses the anonymous Gregorian method, which gives April 5 for 1722, and the C program repeats the same integer steps to print the same month and day.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### easter.py
            def easter(year):
                # Anonymous Gregorian method: the month and day of Easter Sunday in the Gregorian calendar.
                a = year % 19
                b, c = divmod(year, 100)
                d, e = divmod(b, 4)
                f = (b + 8) // 25
                g = (b - f + 1) // 3
                h = (19 * a + b - d - g + 15) % 30
                i, k = divmod(c, 4)
                l = (32 + 2 * e + 2 * i - h - k) % 7
                m = (a + 11 * h + 22 * l) // 451
                month = (h + l - 7 * m + 114) // 31
                day = (h + l - 7 * m + 114) % 31 + 1
                return month, day

            month, day = easter(1722)
            print(f"Easter Sunday 1722 (Gregorian): month {month}, day {day}")
            ### easter.c
            #include <stdio.h>

            int main(void) {
                int year = 1722;
                int a = year % 19;
                int b = year / 100;
                int c = year % 100;
                int d = b / 4;
                int e = b % 4;
                int f = (b + 8) / 25;
                int g = (b - f + 1) / 3;
                int h = (19 * a + b - d - g + 15) % 30;
                int i = c / 4;
                int k = c % 4;
                int l = (32 + 2 * e + 2 * i - h - k) % 7;
                int m = (a + 11 * h + 22 * l) / 451;
                int month = (h + l - 7 * m + 114) / 31;
                int day = (h + l - 7 * m + 114) % 31 + 1;
                printf("Easter Sunday 1722 (Gregorian): month %d, day %d\n", month, day);
                return 0;
            }
            ### run.sh
            python3 easter.py > python.txt
            gcc -o easter_c easter.c && ./easter_c > c.txt
            diff python.txt c.txt && cat python.txt && echo "same result"
        """,
    ),

)
