package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 64 to 69: four events of the Roman years sorted in code, with the gap between each pair. */
val db198Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 198,
        kind = Kind.PROJECT,
        section = "Events",
        title = "Four events of 64 to 69, sorted and spaced in code",
        originalTitle = "Four dated events",
        author = "No single author",
        year = 64,
        summary = "Four events of the years 64 to 69 are sorted by year, and a short program prints the gap between each pair and the span from the first to the last.",
        details = "The years are the commonly given years of the Great Fire of Rome, the death of Seneca, the death of Nero and the proclamation of Vespasian, and the program works in whole years. The gaps come to 1, 3 and 1 years, and the span from first to last is 5 years, so the output can be checked by hand.",
        instructions = """
            1. Save the example as events.py.
            2. Run it: python3 events.py
        """,
        example = """
            events = [
                (68, "the death of Nero"),
                (64, "the Great Fire of Rome"),
                (69, "Vespasian proclaimed emperor"),
                (65, "the death of Seneca"),
            ]
            events.sort()
            previous = None
            for year, name in events:
                if previous is None:
                    print(year, name)
                else:
                    print(year, name, "gap", year - previous)
                previous = year
            print("years from first to last:", events[-1][0] - events[0][0])
        """,
    ),

)
