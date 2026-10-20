package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 20 to 30: four dated events of 21 to 26, sorted and spaced in code. */
val db202Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 202,
        kind = Kind.PROJECT,
        section = "Timeline",
        title = "Four dated events of 21 to 26, sorted and spaced in code",
        originalTitle = "Timeline of four events",
        author = "No single author",
        year = 26,
        summary = "Four dated events of 21 to 26 are sorted by year and spaced in code, with the years between neighbours and the overall span printed.",
        details = "The four events are the revolt of Julius Sacrovir in Gaul in 21, the death of Drusus Julius Caesar in 23, the restoration of the Han under Liu Xiu in 25 and the appointment of Pontius Pilate as governor of Judaea in 26, each given by its year. The program sorts them into order and prints the gaps, so the output can be checked by hand.",
        instructions = """
            1. Save the example as timeline.py.
            2. Run it: python3 timeline.py
        """,
        example = """
            events = [
                (26, "Pilate takes office as governor of Judaea"),
                (21, "Julius Sacrovir revolts in Gaul"),
                (25, "Liu Xiu restores the Han as Emperor Guangwu"),
                (23, "Drusus Julius Caesar dies"),
            ]
            events.sort()
            for year, what in events:
                print(year, what)
            for (first, _), (second, _) in zip(events, events[1:]):
                print("years apart", second - first)
            print("span", events[-1][0] - events[0][0])
        """,
    ),

)
