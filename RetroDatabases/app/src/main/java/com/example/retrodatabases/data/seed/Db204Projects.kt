package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 0 to 10: four dated events of 4 to 9, sorted by year and spaced in a short program. */
val db204Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 204,
        kind = Kind.PROJECT,
        section = "Events",
        title = "Four events of 4 to 9, from the death of Gaius Caesar to the defeat of Varus, sorted and spaced in code",
        originalTitle = "Timeline of four events",
        author = "No single author",
        year = 4,
        summary = "Four dated events of 4 to 9 are sorted by year and spaced in a short program, which prints the years between each pair and the span from first to last.",
        details = "The events are the death of Gaius Caesar in 4, the death of Emperor Ping of Han in 6, the exile of Ovid to Tomis in 8, and the defeat of Varus in Germany in 9, each given by its usual year. The example lists them out of order so that the sorting step is shown, and the output can be checked by hand.",
        instructions = """
            1. Save the example as events.py.
            2. Run it: python3 events.py
        """,
        example = """
            events = [
                (9, "defeat of Varus in Germany"),
                (4, "death of Gaius Caesar"),
                (8, "exile of Ovid to Tomis"),
                (6, "death of Emperor Ping of Han"),
            ]
            events.sort()
            for index, (year, name) in enumerate(events):
                print(year, name)
                if index + 1 < len(events):
                    print("years to the next event:", events[index + 1][0] - year)
            print("span from first to last:", events[-1][0] - events[0][0], "years")
        """,
    ),

)
