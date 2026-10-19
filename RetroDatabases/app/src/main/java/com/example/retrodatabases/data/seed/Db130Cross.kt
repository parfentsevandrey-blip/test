package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code piece of 740 to 750: the years between the death of Leo III and two decrees of 743, computed in Python and in C. */
val db130Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 130,
        kind = Kind.CROSS,
        section = "Decrees",
        title = "The years between the death of Leo III and two decrees of 743, in Python and C",
        originalTitle = "Leo III to the decrees of 743",
        author = "Leo III and Shomu",
        year = 741,
        summary = "The years between the death of Leo III and two decrees of 743 are printed by a Python program and by a C program, and a script checks that both print the same figures.",
        details = "Leo III died in June 741, and the two decrees counted here, the Konden Einen Shizaiho and Shomu's decree for the Great Buddha, were both issued in 743. The spans from the death are 2 years to each decree, the two decrees fall in the same year, and the arithmetic uses only the usual dates.",
        instructions = """
            1. Save the example as decrees.py, decrees.c and run.sh in one folder.
            2. Run it: sh run.sh
        """,
        example = """
            ### decrees.py
            DEATH_LEO = 741
            KONDEN = 743
            BUDDHA = 743
            print("years from the death of Leo III to the Konden law:", KONDEN - DEATH_LEO)
            print("years from the death of Leo III to the Great Buddha decree:", BUDDHA - DEATH_LEO)
            print("years from the Konden law to the Great Buddha decree:", BUDDHA - KONDEN)
            ### decrees.c
            #include <stdio.h>

            int main(void) {
                int death = 741, konden = 743, buddha = 743;
                printf("years from the death of Leo III to the Konden law: %d\n", konden - death);
                printf("years from the death of Leo III to the Great Buddha decree: %d\n", buddha - death);
                printf("years from the Konden law to the Great Buddha decree: %d\n", buddha - konden);
                return 0;
            }
            ### run.sh
            python3 decrees.py > python.txt
            cc -o decrees_c decrees.c
            ./decrees_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
