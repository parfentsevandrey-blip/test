package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 290 to 300: a timeline of four dated events from China, Rome and Persia, sorted and spaced in code. */
val db175Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 175,
        kind = Kind.PROJECT,
        section = "Timeline",
        title = "A timeline of four events from 290 to 298, spaced out in code",
        originalTitle = "Four dated events",
        author = "No single author",
        year = 290,
        summary = "Four dated events of 290 to 298, from China, Rome and Persia, are sorted by year and spaced out by a short program.",
        details = "The events are the death of Emperor Wu of Jin in 290, the naming of the Caesars on 1 March 293, Constantius's reconquest of Britain in 296 and Galerius's victory at Satala in 298. Only the Caesars' date is given to the day, and the program counts the years between the events, so its output can be checked by hand.",
        instructions = """
            1. Save the example as timeline.py.
            2. Run it: python3 timeline.py
        """,
        example = """
            events = [
                (298, "Galerius's victory at Satala over Narseh"),
                (290, "death of Emperor Wu of Jin"),
                (296, "Constantius's reconquest of Britain"),
                (293, "Galerius and Constantius named Caesars"),
            ]
            events.sort()
            previous = None
            for year, what in events:
                if previous is None:
                    print(year, what)
                else:
                    print(year, what, f"({year - previous} years after the last)")
                previous = year
            print("span in years:", events[-1][0] - events[0][0])
        """,
    ),

)
