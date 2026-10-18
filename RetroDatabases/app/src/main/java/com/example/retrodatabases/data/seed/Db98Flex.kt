package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1063: one line of arithmetic about Alp Arslan's accession and the battle of Manzikert. */
val db98Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 98,
        kind = Kind.FLEX,
        section = "Sultans",
        title = "Years from Alp Arslan's accession to the battle of Manzikert",
        originalTitle = "Years between two fixed dates",
        author = "Alp Arslan and Romanos IV Diogenes",
        year = 1063,
        summary = "Alp Arslan became Seljuq sultan in 1063, and the battle of Manzikert, where he defeated the Byzantine emperor Romanos IV Diogenes, came in 1071, eight years later.",
        details = "Both dates are given as commonly stated, and the example is only a subtraction, so it tests the arithmetic rather than the events. The battle of Manzikert is usually dated to August 1071.",
        instructions = """
            1. Save the example as manzikert.py.
            2. Run it: python3 manzikert.py
        """,
        example = """
            accession = 1063   # Alp Arslan's accession, as commonly given
            battle = 1071      # the battle of Manzikert, as commonly given
            print("years from Alp Arslan's accession to Manzikert:", battle - accession)
        """,
        rare = true,
    ),

)
