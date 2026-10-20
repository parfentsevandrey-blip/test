package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One cross-code experiment of 258: the weekday of Lawrence's martyrdom, worked out from a Julian day number in Python and C. */
val db179Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 179,
        kind = Kind.CROSS,
        section = "Weekdays",
        title = "The weekday of Lawrence's martyrdom on 10 August 258, in Python and C",
        originalTitle = "Julian day number",
        author = "No single author",
        year = 258,
        summary = "The Julian calendar date 10 August 258, the usual date of Lawrence's death at Rome, is turned into a Julian day number and a weekday, and a script checks that the Python and C programs agree.",
        details = "Lawrence's death is dated to 10 August 258 in the usual accounts, and the Julian day formula gives the number 1815514, which falls on a Tuesday. The run script compares the two outputs line by line, and the weekday is a computed fact about the calendar rather than a record of what anyone called that day.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### lawrence_weekday.py
            def julian_day_number(year, month, day):
                a = (14 - month) // 12
                y = year + 4800 - a
                m = month + 12 * a - 3
                return day + (153 * m + 2) // 5 + 365 * y + y // 4 - 32083

            NAMES = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]
            jdn = julian_day_number(258, 8, 10)
            print("Julian day number:", jdn)
            print("weekday:", NAMES[jdn % 7])
            ### lawrence_weekday.c
            #include <stdio.h>

            int main(void) {
                int year = 258, month = 8, day = 10;
                int a = (14 - month) / 12;
                int y = year + 4800 - a;
                int m = month + 12 * a - 3;
                long jdn = day + (153 * m + 2) / 5 + 365L * y + y / 4 - 32083;
                const char *names[] = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
                printf("Julian day number: %ld\n", jdn);
                printf("weekday: %s\n", names[jdn % 7]);
                return 0;
            }
            ### run.sh
            python3 lawrence_weekday.py > python.txt
            cc -o lawrence_weekday_c lawrence_weekday.c
            ./lawrence_weekday_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
        rare = true,
    ),

)
