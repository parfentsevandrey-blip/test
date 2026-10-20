package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 175 to 180: four dated events of the decade, sorted and spaced by a short program. */
val db187Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 187,
        kind = Kind.PROJECT,
        section = "Events",
        title = "Four dated events of 175 to 180 sorted and spaced in code",
        originalTitle = "Event spacing",
        author = "No single author",
        year = 180,
        summary = "The revolt of Avidius Cassius in 175, the persecution dated to 177, the sale of offices in 178 and the death of Marcus Aurelius in 180 are sorted and spaced by a short program.",
        details = "The years are the commonly given ones for these events, and the program prints their order and the gaps between them, which show the spacing of the years rather than the length of any event. The sale of offices is dated 177 in some accounts, so its gaps are the figures for the commonly given year.",
        instructions = """
            1. Save the example as event_spacing.py.
            2. Run it: python3 event_spacing.py
        """,
        example = """
            events = {
                "revolt of Avidius Cassius": 175,
                "persecution at Lyons": 177,
                "sale of offices under Ling": 178,
                "death of Marcus Aurelius": 180,
            }
            years = sorted(events.values())
            print("sorted years:", years)
            for first, second in zip(years, years[1:]):
                print("gap", first, "to", second, ":", second - first)
            print("span:", years[-1] - years[0])
        """,
    ),

)
