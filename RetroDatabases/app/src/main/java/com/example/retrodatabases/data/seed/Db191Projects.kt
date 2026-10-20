package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 130 to 140: four dated events from the Bar Kokhba revolt to the death of Zhang Heng, sorted and spaced in code. */
val db191Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 191,
        kind = Kind.PROJECT,
        section = "Timelines",
        title = "Four events of 132 to 139, sorted by year and timed in code",
        originalTitle = "Timeline of four events",
        author = "No single author",
        year = 132,
        summary = "Four dated events from 132 to 139 are sorted by year, and a short program prints the gap in years from each event to the next.",
        details = "The events are the start of the Bar Kokhba revolt in 132, the fall of Betar in 135, the succession of Antoninus Pius to Hadrian in 138 and the death of Zhang Heng in 139, each given by its year only. The gaps are 3, 3 and 1 years, and the span is 7, so the output can be checked by hand.",
        instructions = """
            1. Save the example as timeline.py.
            2. Run it: python3 timeline.py
        """,
        example = """
            events = [
                (139, "death of Zhang Heng"),
                (132, "start of the Bar Kokhba revolt"),
                (138, "Antoninus Pius succeeds Hadrian"),
                (135, "fall of Betar"),
            ]
            events.sort()
            for (year, name), (later, _) in zip(events, events[1:]):
                print(year, name, "gap", later - year)
            print("last", events[-1][0], events[-1][1])
            print("span", events[-1][0] - events[0][0])
        """,
        rare = true,
    ),

)
