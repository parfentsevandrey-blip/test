package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One project of 560 to 570: three reigns that touch the 560s, Khosrow I, Chilperic I and Justin II, summed and ranked by a short program. */
val db148Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 148,
        kind = Kind.PROJECT,
        section = "Eurasian rulers",
        title = "Khosrow I, Chilperic I and Justin II, three reigns that touch the 560s, summed and ranked by a short program",
        originalTitle = "reign lengths of three rulers",
        author = "Compilers of the program",
        year = 565,
        summary = "Three reigns that overlap the 560s are measured in years, ranked from longest to shortest, and summed by a short program.",
        details = "Khosrow I of Persia reigned from 531 to 579, Chilperic I of the Franks from 561 to 584, and Justin II of Byzantium from 565 to 578, giving 48, 23 and 13 years. The year 565 is the accession of Justin II, the latest start in the list.",
        rare = true,
        instructions = """
            1. Save the example as reign_ranking.py.
            2. Run it: python3 reign_ranking.py
        """,
        example = """
            # Three reigns that touch the 560s, summed and ranked by length
            REIGNS = {
                "Khosrow I": (531, 579),
                "Chilperic I": (561, 584),
                "Justin II": (565, 578),
            }
            lengths = {name: end - start for name, (start, end) in REIGNS.items()}
            for name, years in sorted(lengths.items(), key=lambda pair: -pair[1]):
                print(name, years, "years")
            print("total:", sum(lengths.values()), "years")
        """,
    ),

)
