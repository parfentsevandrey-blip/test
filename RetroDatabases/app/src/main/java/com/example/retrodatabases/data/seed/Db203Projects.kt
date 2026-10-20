package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 10 to 20: four dated events from 11 to 18, put in order and spaced by a short program. */
val db203Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 203,
        kind = Kind.PROJECT,
        section = "Timeline",
        title = "Four events of 11 to 18 in order, with the years between them, worked out in code",
        originalTitle = "Timeline of four events",
        author = "No single author",
        year = 11,
        summary = "Four dated events of the years 11 to 18 are sorted by year, and a short program prints the gap in years between each event and the one before it.",
        details = "The events are the change of course of the Yellow River in 11, the battle of Idistaviso in 16, the making of Cappadocia into a Roman province in 17, and the start of the Red Eyebrows rebellion in 18. Accounts differ on the river, which changed course more than once, and 11 is the year most accounts give for the main change. The program prints the spacing between the events, so the span of seven years can be checked by hand.",
        instructions = """
            1. Save the example as timeline.py.
            2. Run it: python3 timeline.py
        """,
        example = """
            events = [
                (18, "the start of the Red Eyebrows rebellion"),
                (11, "the change of course of the Yellow River"),
                (17, "Cappadocia made a Roman province"),
                (16, "the battle of Idistaviso"),
            ]
            events.sort()
            previous = None
            for year, name in events:
                if previous is None:
                    print(year, name)
                else:
                    print(year, name, "gap", year - previous)
                previous = year
            print("span from first to last:", events[-1][0] - events[0][0], "years")
        """,
        rare = true,
    ),

)
