package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One project of 70 to 80: four dated events of the decade, put in order and spaced by a short program. */
val db197Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 197,
        kind = Kind.PROJECT,
        section = "Timeline",
        title = "Four dated events from 70 to 79 in order, with the years between them",
        originalTitle = "Timeline of four events",
        author = "No single author",
        year = 70,
        summary = "Four dated events of the decade, from the burning of the Temple to the eruption of Vesuvius, are sorted and spaced by a short program that counts the years between them.",
        details = "The census is dated 73 to 74 and the program uses 74, the Natural History is taken as finished in 77 and the eruption as 79, so the gaps count years and not days. The year 70 for the Temple is the start of the timeline, and the program prints the gap from each event to the one before it.",
        instructions = """
            1. Save the example as timeline.py.
            2. Run it: python3 timeline.py
        """,
        example = """
            events = [
                (79, "eruption of Vesuvius"),
                (70, "Temple at Jerusalem burned"),
                (77, "Pliny's Natural History finished"),
                (74, "census of Vespasian and Titus"),
            ]
            events.sort()
            previous = None
            for year, name in events:
                gap = "" if previous is None else f" (+{year - previous})"
                print(year, name + gap)
                previous = year
            print("span in years:", events[-1][0] - events[0][0])
        """,
    ),

)
