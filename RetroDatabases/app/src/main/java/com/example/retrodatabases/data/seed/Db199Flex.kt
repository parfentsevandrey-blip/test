package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 50 to 59: the years from the founding of Cologne to the murder of Agrippina. */
val db199Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 199,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Years from the founding of Cologne to the murder of Agrippina",
        originalTitle = "Years between two events",
        author = "Tacitus and Suetonius, the main Roman accounts",
        year = 59,
        summary = "Cologne was founded in 50 and Agrippina was murdered in 59, and a short program counts the nine years between the two events.",
        details = "Both years are given as commonly stated, and the example works with year figures only, so no day or calendar reckoning is needed. The answer is 9, which can be checked by hand from the two years.",
        instructions = """
            1. Save the example as cologne_agrippina.py.
            2. Run it: python3 cologne_agrippina.py
        """,
        example = """
            cologne = 50      # the founding of the colony at Cologne, as commonly given
            agrippina = 59    # the murder of Agrippina, as commonly given
            print("years from Cologne to Agrippina:", agrippina - cologne)
        """,
    ),

)
