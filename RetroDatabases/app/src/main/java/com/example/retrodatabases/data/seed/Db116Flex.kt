package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex piece of 885: the three years from the Norse siege of Paris to Leo VI's Basilika, counted in one line of code. */
val db116Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 116,
        kind = Kind.FLEX,
        section = "Years",
        title = "Years from the Norse siege of Paris to Leo VI's Basilika",
        originalTitle = "Years from the Norse siege of Paris to Leo VI's Basilika",
        author = "Odo of Paris and Leo VI",
        year = 885,
        summary = "The Norse siege of Paris began in 885 and Leo VI's Basilika is dated 888, and a one-line program counts the three years between them.",
        details = "The siege is usually dated from late 885, and the Basilika date of 888 is the one usually given, although accounts differ on it. The example uses the two years as commonly given and prints the difference.",
        instructions = """
            1. Save the example as paris_basilika.py.
            2. Run it: python3 paris_basilika.py
        """,
        example = """
            PARIS_SIEGE = 885   # the Norse siege of Paris begins, as usually given
            BASILIKA = 888      # Leo VI's Basilika, as usually given
            print("years from the siege of Paris to the Basilika:", BASILIKA - PARIS_SIEGE)
        """,
    ),

)
