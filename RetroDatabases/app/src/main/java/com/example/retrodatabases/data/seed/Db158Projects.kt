package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 460 to 470: four events of 461 to 468 from both empires, sorted by year and spaced by a short program. */
val db158Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 158,
        kind = Kind.PROJECT,
        section = "Timelines",
        title = "Four events of 461 to 468 in the western and eastern empires, sorted and spaced in code",
        originalTitle = "Timeline of 461 to 468",
        author = "No single author",
        year = 461,
        summary = "A short program sorts four dated events of the years 461 to 468 and prints the gap before each one, which gives a span of seven years.",
        details = "The events are the killing of Majorian in 461, the death of Libius Severus in 465, the rise of Euric to the Visigothic throne in 466, and the fleet that Basiliscus led against the Vandals in 468. The example sorts them by year and prints the gap before each event, so the seven-year span from first to last can be verified by hand.",
        instructions = """
            1. Save the example as events.py.
            2. Run it: python3 events.py
        """,
        example = """
            EVENTS = [
                (461, "Majorian killed"),
                (465, "Libius Severus dies"),
                (466, "Euric becomes Visigothic king"),
                (468, "Basiliscus leads a fleet against the Vandals"),
            ]
            EVENTS.sort()
            previous = None
            for year, label in EVENTS:
                if previous is None:
                    print(year, label)
                else:
                    print(year, label, "| gap", year - previous)
                previous = year
            print("span from first to last event:", EVENTS[-1][0] - EVENTS[0][0], "years")
        """,
        rare = true,
    ),

)
