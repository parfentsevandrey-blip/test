package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 341 to 346: three events of the decade put in order, with the gaps between them counted by a short program. */
val db170Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 170,
        kind = Kind.PROJECT,
        section = "Timelines",
        title = "Three events of 341 to 346, put in order and spaced by a program",
        originalTitle = "Timeline of events",
        author = "No single author",
        year = 341,
        summary = "A council of Eastern bishops at Antioch in 341, the accession of Emperor Mu of the Eastern Jin in 344 and the return of Athanasius to Alexandria in 346 are put in order, and a short program prints the gaps.",
        details = "The three events are given by the year usually cited for each, and the program sorts them and prints the gap to the next event. The arithmetic is simple enough to check by hand, and the example counts no reign length.",
        instructions = """
            1. Save the example as timeline.py.
            2. Run it: python3 timeline.py
        """,
        example = """
            events = [
                (346, "Athanasius returns to Alexandria"),
                (341, "council of Eastern bishops at Antioch"),
                (344, "Emperor Mu of the Eastern Jin comes to the throne"),
            ]
            events.sort()
            for (year, label), (next_year, _) in zip(events, events[1:]):
                print(year, label, "gap to next:", next_year - year)
            last_year, last_label = events[-1]
            print("last event:", last_year, last_label)
            print("years from first to last:", last_year - events[0][0])
        """,
    ),

)
