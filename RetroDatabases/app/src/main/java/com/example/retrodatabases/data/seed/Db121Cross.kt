package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiment of 833: the Kaicheng stone tablets counted in Python and in C. */
val db121Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 121,
        kind = Kind.CROSS,
        section = "China",
        title = "The Kaicheng stone tablets, their faces and the years of carving, in Python and C",
        originalTitle = "The Kaicheng stone tablets, their faces and the years of carving, in Python and C",
        author = "Tang court scholars at Chang'an",
        year = 833,
        summary = "Python and C programs count the 114 Kaicheng tablets, their 228 faces, the twelve classics and the four years from 833 to 837, and a script checks that both give the same result.",
        details = "Both programs use plain integer arithmetic on the same counts, so the run tests the two languages on the same numbers rather than on the stones. The counts are the usual figures for the set: 114 tablets carved on both faces, twelve works, and carving from 833 to 837.",
        instructions = """
            1. Save the three sections below as stones.py, stones.c and run.sh in one folder.
            2. Run it: sh run.sh
        """,
        example = """
            ### stones.py
            tablets = 114
            faces = 2 * tablets
            classics = 12
            carving = 837 - 833
            print("tablets:", tablets)
            print("faces:", faces)
            print("classics:", classics)
            print("carving years:", carving)
            ### stones.c
            #include <stdio.h>

            int main(void) {
                int tablets = 114;
                int faces = 2 * tablets;
                int classics = 12;
                int carving = 837 - 833;
                printf("tablets: %d\n", tablets);
                printf("faces: %d\n", faces);
                printf("classics: %d\n", classics);
                printf("carving years: %d\n", carving);
                return 0;
            }
            ### run.sh
            python3 stones.py > python.txt
            cc -o stones_c stones.c
            ./stones_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
