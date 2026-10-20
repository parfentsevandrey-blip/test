package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 160 to 170: three dated events from 161 to 169, sorted and spaced by a short program. */
val db188Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 188,
        kind = Kind.PROJECT,
        section = "Timelines",
        title = "Three dated events of 161 to 169, sorted and spaced in code",
        originalTitle = "Events in order",
        author = "No single author",
        year = 169,
        summary = "A short program sorts three dated events of 161, 168 and 169 and prints the years between them.",
        details = "The events are the joint accession of Marcus Aurelius and Lucius Verus in 161, the first year of Emperor Ling of Han in 168, and the death of Lucius Verus in 169. The program stores them out of order, sorts them by year, and prints each gap, so the output can be checked by hand.",
        rare = true,
        instructions = """
            1. Save the example as timeline.py.
            2. Run it: python3 timeline.py
        """,
        example = """
            events = [
                (169, "Lucius Verus dies"),
                (161, "Marcus Aurelius and Lucius Verus share the throne"),
                (168, "Emperor Ling of Han begins his reign"),
            ]
            events.sort()
            for year, label in events:
                print(year, label)
            for (first, _), (second, _) in zip(events, events[1:]):
                print("years between:", second - first)
            print("span:", events[-1][0] - events[0][0])
        """,
    ),

)
