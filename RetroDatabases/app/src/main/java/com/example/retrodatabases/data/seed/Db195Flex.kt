package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 90 to 100: one line of arithmetic from Quintilian's textbook of about 95 to Tacitus's Agricola of 98. */
val db195Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 195,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Years from Quintilian's textbook to Tacitus's Agricola",
        originalTitle = "Years between two books",
        author = "Quintilian and Tacitus",
        year = 98,
        summary = "Quintilian's Institutio oratoria is dated to about 95 and Tacitus's Agricola to 98, and a short program counts the three years between them.",
        details = "Both dates are the usual ones, so the answer is only as exact as they are, and the three years are a rough figure because the Institutio is dated to about 95. The example subtracts the two dates and prints the result, which can be checked by hand.",
        instructions = """
            1. Save the example as quintilian_agricola.py.
            2. Run it: python3 quintilian_agricola.py
        """,
        example = """
            quintilian = 95   # Institutio oratoria, dated about 95 as usual
            agricola = 98     # Agricola, dated 98 as usual
            print("years from Quintilian's textbook to Tacitus's Agricola:", agricola - quintilian)
        """,
    ),

)
