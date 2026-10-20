package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 362 and 363: the day of the year for Julian's school edict and for his death, each in Python and C. */
val db168Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 168,
        kind = Kind.CROSS,
        section = "Dates",
        title = "The day of the year for Julian's school edict and his death, in Python and C",
        originalTitle = "Day of the year",
        author = "No single author",
        year = 363,
        summary = "The day of the year for the school edict of 17 June 362 and for the death of Julian on 26 June 363 is worked out in Python and again in C, and a script checks that the two outputs match.",
        details = "Both years are common years, so the example uses a fixed table with 28 days in February and no leap rule. The Python and C programs print the same two lines, and the run script compares them line by line.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### day_count.py
            # Day of the year for two dates of 362 and 363; both are common years, so February has 28 days.
            LENGTHS = [31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]


            def day_of_year(month, day):
                return sum(LENGTHS[:month - 1]) + day


            print("school edict of 17 June 362: day", day_of_year(6, 17))
            print("death of Julian on 26 June 363: day", day_of_year(6, 26))
            ### day_count.c
            #include <stdio.h>

            static const int lengths[12] = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};

            static int day_of_year(int month, int day) {
                int total = day;
                for (int m = 0; m < month - 1; m++) {
                    total += lengths[m];
                }
                return total;
            }

            int main(void) {
                printf("school edict of 17 June 362: day %d\n", day_of_year(6, 17));
                printf("death of Julian on 26 June 363: day %d\n", day_of_year(6, 26));
                return 0;
            }
            ### run.sh
            python3 day_count.py > python.txt
            cc -o day_count_c day_count.c
            ./day_count_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
