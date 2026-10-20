package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One cross-code piece of 600 to 610: the 609 census totals written in groups of ten thousand, in Python and in C. */
val db144Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 144,
        kind = Kind.CROSS,
        section = "Chinese",
        title = "The 609 census written in groups of ten thousand, computed in Python and in C",
        originalTitle = "wan (ten thousand)",
        author = "Sui court under Emperor Yang",
        year = 609,
        summary = "The census totals of 609 are split into groups of ten thousand, the Chinese unit wan, by a Python program and by a C program that print the same result.",
        details = "Chinese numbers group by ten thousand rather than by a thousand, so 46,019,956 people is written as 4601 wan 9956, and 8,907,546 households as 890 wan 7546. The two programs print the same two lines, and a script checks that the outputs agree.",
        rare = true,
        instructions = """
            1. Save the example as wan_census.py, wan_census.c and run.sh in one folder.
            2. Run it: sh run.sh
        """,
        example = """
            ### wan_census.py
            PEOPLE = 46019956       # the people counted in 609, as the Sui history gives them
            HOUSEHOLDS = 8907546    # the households counted in 609
            print("people:", PEOPLE // 10000, "wan", PEOPLE % 10000)
            print("households:", HOUSEHOLDS // 10000, "wan", HOUSEHOLDS % 10000)
            ### wan_census.c
            #include <stdio.h>

            int main(void) {
                long people = 46019956, households = 8907546;
                printf("people: %ld wan %04ld\n", people / 10000, people % 10000);
                printf("households: %ld wan %04ld\n", households / 10000, households % 10000);
                return 0;
            }
            ### run.sh
            python3 wan_census.py > python.txt
            cc -o wan_census_c wan_census.c
            ./wan_census_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
