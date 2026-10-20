package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 270 to 279: four dated events from Plotinus's death to the find of the Bamboo Annals, sorted and spaced in code. */
val db177Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 177,
        kind = Kind.PROJECT,
        section = "Events",
        title = "Plotinus's death to the Bamboo find: four dates of the 270s, spaced in code",
        originalTitle = "Four dated events",
        author = "Ancient chroniclers of Rome and China",
        year = 270,
        summary = "Four dated events of 270 to 279, from Plotinus's death to the find of the Bamboo Annals, are sorted and spaced by a short program that prints the years between them.",
        details = "Plotinus died in 270, Aurelian's festival of the Sun is dated to 274, Aurelian was killed in 275, and the Bamboo Annals were found in 279, with accounts differing on the find, which some give as 281. The example sorts the four events by year, prints the gap to each earlier event, and reports a span of nine years, using the 279 date.",
        instructions = """
            1. Save the example as timeline.py.
            2. Run it: python3 timeline.py
        """,
        example = """
            events = [
                (279, "the Bamboo Annals are found in a tomb"),
                (270, "Plotinus dies"),
                (275, "Aurelian is killed in Thrace"),
                (274, "Aurelian's Sun festival is set at Rome"),
            ]
            events.sort()
            previous = None
            for year, name in events:
                gap = "" if previous is None else f" (+{year - previous})"
                print(f"{year} {name}{gap}")
                previous = year
            print("span in years:", events[-1][0] - events[0][0])
        """,
    ),

)
