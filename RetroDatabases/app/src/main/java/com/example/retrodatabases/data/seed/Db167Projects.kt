package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 370 to 379: a timeline of three dated events of the 370s, sorted and spaced by a short program. */
val db167Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 167,
        kind = Kind.PROJECT,
        section = "Timeline",
        title = "The spans between three dated events of the 370s, counted in code",
        originalTitle = "The spans between three dated events of the 370s, counted in code",
        author = "No single author",
        year = 370,
        summary = "Three dated events of the 370s, the fall of Former Yan, the death of Ephrem and the death of Basil of Caesarea, are sorted and their gaps counted by a short program.",
        details = "The years used are 370, 373 and 379, the years usually given for these three events. The program sorts them by year and prints the gap before each one, so the output can be checked by hand, and it prints the span from the first event to the last.",
        instructions = """
            1. Save the example as timeline_370s.py.
            2. Run it: python3 timeline_370s.py
        """,
        example = """
            events = [
                (379, "death of Basil of Caesarea"),
                (370, "fall of Former Yan to Former Qin"),
                (373, "death of Ephrem the Syrian"),
            ]
            events.sort()
            previous = None
            for year, what in events:
                gap = "" if previous is None else f", {year - previous} years after the one before"
                print(year, what + gap)
                previous = year
            print("span in years:", events[-1][0] - events[0][0])
        """,
        rare = true,
    ),

)
