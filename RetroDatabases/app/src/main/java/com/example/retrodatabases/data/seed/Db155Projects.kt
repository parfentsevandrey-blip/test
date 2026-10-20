package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 490 to 500: a short program that sorts three changes of ruler of the decade and counts the years between them. */
val db155Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 155,
        kind = Kind.PROJECT,
        section = "Rulers",
        title = "A timeline of three changes of ruler in the 490s, sorted and counted in code",
        originalTitle = "Timeline of events",
        author = "Chroniclers of Byzantium and Persia",
        year = 491,
        summary = "A short program sorts three changes of ruler in the 490s by year, prints the gap between each pair, and counts the years from the first to the last.",
        details = "The events are the proclamation of Anastasius as Eastern emperor in 491, the deposition of Kavadh of Persia in 496 and his return to the throne in 499, as commonly given. The program sorts the list and prints the gaps, so the output can be checked by hand.",
        instructions = """
            1. Save the example as timeline.py.
            2. Run it: python3 timeline.py
        """,
        example = """
            events = [
                (499, "Kavadh of Persia returns to the throne"),
                (491, "Anastasius is proclaimed Eastern emperor"),
                (496, "Kavadh of Persia is deposed"),
            ]
            events.sort()
            previous = None
            for year, what in events:
                gap = "" if previous is None else f" (+{year - previous} years)"
                print(year, what + gap)
                previous = year
            print("years from first to last:", events[-1][0] - events[0][0])
        """,
    ),

)
