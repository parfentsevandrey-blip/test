package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 110 to 120: the days from the death of Trajan to the accession of Hadrian, in Python and C. */
val db193Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 193,
        kind = Kind.CROSS,
        section = "Dates",
        title = "Days from the death of Trajan to the accession of Hadrian, in Python and C",
        originalTitle = "Julian day numbers",
        author = "Roman chroniclers",
        year = 117,
        summary = "Trajan died on 8 August 117 and Hadrian was acclaimed on 11 August 117, and a Python and a C program count the three days between them with one day-number formula.",
        details = "Both dates are given as commonly stated, and both programs use the same integer formula for the day number in the Julian calendar, so the count of 3 is the same in each. The run script compares the two outputs line by line, and the example tests the arithmetic rather than the succession itself.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### day.py
            def day_number(year, month, day):
                # Julian day number, using the usual integer formula for the Julian calendar
                a = (14 - month) // 12
                y = year + 4800 - a
                m = month + 12 * a - 3
                return day + (153 * m + 2) // 5 + 365 * y + y // 4 - 32083

            trajan = day_number(117, 8, 8)
            hadrian = day_number(117, 8, 11)
            print("day number of the death of Trajan:", trajan)
            print("day number of the accession of Hadrian:", hadrian)
            print("days from Trajan's death to Hadrian's accession:", hadrian - trajan)
            ### day.c
            #include <stdio.h>

            static long day_number(long year, long month, long day) {
                long a = (14 - month) / 12;
                long y = year + 4800 - a;
                long m = month + 12 * a - 3;
                return day + (153 * m + 2) / 5 + 365 * y + y / 4 - 32083;
            }

            int main(void) {
                long trajan = day_number(117, 8, 8);
                long hadrian = day_number(117, 8, 11);
                printf("day number of the death of Trajan: %ld\n", trajan);
                printf("day number of the accession of Hadrian: %ld\n", hadrian);
                printf("days from Trajan's death to Hadrian's accession: %ld\n", hadrian - trajan);
                return 0;
            }
            ### run.sh
            python3 day.py > python.txt
            cc -o day_c day.c
            ./day_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
