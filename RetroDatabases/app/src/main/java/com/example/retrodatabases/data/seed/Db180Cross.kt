package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiment of 249: Wang Bi's years of life counted in plain and sui counts, in Python and in C. */
val db180Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 180,
        kind = Kind.CROSS,
        section = "Lives",
        title = "Wang Bi's years of life in plain and sui counts, in Python and C",
        originalTitle = "Sui reckoning",
        author = "Chinese reckoners of age",
        year = 249,
        summary = "Wang Bi's life from 226 to 249 is counted in plain years and in sui years, and a script checks that the Python and C programs print the same two counts.",
        details = "The sui count gives a person's age as the plain difference plus one, the birth year counted as year one, which is the traditional East Asian way of counting. The two dates are the commonly given ones for Wang Bi, and the run script compares the two outputs line by line.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### sui.py
            BORN = 226   # Wang Bi's birth year, as commonly given
            DIED = 249   # Wang Bi's death year, as commonly given
            print("plain years from birth to death:", DIED - BORN)
            print("sui count, with the birth year as year one:", DIED - BORN + 1)
            ### sui.c
            #include <stdio.h>

            int main(void) {
                const int born = 226;   /* Wang Bi's birth year, as commonly given */
                const int died = 249;   /* Wang Bi's death year, as commonly given */
                printf("plain years from birth to death: %d\n", died - born);
                printf("sui count, with the birth year as year one: %d\n", died - born + 1);
                return 0;
            }
            ### run.sh
            python3 sui.py > python.txt
            cc -o sui_c sui.c
            ./sui_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
        rare = true,
    ),

)
