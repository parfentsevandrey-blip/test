package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 280 to 290: four dated events of the decade, sorted and spaced by a short program. */
val db176Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 176,
        kind = Kind.PROJECT,
        section = "Timelines",
        title = "A timeline of 280 to 290: Wu's fall, Carus's death, Wani's arrival and Sima Yan's death",
        originalTitle = "Timeline of four dated events",
        author = "No single author",
        year = 280,
        summary = "Four dated events of 280 to 290 are sorted by year, and a short program prints the gap in years between each one and the one before.",
        details = "The events are the Jin conquest of Wu in 280, the death of the Roman emperor Carus in 283, Wani's traditional arrival in Japan in 285, and the death of the Jin emperor Sima Yan in 290. Wani's year is the traditional reckoning, and the other years are the years usually given.",
        instructions = """
            1. Save the example as timeline.py.
            2. Run it: python3 timeline.py
        """,
        example = """
            events = [
                (290, "death of Sima Yan, Emperor Wu of Jin"),
                (280, "Jin conquest of Wu"),
                (285, "Wani's traditional arrival in Japan"),
                (283, "death of the Roman emperor Carus"),
            ]
            events.sort()
            previous = None
            for year, name in events:
                gap = "" if previous is None else f" (+{year - previous} years)"
                print(year, name + gap)
                previous = year
            print("span from first to last:", events[-1][0] - events[0][0], "years")
        """,
        rare = true,
    ),

)
