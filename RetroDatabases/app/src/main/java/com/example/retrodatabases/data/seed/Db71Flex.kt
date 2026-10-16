package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1330 to 1340: days between the fall of Mortimer, a battle of 1333, the climb of Mont Ventoux and the battle of Sluys. */
val db71Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 71,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from Mortimer's execution to Petrarch's climb of Mont Ventoux",
        originalTitle = "Days between two dated events",
        author = "Roger Mortimer and Francesco Petrarca",
        year = 1336,
        summary = "Mortimer was executed on 29 November 1330 and Petrarch climbed Mont Ventoux on 26 April 1336, and a short program counts the days between the two dates.",
        details = "Both dates are given as commonly stated, and the example uses Python's calendar with both dates in the same reckoning, which gives the same count here because no century year falls in the span. The span crosses two leap days, in February 1332 and February 1336.",
        instructions = """
            1. Save the example as mortimer_ventoux.py.
            2. Run it: python3 mortimer_ventoux.py
        """,
        example = """
            from datetime import date

            mortimer = date(1330, 11, 29)   # Mortimer's execution, as commonly given
            ventoux = date(1336, 4, 26)     # Petrarch's climb, as commonly given
            print("days from Mortimer's execution to the climb:", (ventoux - mortimer).days)
        """,
    ),

    entry(
        databaseId = 71,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the battle of Halidon Hill to the battle of Sluys",
        originalTitle = "Days between two battles",
        author = "The commanders of the two battles",
        year = 1340,
        summary = "The battle of Halidon Hill was fought on 19 July 1333 and the battle of Sluys on 24 June 1340, and a short program counts the days between them.",
        details = "Both dates are given as commonly stated, and the example uses fixed dates, so it tests date arithmetic rather than the battles themselves. The span crosses two leap days, in February 1336 and February 1340.",
        instructions = """
            1. Save the example as halidon_sluys.py.
            2. Run it: python3 halidon_sluys.py
        """,
        example = """
            from datetime import date

            halidon = date(1333, 7, 19)   # Halidon Hill, as commonly given
            sluys = date(1340, 6, 24)     # Sluys, as commonly given
            print("days from Halidon Hill to Sluys:", (sluys - halidon).days)
        """,
    ),

    entry(
        databaseId = 71,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from Mortimer's execution to the battle of Halidon Hill",
        originalTitle = "Days between two dated events",
        author = "Roger Mortimer and the armies of Halidon Hill",
        year = 1333,
        summary = "Mortimer's execution on 29 November 1330 and the battle of Halidon Hill on 19 July 1333 are two dated events, and a short program counts the days between them.",
        details = "The count uses fixed dates, as the other flex pieces of this decade do, and it crosses one leap day, February 1332. No century year falls in the span.",
        instructions = """
            1. Save the example as mortimer_halidon.py.
            2. Run it: python3 mortimer_halidon.py
        """,
        example = """
            from datetime import date

            mortimer = date(1330, 11, 29)
            halidon = date(1333, 7, 19)
            print("days from Mortimer's execution to Halidon Hill:", (halidon - mortimer).days)
        """,
    ),

)
