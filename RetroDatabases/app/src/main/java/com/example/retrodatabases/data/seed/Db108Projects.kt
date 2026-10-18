package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 960 to 970: a short program that sums and ranks three Byzantine reigns from 959 to 976. */
val db108Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 108,
        kind = Kind.PROJECT,
        section = "Byzantium",
        title = "The reigns of three Byzantine emperors from 959 to 976, summed and ranked",
        originalTitle = "The reigns of three Byzantine emperors from 959 to 976, summed and ranked",
        author = "Romanos II, Nikephoros II Phokas and John I Tzimiskes",
        year = 969,
        summary = "A short program sums and ranks the reign lengths of three Byzantine emperors, Romanos II, Nikephoros II Phokas and John I Tzimiskes, from their start and end years.",
        details = "Romanos II reigned from 959 to 963, Nikephoros II Phokas from 963 to 969, and John I Tzimiskes from 969 to 976, and the program counts each reign as the difference between its start and end years. The counts are whole calendar years, so they are approximate lengths rather than exact durations. The year 969 is when John I succeeded Nikephoros II, which closes one reign and opens the next.",
        instructions = """
            1. Save the example as reigns.py.
            2. Run it: python3 reigns.py
        """,
        example = """
            reigns = [
                ("Romanos II", 959, 963),
                ("Nikephoros II Phokas", 963, 969),
                ("John I Tzimiskes", 969, 976),
            ]
            lengths = [(end - start, name) for name, start, end in reigns]
            print("rulers:", len(lengths))
            print("total years:", sum(length for length, name in lengths))
            longest = max(lengths)
            shortest = min(lengths)
            print("longest:", longest[1], longest[0], "years")
            print("shortest:", shortest[1], shortest[0], "years")
            print("average:", f"{sum(length for length, name in lengths) / len(lengths):.2f}")
        """,
    ),

)
