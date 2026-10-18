package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1200 to 1210: one line of arithmetic each, counting the years between two dated events of the decade. */
val db84Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 84,
        kind = Kind.FLEX,
        section = "Years",
        title = "Years from Philip Augustus's privilege of 1200 to the Delhi Sultanate",
        originalTitle = "Years between two dates",
        author = "Philip Augustus of France and Qutb al-Din Aibak",
        year = 1200,
        summary = "Philip Augustus's privilege for the scholars of Paris came six years before the Delhi Sultanate was founded in 1206, and a one-line program counts the years.",
        details = "Both dates are given as commonly stated, the privilege as 1200 and the sultanate as 1206. The program subtracts one year from the other, so the answer counts years and not days.",
        instructions = """
            1. Save the example as paris_delhi.py.
            2. Run it: python3 paris_delhi.py
        """,
        example = """
            print("years from 1200 to 1206:", 1206 - 1200)
        """,
    ),

    entry(
        databaseId = 84,
        kind = Kind.FLEX,
        section = "Years",
        title = "Years from the first English patent rolls to the Mongol Empire",
        originalTitle = "Years between two dates",
        author = "The chancery of King John and Chinggis Khan",
        year = 1201,
        summary = "The first surviving English patent rolls of 1201 came five years before the Mongol Empire was proclaimed in 1206, and a one-line program counts the years.",
        details = "The patent rolls are dated from the surviving series, which begins in 1201, and the Mongol Empire is dated to 1206 as commonly given. The line of code is only the subtraction, and both years are fixed in the example.",
        instructions = """
            1. Save the example as patent_mongol.py.
            2. Run it: python3 patent_mongol.py
        """,
        example = """
            print("years from 1201 to 1206:", 1206 - 1201)
        """,
    ),

    entry(
        databaseId = 84,
        kind = Kind.FLEX,
        section = "Years",
        title = "Years from the Venetian grosso to Gottfried's Tristan",
        originalTitle = "Years between two dates",
        author = "Enrico Dandolo and Gottfried von Strassburg",
        year = 1202,
        summary = "Venice's grosso of 1202 came eight years before Gottfried's Tristan of about 1210, and a one-line program counts the years.",
        details = "Both dates are given as commonly stated, the coin as 1202 and the poem as about 1210. Because one of the dates is an estimate, the answer is an estimate too, and the program prints it as a whole number of years.",
        instructions = """
            1. Save the example as grosso_tristan.py.
            2. Run it: python3 grosso_tristan.py
        """,
        example = """
            print("years from 1202 to 1210:", 1210 - 1202)
        """,
    ),

)
