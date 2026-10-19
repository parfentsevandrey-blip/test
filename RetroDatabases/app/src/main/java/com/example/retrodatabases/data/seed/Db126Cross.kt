package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code piece of 780 to 790: Greek-era years turned into AD years, as the Syriac date of the Xi'an stele requires, computed in Python and in C. */
val db126Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 126,
        kind = Kind.CROSS,
        section = "Syriac",
        title = "Greek-era years turned into AD years, as the Syriac date of the Xi'an stele requires, in Python and C",
        originalTitle = "Years of the Greeks",
        author = "Syriac church chronographers",
        year = 781,
        summary = "A Greek-era year changes to an AD year in the middle of the Syriac year, and a Python program and a C program print the same four conversions.",
        details = "The Syriac year begins in October, so dates from January to September take 311 off the Greek-era year and dates from October to December take 312 off. The Xi'an stele is dated in this era as 1092, which gives 781 for January to September, and the program checks four cases.",
        instructions = """
            1. Save the example as seleucid.py, seleucid.c and run.sh in one folder.
            2. Run it: sh run.sh
        """,
        example = """
            ### seleucid.py
            def ad_year(greek_year, month):
                # the Syriac year starts in October, so October to December take the larger offset
                offset = 312 if month >= 10 else 311
                return greek_year - offset

            CASES = [(1092, 1), (1092, 11), (1098, 3), (1099, 10)]
            for greek_year, month in CASES:
                print("Syriac year", greek_year, "month", month, "is AD", ad_year(greek_year, month))
            ### seleucid.c
            #include <stdio.h>

            int ad_year(int greek_year, int month) {
                int offset = month >= 10 ? 312 : 311;
                return greek_year - offset;
            }

            int main(void) {
                int cases[4][2] = {{1092, 1}, {1092, 11}, {1098, 3}, {1099, 10}};
                for (int i = 0; i < 4; i++) {
                    printf("Syriac year %d month %d is AD %d\n", cases[i][0], cases[i][1], ad_year(cases[i][0], cases[i][1]));
                }
                return 0;
            }
            ### run.sh
            python3 seleucid.py > python.txt
            cc -o seleucid_c seleucid.c
            ./seleucid_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
