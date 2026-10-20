package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One cross-code piece of 650 to 660: the years between four dated items of the 650s, counted in Python and in C. */
val db139Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 139,
        kind = Kind.CROSS,
        section = "Mixed",
        title = "The years between the Yongwei code of 651, the Naniwa palace of 652, the Liber Iudiciorum of 654 and the Ten Canons of 656, counted in Python and C",
        originalTitle = "Yongwei lu, Liber Iudiciorum",
        author = "Several authors",
        year = 651,
        summary = "The years between four dated items of the 650s are counted by a Python program and by a C program, and a script checks that both print the same figures.",
        details = "The dates are the commonly given years of the four items, so the gaps of one, two and two years, and five from the first to the last, are arithmetic and not a claim about cause. The two programs print the same lines, and the script confirms the match.",
        instructions = """
            1. Save the example as cross_gaps.py, cross_gaps.c and run.sh in one folder.
            2. Run it: sh run.sh
        """,
        example = """
            ### cross_gaps.py
            DATES = [
                ("Yongwei code", 651),
                ("Naniwa palace", 652),
                ("Liber Iudiciorum", 654),
                ("Ten Canons", 656),
            ]
            for (name_a, year_a), (name_b, year_b) in zip(DATES, DATES[1:]):
                print(f"{name_a} to {name_b}: {year_b - year_a} years")
            print(f"first to last: {DATES[-1][1] - DATES[0][1]} years")
            ### cross_gaps.c
            #include <stdio.h>

            int main(void) {
                int yongwei = 651, naniwa = 652, liber = 654, canons = 656;
                printf("Yongwei code to Naniwa palace: %d years\n", naniwa - yongwei);
                printf("Naniwa palace to Liber Iudiciorum: %d years\n", liber - naniwa);
                printf("Liber Iudiciorum to Ten Canons: %d years\n", canons - liber);
                printf("first to last: %d years\n", canons - yongwei);
                return 0;
            }
            ### run.sh
            python3 cross_gaps.py > python.txt
            cc -o cross_gaps_c cross_gaps.c
            ./cross_gaps_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
