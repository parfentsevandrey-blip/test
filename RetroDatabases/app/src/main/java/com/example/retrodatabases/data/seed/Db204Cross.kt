package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiment of 1 to 9: the people per household of the Han census of 2, computed in Python and in C. */
val db204Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 204,
        kind = Kind.CROSS,
        section = "Population",
        title = "The average household size of the Han census of 2 in Python and C",
        originalTitle = "Persons per household",
        author = "Census clerks of the Han and later readers",
        year = 2,
        summary = "Dividing the stated people of the Han census of 2 by its households gives 4.82 people per household, and a script checks that the Python and C programs print the same figures.",
        details = "The totals are the stated figures in the Hanshu, and the example uses the stated people, so the average inherits the dispute over that total; a lower total of about 57.67 million would give an average of about 4.66. The run script compares the two outputs line by line.",
        instructions = """
            1. Save the example as census_average.py and census_average.c, with the runner as run.sh.
            2. Run it: sh run.sh
        """,
        example = """
            ### census_average.py
            PEOPLE = 59594978
            HOUSEHOLDS = 12366470
            average = PEOPLE / HOUSEHOLDS
            print(f"persons per household: {average:.2f}")
            print("whole persons per household:", PEOPLE // HOUSEHOLDS)
            ### census_average.c
            #include <stdio.h>

            int main(void) {
                const long people = 59594978L;
                const long households = 12366470L;
                double average = (double)people / (double)households;
                printf("persons per household: %.2f\n", average);
                printf("whole persons per household: %ld\n", people / households);
                return 0;
            }
            ### run.sh
            python3 census_average.py > python.txt
            cc -o census_average_c census_average.c
            ./census_average_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
        rare = true,
    ),

)
