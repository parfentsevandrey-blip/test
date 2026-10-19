package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code piece of 790 to 800: three dates before Charlemagne's imperial coronation, the years between them computed in Python and in C. */
val db125Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 125,
        kind = Kind.CROSS,
        section = "Frankish",
        title = "Three dates before Charlemagne's imperial coronation, 796, 799 and 800, with the years between them in Python and C",
        originalTitle = "Three dates before the coronation",
        author = "Charlemagne",
        year = 796,
        summary = "The Avar treasure of 796, the flight of Pope Leo III to Paderborn in 799 and the coronation of 800 are subtracted in Python and in C, and a script checks that both print the same spans.",
        details = "The spans printed are 3, 1 and 4 years, from the treasure to the flight, from the flight to the coronation, and from the treasure to the coronation. The dates are the usual ones, and the figures show only the arithmetic of those dates; they do not claim that the events were planned in this order.",
        instructions = """
            1. Save the example as charlemagne.py, charlemagne.c and run.sh in one folder.
            2. Run it: sh run.sh
        """,
        example = """
            ### charlemagne.py
            DATES = [("Avar treasure at Aachen", 796), ("flight of Leo III", 799), ("coronation at Rome", 800)]
            treasure, flight, crown = [year for _, year in DATES]
            print("years from the Avar treasure to the flight of Leo III:", flight - treasure)
            print("years from the flight of Leo III to the coronation:", crown - flight)
            print("years from the Avar treasure to the coronation:", crown - treasure)
            ### charlemagne.c
            #include <stdio.h>

            int main(void) {
                int treasure = 796, flight = 799, crown = 800;
                printf("years from the Avar treasure to the flight of Leo III: %d\n", flight - treasure);
                printf("years from the flight of Leo III to the coronation: %d\n", crown - flight);
                printf("years from the Avar treasure to the coronation: %d\n", crown - treasure);
                return 0;
            }
            ### run.sh
            python3 charlemagne.py > python.txt
            cc -o charlemagne_c charlemagne.c
            ./charlemagne_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
