package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 110 to 120: four dates of 114 to 120, Armenia, Antioch, Ctesiphon and the Yongning era, put in order. */
val db193Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 193,
        kind = Kind.PROJECT,
        section = "Timelines",
        title = "Armenia, Antioch, Ctesiphon and the Yongning era: four dates of 114 to 120 in order",
        originalTitle = "Dated events",
        author = "No single author",
        year = 120,
        summary = "Four dated events of 114 to 120, the annexation of Armenia, an earthquake at Antioch, the capture of Ctesiphon and the start of the Yongning era, are put in order and spaced by a short program.",
        details = "Trajan annexed Armenia in 114, an earthquake struck Antioch in 115, Trajan took Ctesiphon in 116, and the Han Yongning era began in 120, each date as commonly given. The program prints the events in order with the gap in years after each one, so the output is the same on every run.",
        instructions = """
            1. Save the example as events.py.
            2. Run it: python3 events.py
        """,
        example = """
            events = [
                (120, "Yongning era begins in Han China"),
                (114, "Trajan annexes Armenia"),
                (116, "Trajan takes Ctesiphon"),
                (115, "earthquake at Antioch"),
            ]
            events.sort()
            for index, (year, label) in enumerate(events):
                gap = "" if index == 0 else f" (+{year - events[index - 1][0]} years)"
                print(year, label + gap)
            print("events:", len(events), "span:", events[-1][0] - events[0][0], "years")
        """,
    ),

)
