package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 150 to 159: four dated events of the decade, sorted by year and spaced by a short program. */
val db189Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 189,
        kind = Kind.PROJECT,
        section = "Dates",
        title = "Four dated events of 150 to 159 sorted and spaced in code",
        originalTitle = "Timeline of four events",
        author = "Inscriptions and chroniclers of the period",
        year = 150,
        summary = "Four events of the years 150 to 159, from an inscription in India to a purge at the Han court, are sorted by year, and a short program prints the gap to each.",
        details = "The events are the Junagadh rock inscription dated about 150, Justin Martyr's First Apology dated about 155, the start of the Yanxi era in 158, and the destruction of the Liang family at the Han court in 159. The dates are given as commonly stated, and the gaps count years only.",
        instructions = """
            1. Save the example as timeline.py.
            2. Run it: python3 timeline.py
        """,
        example = """
            events = [
                (150, "Junagadh rock inscription, about 150"),
                (155, "Justin Martyr's First Apology, about 155"),
                (158, "the Yanxi era begins in Han China"),
                (159, "the Liang family is destroyed at the Han court"),
            ]
            events.sort()
            previous = None
            for year, name in events:
                gap = "" if previous is None else f" (+{year - previous})"
                print(year, name + gap)
                previous = year
        """,
        rare = true,
    ),

)
