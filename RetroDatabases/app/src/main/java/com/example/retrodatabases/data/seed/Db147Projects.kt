package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One project of 570 to 580: three dated events of the 570s from Persia, China and Byzantium, put in order by a short program. */
val db147Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 147,
        kind = Kind.PROJECT,
        section = "Persia, China and Byzantium",
        title = "Three events of 573 to 578 in Persia, China and Byzantium, ordered in code with their gaps",
        originalTitle = "Three events of 573 to 578 in Persia, China and Byzantium, ordered in code with their gaps",
        author = "Court annalists of Persia, China and Byzantium",
        year = 573,
        summary = "A short program puts three dated events of the 570s in order and prints the gap between each pair of neighbours.",
        details = "The events are the fall of Dara to the Persians in 573, the Northern Zhou's taking of the Northern Qi capital in 577, and the death of Justin II in 578, which brought Tiberius II to the Byzantine throne. The program only sorts the years and subtracts them, so a reader can check the gaps of 4 and 1 years and the span of 5 years against the three entries.",
        instructions = """
            1. Save the example as events_570s.py.
            2. Run it: python3 events_570s.py
        """,
        example = """
            events = [
                (578, "Justin II dies and Tiberius II takes the throne"),
                (573, "Dara falls to the Persians"),
                (577, "the Northern Zhou takes the capital of Northern Qi"),
            ]
            events.sort()
            for year, what in events:
                print(year, what)
            gaps = [later[0] - earlier[0] for earlier, later in zip(events, events[1:])]
            print("gaps:", " ".join(str(gap) for gap in gaps))
            print("span:", events[-1][0] - events[0][0], "years")
        """,
    ),

)
