package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 110 to 120: the years from the Yiwen zhi, dated 111, to Hadrian's accession in 117. */
val db193Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 193,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Years from the Yiwen zhi of 111 to Hadrian's accession in 117",
        originalTitle = "Years between two dated events",
        author = "Ban Gu and Hadrian",
        year = 117,
        summary = "The Yiwen zhi is dated to 111 and Hadrian came to power in 117, so six years lie between the two, and a short program prints that count.",
        details = "The year 111 is the completion date that one summary gives for the Book of Han, and 117 is the usual year of Hadrian's accession. The program makes one subtraction, so it checks the arithmetic and says nothing about how the book was made.",
        instructions = """
            1. Save the example as yiwen_hadrian.py.
            2. Run it: python3 yiwen_hadrian.py
        """,
        example = """
            CATALOGUE = 111   # completion year of the Book of Han, with its Yiwen zhi, as one summary gives it
            ACCESSION = 117   # Hadrian's accession, as usually given
            print("years from the Yiwen zhi to Hadrian's accession:", ACCESSION - CATALOGUE)
        """,
        rare = true,
    ),

)
