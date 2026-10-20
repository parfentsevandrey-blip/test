package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 20 to 30: the years from Strabo's Geographica to the accession of Caligula. */
val db202Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 202,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Years from Strabo's Geographica to the accession of Caligula",
        originalTitle = "Years between two fixed dates",
        author = "Strabo and Gaius Caligula",
        year = 23,
        summary = "The completion year of Strabo's Geographica and the accession of Caligula are fourteen years apart, and a short program counts the years between them.",
        details = "Both years are given as commonly stated, and the example uses plain integers, so the count is exact for the years as written. The span includes the last years of Tiberius's reign, and the first year is the completion year used for the Geographica in this record.",
        instructions = """
            1. Save the example as strabo_caligula.py.
            2. Run it: python3 strabo_caligula.py
        """,
        example = """
            strabo = 23     # completion of the Geographica, as used in this record
            caligula = 37   # accession of Gaius Caligula, as commonly given
            print("years from Strabo's work to Caligula's accession:", caligula - strabo)
        """,
    ),

)
