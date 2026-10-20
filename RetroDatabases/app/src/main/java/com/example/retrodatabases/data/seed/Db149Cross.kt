package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One cross-code piece of 550 to 560: the gaps between Jordanes's Getica, the battle of Taginae and the Pragmatic Sanction, in Python and in C. */
val db149Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 149,
        kind = Kind.CROSS,
        section = "Byzantine",
        title = "The years from Jordanes's Getica of 551 to the Pragmatic Sanction of 554, with Taginae between, computed in Python and in C",
        originalTitle = "Years between three dated entries",
        author = "Jordanes and the Byzantine court",
        year = 551,
        summary = "The years between Jordanes's Getica of 551, the battle of Taginae in 552 and the Pragmatic Sanction of 554 are printed by a Python program and a C program that give the same result.",
        details = "Each gap is a plain subtraction of two dates that the sources give: 1 year from the Getica to Taginae, 2 years from Taginae to the sanction, and 3 years from the Getica to the sanction. The two programs print the same three lines, and a script checks that their outputs agree.",
        instructions = """
            1. Save the example as gap_years.py, gap_years.c and run.sh in one folder.
            2. Run it: sh run.sh
        """,
        example = """
            ### gap_years.py
            GETICA = 551    # Jordanes's Getica, written in 551
            TAGINAE = 552   # the battle of Taginae, in 552
            SANCTION = 554  # the Pragmatic Sanction, in 554
            print("Getica to Taginae:", TAGINAE - GETICA)
            print("Taginae to the Pragmatic Sanction:", SANCTION - TAGINAE)
            print("Getica to the Pragmatic Sanction:", SANCTION - GETICA)
            ### gap_years.c
            #include <stdio.h>

            int main(void) {
                int getica = 551, taginae = 552, sanction = 554;
                printf("Getica to Taginae: %d\n", taginae - getica);
                printf("Taginae to the Pragmatic Sanction: %d\n", sanction - taginae);
                printf("Getica to the Pragmatic Sanction: %d\n", sanction - getica);
                return 0;
            }
            ### run.sh
            python3 gap_years.py > python.txt
            cc -o gap_years_c gap_years.c
            ./gap_years_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
