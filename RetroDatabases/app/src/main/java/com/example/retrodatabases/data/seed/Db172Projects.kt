package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Project of 325 to 328: three dated events, the death of Licinius, the death of Crispus and Athanasius's election, sorted and spaced by a short program. */
val db172Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 172,
        kind = Kind.PROJECT,
        section = "Events",
        title = "Three dated events of 325 to 328 sorted and spaced in code",
        originalTitle = "Three dated events",
        author = "No single author",
        year = 325,
        summary = "The execution of Licinius, the death of Crispus and the election of Athanasius are sorted by year and spaced by a short program.",
        details = "The years are the ones usually given for the three events, and the program only sorts them and subtracts, so the spacing can be checked by hand. Licinius is said to have died at Thessalonica in 325, Crispus's death is given as 326 and Athanasius's election as 328.",
        instructions = """
            1. Save the example as events.py.
            2. Run it: python3 events.py
        """,
        example = """
            events = [
                (328, "Athanasius elected bishop of Alexandria"),
                (325, "Licinius put to death at Thessalonica"),
                (326, "Crispus put to death"),
            ]
            events.sort()
            previous = None
            for year, name in events:
                if previous is None:
                    print(year, name, "(first)")
                else:
                    print(year, name, "- years after the one before:", year - previous)
                previous = year
            print("years from the first to the last:", events[-1][0] - events[0][0])
        """,
    ),

)
