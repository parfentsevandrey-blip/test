package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiment of 1291: four dates from 1291 to 1300 turned into Armenian era years, in a Python program and a C program. */
val db75Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 75,
        kind = Kind.CROSS,
        section = "Calendar",
        title = "The Armenian year of four dates from 1291 to 1300 in Python and C",
        originalTitle = "Armenian era",
        author = "Armenian chroniclers",
        year = 1291,
        summary = "The Armenian era counts from 552 CE, so a Python program and a C program turn four dates of 1291 to 1300 into Armenian years and must print the same answers.",
        details = "The Armenian year changes on 11 July, so a date before that day takes one year less than a date after it. The run script compares the two outputs line by line and passes only when they match.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### armenian.py
            dates = [("18 May 1291", 1291, 5, 18), ("1 August 1291", 1291, 8, 1),
                     ("18 January 1293", 1293, 1, 18), ("22 February 1300", 1300, 2, 22)]
            for label, year, month, day in dates:
                after_new_year = (month, day) >= (7, 11)
                era = year - 551 if after_new_year else year - 552
                print(label, "is Armenian year", era)
            ### armenian.c
            #include <stdio.h>

            int main(void) {
                const char *labels[] = {"18 May 1291", "1 August 1291", "18 January 1293", "22 February 1300"};
                int years[] = {1291, 1291, 1293, 1300};
                int months[] = {5, 8, 1, 2};
                int days[] = {18, 1, 18, 22};
                for (int i = 0; i < 4; i++) {
                    int after = (months[i] > 7) || (months[i] == 7 && days[i] >= 11);
                    int era = after ? years[i] - 551 : years[i] - 552;
                    printf("%s is Armenian year %d\n", labels[i], era);
                }
                return 0;
            }
            ### run.sh
            python3 armenian.py > python.txt
            cc -o armenian_c armenian.c
            ./armenian_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
