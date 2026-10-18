package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiment of 964: the stars of al-Sufi's catalogue shared among its constellations, in Python and C. */
val db108Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 108,
        kind = Kind.CROSS,
        section = "Astronomy",
        title = "The stars of al-Sufi's catalogue shared among its 48 constellations, in Python and C",
        originalTitle = "The stars of al-Sufi's catalogue shared among its 48 constellations, in Python and C",
        author = "Abd al-Rahman al-Sufi",
        year = 964,
        summary = "Python and C programs divide the 1,017 stars of al-Sufi's catalogue among its 48 constellations, and both print 21 stars per constellation with 9 left over, which the run script compares.",
        details = "The catalogue gives coordinates for 1,017 stars, and that figure is the one used here. The two programs use only integer division and a remainder, and they print the same four lines, which the run script compares. The division gives a rounded average and not the number of stars in each constellation, since the groups differ in size.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### stars.py
            stars = 1017
            constellations = 48
            print("stars:", stars)
            print("constellations:", constellations)
            print("stars per constellation, rounded down:", stars // constellations)
            print("left over:", stars % constellations)
            ### stars.c
            #include <stdio.h>

            int main(void) {
                int stars = 1017;
                int constellations = 48;
                printf("stars: %d\n", stars);
                printf("constellations: %d\n", constellations);
                printf("stars per constellation, rounded down: %d\n", stars / constellations);
                printf("left over: %d\n", stars % constellations);
                return 0;
            }
            ### run.sh
            python3 stars.py > python.txt
            cc -o stars_c stars.c
            ./stars_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
