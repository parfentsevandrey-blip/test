package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One project of 760 to 770: four events of 763 to 769 sorted by year and spaced by a short program. */
val db128Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 128,
        kind = Kind.PROJECT,
        section = "Events",
        title = "Four events of 763 to 769 from Tibet, Japan and the Franks, sorted and spaced by the years between them",
        originalTitle = "Event list, 763 to 769",
        author = "The courts named in the list",
        year = 769,
        summary = "A short program puts four events of 763 to 769 in year order, prints the gap from each one to the next and names the longest gap.",
        details = "The events are the Tibetan seizure of Chang'an in 763, the defeat of the Emi rebellion in 764, the end of Pippin's Aquitaine war in 768 and the Usa Hachiman oracle of 769. The first and last events are six years apart, and the longest gap is the four years from 764 to 768.",
        rare = true,
        instructions = """
            1. Save the example as event_gaps.py.
            2. Run it: python3 event_gaps.py
        """,
        example = """
            EVENTS = [
                ("Tibetan seizure of Chang'an", 763),
                ("defeat of the Emi rebellion", 764),
                ("end of Pippin's Aquitaine war", 768),
                ("Usa Hachiman oracle", 769),
            ]
            events = sorted(EVENTS, key=lambda event: event[1])
            gaps = []
            for (name, year), (_, next_year) in zip(events, events[1:]):
                gaps.append(next_year - year)
                print(year, name, "gap to the next event:", next_year - year)
            print("span from first to last:", events[-1][1] - events[0][1])
            print("longest gap:", max(gaps))
        """,
    ),

)
