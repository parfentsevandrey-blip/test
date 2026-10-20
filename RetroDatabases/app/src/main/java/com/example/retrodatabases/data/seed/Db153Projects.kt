package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One project of 510 to 520: four dated turns from the 510s in the eastern Roman world and Rome, sorted and spaced by a short program. */
val db153Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 153,
        kind = Kind.PROJECT,
        section = "Byzantine",
        title = "Four dated turns of 513 to 519 in the eastern Roman world and Rome, sorted and spaced in code",
        originalTitle = "Four dated turns of 513 to 519 in the eastern Roman world and Rome, sorted and spaced in code",
        author = "Byzantine and papal chroniclers, as summarised in standard histories",
        year = 519,
        summary = "Four dated turns of the 510s, from Vitalian's revolt to the end of the Acacian schism, are set in a short program that prints the gaps between them.",
        details = "Vitalian rose against Emperor Anastasius in 513, Pope Symmachus died in 514, Anastasius died in 518, and the Acacian schism between Rome and Constantinople, which began in 484, ended in 519 when the eastern church accepted the Formula of Hormisdas. The program prints each gap of 1, 4 and 1 years, the total of 6, and the longest gap, all of which can be checked by subtraction.",
        rare = true,
        instructions = """
            1. Save the example as turns_510s.py.
            2. Run it: python3 turns_510s.py
        """,
        example = """
            EVENTS = [
                ("Vitalian revolt begins", 513),
                ("Pope Symmachus dies", 514),
                ("Anastasius dies", 518),
                ("Acacian schism ends", 519),
            ]

            gaps = []
            for (name1, year1), (name2, year2) in zip(EVENTS, EVENTS[1:]):
                gaps.append((name1, name2, year2 - year1))
                print(name1, year1, "to", name2, year2, ":", year2 - year1, "years")
            total = EVENTS[-1][1] - EVENTS[0][1]
            print("total from Vitalian revolt to Acacian schism end:", total, "years")
            longest = max(gaps, key=lambda gap: gap[2])
            print("longest gap:", longest[0], "to", longest[1], "(" + str(longest[2]) + " years)")
        """,
    ),

)
