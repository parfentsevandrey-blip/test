package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 400 to 410: a timeline of four events of the western empire from 402 to 408, sorted and spaced by year. */
val db164Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 164,
        kind = Kind.PROJECT,
        section = "Western wars",
        title = "Pollentia, Verona, the Rhine crossing and Stilicho's death, in order",
        originalTitle = "Timeline of four events",
        author = "Stilicho and the western court",
        year = 402,
        summary = "A short program sorts four events of the western Roman empire from 402 to 408 by year and prints the gaps between them.",
        details = "The events are the battle of Pollentia in 402, the battle of Verona in 403, the Rhine crossing at the end of 406, and the death of Stilicho in 408. The example uses only the year of each event, so the gaps come out in whole years.",
        instructions = """
            1. Save the example as events.py.
            2. Run it: python3 events.py
        """,
        example = """
            events = [
                (408, "the death of Stilicho"),
                (402, "the battle of Pollentia"),
                (406, "the Rhine crossing"),
                (403, "the battle of Verona"),
            ]
            events.sort()
            for year, name in events:
                print(year, name)
            print("span in years:", events[-1][0] - events[0][0])
            gaps = [events[i + 1][0] - events[i][0] for i in range(len(events) - 1)]
            print("gaps:", gaps)
        """,
        rare = true,
    ),

)
