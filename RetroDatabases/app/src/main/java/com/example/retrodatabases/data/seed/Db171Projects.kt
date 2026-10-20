package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One project of 330 to 340: three dated events from outside this database, sorted and spaced by a short program. */
val db171Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 171,
        kind = Kind.PROJECT,
        section = "Timeline",
        title = "Three dated events of 332 to 340 from the Danube, north China and Aquileia",
        originalTitle = "Three dated events",
        author = "No single author",
        year = 332,
        summary = "Three events of the 330s and 340 are sorted by year in code, and the program prints the gaps and the span between them.",
        details = "The events are Constantine's campaign against the Goths in 332, Murong Huang's assumption of the title of Prince of Yan in November 337, and the death of Constantine II in an ambush near Aquileia in 340. The program sorts them by year and prints the gaps in years, so the output can be checked by hand.",
        instructions = """
            1. Save the example as timeline.py.
            2. Run it: python3 timeline.py
        """,
        example = """
            events = [
                (340, "Constantine II killed in an ambush near Aquileia"),
                (332, "Constantine campaigns against the Goths on the Danube"),
                (337, "Murong Huang takes the title of Prince of Yan"),
            ]
            events.sort()
            for year, what in events:
                print(year, what)
            gaps = [later[0] - earlier[0] for earlier, later in zip(events, events[1:])]
            print("gaps in years:", gaps)
            print("span in years:", events[-1][0] - events[0][0])
        """,
        rare = true,
    ),

)
