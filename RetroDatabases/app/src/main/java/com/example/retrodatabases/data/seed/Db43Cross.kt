package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1610 to 1620: a Dutch surveying rod converted to metres, and the journey time along the New River, each in Python and C. */
val db43Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 43,
        kind = Kind.CROSS,
        section = "Surveying",
        title = "Converting Rhineland rods to metres in Python and C",
        originalTitle = "Rijnlandse roede",
        author = "Dutch surveyors of the period",
        year = 1615,
        summary = "A Rhineland rod, the length that Dutch surveyors used in the measurements of 1615, is converted to metres once in Python and once in C, and a script checks that both print the same lengths.",
        details = "The Rhineland rod was about 3.766 metres, and the Dutch surveys of the period were given in rods, so the conversion is a single multiplication. The three lengths in the example are invented, and the run script compares the printed lines of the two languages.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### rods.py
            ROD_METRES = 3.766

            for rods in (10, 25, 100):
                print(f"{rods} rods: {rods * ROD_METRES:.2f} m")
            ### rods.c
            #include <stdio.h>

            int main(void) {
                const double rod = 3.766;
                int rods[3] = {10, 25, 100};
                for (int i = 0; i < 3; i++) {
                    printf("%d rods: %.2f m\n", rods[i], rods[i] * rod);
                }
                return 0;
            }
            ### run.sh
            python3 rods.py > python.txt
            cc -o rods_c rods.c
            ./rods_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

    entry(
        databaseId = 43,
        kind = Kind.CROSS,
        section = "Water supply",
        title = "The New River's journey time at a fixed speed in Python and C",
        originalTitle = "New River",
        author = "Hugh Myddelton",
        year = 1613,
        summary = "The journey time of water along the New River at a fixed speed is computed once in Python and once in C, and a script checks that both print the same times.",
        details = "The New River brought water to London from the north, and it was opened in 1613 under Hugh Myddelton; its length is given as about 38 miles in the sources used here. The speed is illustrative, not a measured flow, and the run script compares the printed lines of the two languages.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### river.py
            MILE_KM = 1.609344
            LENGTH_MILES = 38
            SPEED_KMH = 5.0

            distance = LENGTH_MILES * MILE_KM
            hours = distance / SPEED_KMH
            print(f"length: {distance:.2f} km")
            print(f"time at {SPEED_KMH:.0f} km/h: {hours:.2f} hours")
            ### river.c
            #include <stdio.h>

            int main(void) {
                const double mile_km = 1.609344;
                const double length_miles = 38.0;
                const double speed_kmh = 5.0;
                double distance = length_miles * mile_km;
                double hours = distance / speed_kmh;
                printf("length: %.2f km\n", distance);
                printf("time at %.0f km/h: %.2f hours\n", speed_kmh, hours);
                return 0;
            }
            ### run.sh
            python3 river.py > python.txt
            cc -o river_c river.c
            ./river_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
