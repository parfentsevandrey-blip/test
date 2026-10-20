package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 350 to 357: a timeline of four dated events, sorted and spaced in code. */
val db169Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 169,
        kind = Kind.PROJECT,
        section = "Timeline",
        title = "The murder of Constans to Constantius's Rome visit, four dated events counted in code",
        originalTitle = "Timeline of 350 to 357",
        author = "No one author",
        year = 350,
        summary = "Four dated events from the murder of Constans in 350 to Constantius's visit to Rome in 357 are sorted by year, and a short program prints the gaps between them.",
        details = "Magnentius's agents killed Constans in 350, the Caesar Gallus was executed in 354, the council of Milan of 355 condemned Athanasius, and Constantius visited Rome in 357. The years are given as commonly stated, and the program prints each event, the gap to the next event and the span of the four.",
        instructions = """
            1. Save the example as timeline.py.
            2. Run it: python3 timeline.py
        """,
        example = """
            EVENTS = [
                (357, "Constantius visits Rome"),
                (350, "Constans is murdered"),
                (355, "council of Milan"),
                (354, "Gallus is executed"),
            ]
            events = sorted(EVENTS)
            for year, name in events:
                print(year, name)
            for (first, _), (second, _) in zip(events, events[1:]):
                print("gap", second - first)
            print("span", events[-1][0] - events[0][0])
        """,
    ),

)
