package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 230 to 240: four dated events of 234 to 239 in China and Rome, sorted and spaced in code. */
val db181Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 181,
        kind = Kind.PROJECT,
        section = "Timelines",
        title = "Four dated events of 234 to 239 in China and Rome, sorted and spaced in code",
        originalTitle = "Timeline of four events",
        author = "No single author",
        year = 234,
        summary = "Four dated events of 234 to 239 in China and Rome are sorted by year, and a short program prints the years between each pair of neighbouring events.",
        details = "The deaths are Zhuge Liang's in 234, the murder of Severus Alexander in 235, the death of Gongsun Yuan in 238 and the death of Cao Rui in 239, each year given as commonly stated. The example prints the sorted timeline and the years between neighbouring events, so the spacing can be checked by hand.",
        instructions = """
            1. Save the example as timeline_230s.py.
            2. Run it: python3 timeline_230s.py
        """,
        example = """
            events = [
                (239, "Cao Rui's death"),
                (234, "Zhuge Liang's death"),
                (238, "Gongsun Yuan's death"),
                (235, "Severus Alexander's murder"),
            ]
            events.sort()
            for year, name in events:
                print(year, name)
            for (earlier, _), (later, _) in zip(events, events[1:]):
                print("years from", earlier, "to", later, "is", later - earlier)
        """,
        rare = true,
    ),

)
