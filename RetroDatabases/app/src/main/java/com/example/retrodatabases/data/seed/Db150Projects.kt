package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One project of 540 to 550: four dated events from 536 to 548, the entry into Rome, the lifted siege, the sack of Antioch and the death of Theodora, spaced by a short program. */
val db150Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 150,
        kind = Kind.PROJECT,
        section = "Byzantine",
        title = "Four dated events of 536 to 548, the entry into Rome, the lifted siege, the sack of Antioch and the death of Theodora, spaced in code",
        originalTitle = "Four dated events of 536 to 548, spaced in code",
        author = "Procopius and the Byzantine chroniclers",
        year = 548,
        summary = "Four events of Justinian's wars and court, from Belisarius's entry into Rome to the death of Theodora, are set in a short program that prints the gaps between them.",
        details = "Belisarius entered Rome in December 536, the Goths lifted their siege of the city in March 538, Khosrow I sacked Antioch in 540, and Theodora died in June 548. The program prints the gaps of 2, 2 and 8 years, their total of 12, and the longest gap, all of which can be checked by subtraction.",
        instructions = """
            1. Save the example as events_540s.py.
            2. Run it: python3 events_540s.py
        """,
        example = """
            EVENTS = [
                ("Belisarius enters Rome", 536),
                ("Goths lift the siege of Rome", 538),
                ("Khosrow sacks Antioch", 540),
                ("Theodora dies", 548),
            ]

            gaps = []
            for (name1, year1), (name2, year2) in zip(EVENTS, EVENTS[1:]):
                gaps.append((name1, name2, year2 - year1))
                print(name1, year1, "to", name2, year2, ":", year2 - year1, "years")
            total = EVENTS[-1][1] - EVENTS[0][1]
            print("total from Belisarius enters Rome to Theodora dies:", total, "years")
            longest = max(gaps, key=lambda gap: gap[2])
            print("longest gap:", longest[0], "to", longest[1], "(" + str(longest[2]) + " years)")
        """,
    ),

)
