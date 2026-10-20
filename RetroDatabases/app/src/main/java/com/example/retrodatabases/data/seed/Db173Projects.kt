package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 310 to 320: four dated events of 311 to 316, sorted and spaced by a short program. */
val db173Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 173,
        kind = Kind.PROJECT,
        section = "Timeline",
        title = "Four dated events of 311 to 316 in Rome and China, sorted and spaced by a short program",
        originalTitle = "Timeline of four events",
        author = "No single author",
        year = 316,
        summary = "Four events of 311 to 316, the deaths of Galerius and Maximinus Daia, the sack of Luoyang and the fall of Chang'an, are sorted by year and spaced by a short program.",
        details = "Galerius died in 311, Luoyang was sacked in 311, Maximinus Daia died in 313 and Chang'an fell in 316. The program sorts the events with ties in alphabetical order and prints the gap from each event to the next, so the output can be checked by hand.",
        instructions = """
            1. Save the example as timeline.py.
            2. Run it: python3 timeline.py
        """,
        example = """
            events = [
                (316, "fall of Chang'an"),
                (311, "sack of Luoyang"),
                (313, "death of Maximinus Daia"),
                (311, "death of Galerius"),
            ]
            events.sort()
            previous = None
            for year, name in events:
                gap = "" if previous is None else f" (gap {year - previous} years)"
                print(year, name + gap)
                previous = year
            print("span in years:", events[-1][0] - events[0][0])
        """,
    ),

)
