package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 180 to 190: the steles of the Xiping stone classics a year, from request to completion, in Python and in C. */
val db186Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 186,
        kind = Kind.CROSS,
        section = "Stones",
        title = "Steles a year for the Xiping stone classics, from request to completion, in Python and C",
        originalTitle = "Xiping steles a year",
        author = "Cai Yong and his colleagues",
        year = 183,
        summary = "From the request of 175 to the completion of 183, the 46 or 48 Xiping steles come to 5.75 or 6.00 a year, and a script checks that the Python and C programs agree.",
        details = "Accounts give either 46 or 48 steles, so the example prints the rate for both counts to two decimal places. The run script compares the two outputs line by line, and the rate is a plain division over the eight years of the project, not a claim about how the carving was paced.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### xiping_rate.py
            steles_low = 46
            steles_high = 48
            years = 183 - 175
            print("years from the request to the completion:", years)
            print("46 steles a year:", "%.2f" % (steles_low / years))
            print("48 steles a year:", "%.2f" % (steles_high / years))
            ### xiping_rate.c
            #include <stdio.h>

            int main(void) {
                int years = 183 - 175;
                printf("years from the request to the completion: %d\n", years);
                printf("46 steles a year: %.2f\n", 46.0 / years);
                printf("48 steles a year: %.2f\n", 48.0 / years);
                return 0;
            }
            ### run.sh
            python3 xiping_rate.py > python.txt
            cc -o xiping_rate_c xiping_rate.c
            ./xiping_rate_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
        rare = true,
    ),

)
