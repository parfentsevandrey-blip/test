package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 380 to 390: the years from the removal of the Altar of Victory to the senators' appeal of 384. */
val db166Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 166,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Years from the removal of the Altar of Victory to the senators' appeal of 384",
        originalTitle = "Years between two dated events",
        author = "Gratian and Symmachus",
        year = 384,
        summary = "The Altar of Victory was removed in 382 and the senators' appeal for its return was made in 384, and a short program counts the two years between them.",
        details = "Both dates are given as years only, so the count does not depend on any day or month. The appeal was aimed at Valentinian II, Gratian's half-brother, and the program prints the difference between the two years.",
        instructions = """
            1. Save the example as altar_appeal.py.
            2. Run it: python3 altar_appeal.py
        """,
        example = """
            altar_removed = 382
            senators_appeal = 384
            print("years from the removal to the appeal:", senators_appeal - altar_removed)
        """,
    ),

)
