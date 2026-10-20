package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 393 to 400: a short timeline of three events from Hippo to Constantinople, sorted by year in code. */
val db165Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 165,
        kind = Kind.PROJECT,
        section = "Events",
        title = "Three events of 393 to 400 from Hippo to Constantinople, in order by year",
        originalTitle = "Three events of 393 to 400 from Hippo to Constantinople, in order by year",
        author = "No single author",
        year = 393,
        summary = "A short program sorts three dated events of the late fourth century by year and prints the years between neighbours, so the order can be checked by hand.",
        details = "The events are a council of African bishops at Hippo in 393, the fall of Eutropius at Constantinople in 399, and the death of the Gothic rebel Gainas in 400, each given only by its year. The example puts them in order, prints the years after the last event, and gives the total span.",
        instructions = """
            1. Save the example as timeline.py.
            2. Run it: python3 timeline.py
        """,
        example = """
            events = [
                (400, "Gainas, the Gothic rebel against the eastern court, is killed"),
                (393, "a council of African bishops at Hippo"),
                (399, "Eutropius falls from power at Constantinople"),
            ]
            events.sort()
            last = None
            for year, label in events:
                if last is None:
                    print(year, label, "| first event")
                else:
                    print(year, label, "| years after the last:", year - last)
                last = year
            print("span:", events[-1][0] - events[0][0], "years")
        """,
        rare = true,
    ),

)
