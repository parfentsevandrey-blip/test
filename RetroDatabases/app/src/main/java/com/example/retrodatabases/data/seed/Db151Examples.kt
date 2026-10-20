package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One example of 530 to 540: the years of Justinian's Gothic War from its opening in 535 to its close in 554, counted in code. */
val db151Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 151,
        kind = Kind.EXAMPLE,
        section = "Byzantine",
        title = "From the opening of Justinian's Gothic War in 535 to its close in 554, 19 years counted in code",
        originalTitle = "bellum Gothicum",
        author = "Justinian and his generals Belisarius and Narses",
        year = 535,
        summary = "Counted by its usual dates, Justinian's war against the Ostrogoths of Italy ran from 535 to 554, a span of 19 years counted by a short program.",
        details = "The war opened with campaigns in Sicily and Dalmatia in 535, and it is often closed in 554 with the Pragmatic Sanction, although some accounts end it with the fall of Ravenna in 540 or the defeat of Totila at Busta Gallorum in 552. The span therefore depends on the closing date chosen, and the program only subtracts two years.",
        instructions = """
            1. Save the example as gothic_war_span.py.
            2. Run it: python3 gothic_war_span.py
        """,
        example = """
            START = 535  # the Gothic War opens with campaigns in Sicily and Dalmatia
            END = 554    # the war is often closed in 554 with the Pragmatic Sanction
            print("years from the start of the Gothic War to its end:", END - START)
        """,
    ),

)
