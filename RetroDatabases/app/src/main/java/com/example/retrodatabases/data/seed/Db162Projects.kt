package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One project of 421 to 425: three dated events of the western Roman empire, sorted and spaced by a short program. */
val db162Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 162,
        kind = Kind.PROJECT,
        section = "Rome",
        title = "Three dated events of 421 to 425 in the western Roman empire, sorted and spaced in code",
        originalTitle = "Western imperial events",
        author = "No single author",
        year = 421,
        summary = "Three events of 421 to 425 in the western Roman empire are sorted by year, and a short program prints the gap between each pair and the span of the list.",
        details = "The events are the elevation of Constantius III as co-emperor in 421, the death of Honorius in 423, and the elevation of Valentinian III as Augustus in 425. Only the years enter the program, so the gaps are counted in whole years.",
        instructions = """
            1. Save the example as western_events.py.
            2. Run it: python3 western_events.py
        """,
        example = """
            events = [
                (425, "Valentinian III raised to Augustus"),
                (421, "Constantius III made co-emperor"),
                (423, "Honorius dies at Ravenna"),
            ]
            events.sort()
            for year, label in events:
                print(year, label)
            for (first, _), (second, _) in zip(events, events[1:]):
                print("gap", second - first, "years")
            print("span", events[-1][0] - events[0][0], "years")
        """,
    ),

)
