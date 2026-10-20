package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One project of 560 to 570: four dated events from 561 to 568, from the death of Chlothar I to the Lombard entry into Italy, spaced by a short program. */
val db148Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 148,
        kind = Kind.PROJECT,
        section = "Eurasian events",
        title = "Four dated events of 561 to 568, from Chlothar's death to the Lombard entry into Italy, spaced in code",
        originalTitle = "Four dated events of 561 to 568, spaced in code",
        author = "Compilers of the program, from Gregory of Tours and the Byzantine and Irish sources",
        year = 568,
        summary = "Four events of the 560s, from the death of Chlothar I to the Lombard entry into Italy, are set in a short program that prints the gaps between them.",
        details = "Chlothar I of the Franks died in 561, the Fifty Years' Peace between Byzantium and Persia was made in 562, Columba founded the monastery on Iona in 563, and the Lombards entered Italy in 568. The program prints the gaps of 1, 1 and 5 years, the total of 7, and the longest gap, all of which can be checked by subtraction.",
        rare = true,
        instructions = """
            1. Save the example as events_560s.py.
            2. Run it: python3 events_560s.py
        """,
        example = """
            EVENTS = [
                ("Chlothar I dies", 561),
                ("Fifty Years' Peace", 562),
                ("Columba founds Iona", 563),
                ("Lombards enter Italy", 568),
            ]

            gaps = []
            for (name1, year1), (name2, year2) in zip(EVENTS, EVENTS[1:]):
                gaps.append((name1, name2, year2 - year1))
                print(name1, year1, "to", name2, year2, ":", year2 - year1, "years")
            total = EVENTS[-1][1] - EVENTS[0][1]
            print("total from Chlothar I dies to Lombards enter Italy:", total, "years")
            longest = max(gaps, key=lambda gap: gap[2])
            print("longest gap:", longest[0], "to", longest[1], "(" + str(longest[2]) + " years)")
        """,
    ),

)
