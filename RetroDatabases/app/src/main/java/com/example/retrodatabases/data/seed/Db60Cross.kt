package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1440 to 1450: the dates of the Aachen relic pilgrimage and a cannonball pyramid's totals, each in Python and C. */
val db60Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 60,
        kind = Kind.CROSS,
        section = "Pilgrimage",
        title = "Aachen's relic pilgrimage dates in Python and C",
        originalTitle = "Aachener Heiligtumsfahrt",
        author = "Aachen Cathedral",
        year = 1447,
        summary = "The Aachen relic pilgrimage came back every seventh year from 1349, and two programs, one in Python and one in C, print its dates from 1440 to 1460 and check that they agree.",
        details = "The seven-year rhythm of the pilgrimage was set in 1349, and the four great relics were shown on it. The run script compares the two programs' output; 1440 and 1447 fall inside this decade, and 1454 is the next cycle year.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### pilgrimage.py
            START = 1349   # the seven-year cycle of the pilgrimage was set in 1349
            STEP = 7

            for year in range(START, 1461, STEP):
                if year >= 1440:
                    print(year)
            ### pilgrimage.c
            #include <stdio.h>

            int main(void) {
                for (int year = 1349; year <= 1460; year += 7) {
                    if (year >= 1440) {
                        printf("%d\n", year);
                    }
                }
                return 0;
            }
            ### run.sh
            python3 pilgrimage.py > python.txt
            cc -o pilgrimage_c pilgrimage.c
            ./pilgrimage_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

    entry(
        databaseId = 60,
        kind = Kind.CROSS,
        section = "Ordnance",
        title = "Stacked cannonballs of a square pyramid in Python and C",
        originalTitle = "Mons Meg",
        author = "Makers of Mons for Philip the Good",
        year = 1449,
        summary = "A square pyramid of cannonballs has a layer of squares at each level, and two programs add those squares in Python and C and check that the totals agree.",
        details = "Mons Meg of 1449 was a great gun, and the example uses stacked shot only as an illustration of square numbers. It does not record the gun's own ammunition, and the run script compares the two programs' output.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### cannon.py
            for side in range(1, 6):
                total = sum(k * k for k in range(1, side + 1))
                print(f"side {side}: total {total}")
            ### cannon.c
            #include <stdio.h>

            int main(void) {
                for (int side = 1; side <= 5; side++) {
                    int total = 0;
                    for (int k = 1; k <= side; k++) {
                        total += k * k;
                    }
                    printf("side %d: total %d\n", side, total);
                }
                return 0;
            }
            ### run.sh
            python3 cannon.py > python.txt
            cc -o cannon_c cannon.c
            ./cannon_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
