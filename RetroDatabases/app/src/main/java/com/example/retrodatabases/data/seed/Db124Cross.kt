package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code piece of 800 to 810: the years between the stages of Harun al-Rashid's succession plan, computed in Python and in C. */
val db124Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 124,
        kind = Kind.CROSS,
        section = "Abbasid",
        title = "The years between the stages of Harun al-Rashid's succession plan, in Python and C",
        originalTitle = "Succession years",
        author = "Harun al-Rashid",
        year = 802,
        summary = "The years between the stages of Harun al-Rashid's succession plan are printed by a Python program and by a C program, and a script checks that both print the same figures.",
        details = "The dates used are 792 for the first choice of al-Amin, 799 for the addition of al-Ma'mun, 802 for the Mecca arrangement and 809 for Harun's death, and the four spans printed are 10, 3, 7 and 17 years. The first two dates belong to the mainstream reading of the sources, and historians still debate that reading, so the figures show the arithmetic of the dates rather than settle the question.",
        instructions = """
            1. Save the example as mecca.py, mecca.c and run.sh in one folder.
            2. Run it: sh run.sh
        """,
        example = """
            ### mecca.py
            EVENTS = [("first heir named", 792), ("al-Ma'mun added", 799), ("Mecca arrangement", 802), ("death of Harun", 809)]
            first, added, mecca, death = [year for _, year in EVENTS]
            print("years from the first heir to the Mecca arrangement:", mecca - first)
            print("years from al-Ma'mun's addition to the Mecca arrangement:", mecca - added)
            print("years from the Mecca arrangement to the death of Harun:", death - mecca)
            print("years from the first heir to the death of Harun:", death - first)
            ### mecca.c
            #include <stdio.h>

            int main(void) {
                int first = 792, added = 799, mecca = 802, death = 809;
                printf("years from the first heir to the Mecca arrangement: %d\n", mecca - first);
                printf("years from al-Ma'mun's addition to the Mecca arrangement: %d\n", mecca - added);
                printf("years from the Mecca arrangement to the death of Harun: %d\n", death - mecca);
                printf("years from the first heir to the death of Harun: %d\n", death - first);
                return 0;
            }
            ### run.sh
            python3 mecca.py > python.txt
            cc -o mecca_c mecca.c
            ./mecca_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
