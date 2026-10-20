package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 200 to 210: four dated events of the decade from Rome and China, put in order and spaced by a short program. */
val db184Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 184,
        kind = Kind.PROJECT,
        section = "Timeline",
        title = "Four dated events of 200 to 210 from Rome and China, sorted and spaced in code",
        originalTitle = "Timeline of four events",
        author = "No single author",
        year = 200,
        summary = "Four dated events of 200 to 210 are put in order in code, and the program prints the gap between each pair of years.",
        details = "The events are the battle of Guandu in 200, the start of the Severan marble plan in 203, the battle of Red Cliffs in 208 and Cao Cao's order on choosing men of talent in 210, each taken at its usual date. The marble plan's year is an inference, so the program checks the arithmetic of the years and makes no claim about exact days.",
        instructions = """
            1. Save the example as timeline.py.
            2. Run it: python3 timeline.py
        """,
        example = """
            events = [
                (210, "Cao Cao's order on choosing men of talent"),
                (200, "the battle of Guandu"),
                (208, "the battle of Red Cliffs"),
                (203, "the start of the Severan marble plan"),
            ]
            events.sort()
            previous = None
            for year, text in events:
                if previous is not None:
                    print(f"  gap of {year - previous} years")
                print(year, text)
                previous = year
            print("span from first to last:", events[-1][0] - events[0][0], "years")
        """,
    ),

)
