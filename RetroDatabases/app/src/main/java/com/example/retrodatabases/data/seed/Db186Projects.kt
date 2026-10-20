package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 180 to 190: four events of the decade, sorted by year and spaced in code. */
val db186Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 186,
        kind = Kind.PROJECT,
        section = "Timeline",
        title = "From Marcus Aurelius's death to the coalition of 190, four events in year order",
        originalTitle = "Timeline of four events",
        author = "No single author",
        year = 180,
        summary = "Four events of 180 to 190 are sorted by year, and a short program prints the gap between each pair and the span of the list.",
        details = "The events are the death of Marcus Aurelius in 180, the execution of Perennis in 185, the death of Emperor Ling in 189 and the coalition against Dong Zhuo in 190. The program sorts the list before it counts the gaps, so the output is fixed and can be checked by hand.",
        instructions = """
            1. Save the example as events.py.
            2. Run it: python3 events.py
        """,
        example = """
            events = [
                (190, "coalition of warlords formed against Dong Zhuo under Yuan Shao"),
                (180, "death of Marcus Aurelius"),
                (189, "death of Emperor Ling of Han"),
                (185, "execution of Perennis"),
            ]
            events.sort()
            previous = None
            for year, label in events:
                if previous is None:
                    print(year, label)
                else:
                    print(year, label, "gap:", year - previous)
                previous = year
            print("span:", events[-1][0] - events[0][0], "years")
        """,
    ),

)
