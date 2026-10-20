package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 249: a count of the years from the Gaoping coup to the usual start of the Jin, computed in code. */
val db180Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 180,
        kind = Kind.EXAMPLE,
        section = "Wei and Jin",
        title = "Years from the Gaoping coup of 249 to the usual start of the Jin in 266",
        originalTitle = "Years between two dated events",
        author = "Chroniclers of the Jin",
        year = 249,
        summary = "The Sima family held power in the Wei state from the coup of 249 until the Jin dynasty, which the usual lists date to 266, and a short program counts the years between the two.",
        details = "Both years are given as commonly stated, and the count is the plain difference between them, which is 17 whole years. The Jin start of 266 is the usual date in the lists, so the count depends on that date and says nothing about the family's strength.",
        instructions = """
            1. Save the example as jin_count.py.
            2. Run it: python3 jin_count.py
        """,
        example = """
            COUP = 249       # Sima Yi's seizure of power at the Gaoping Tombs, as commonly given
            JIN_START = 266  # the usual start of the Jin dynasty
            print("years from the coup to the start of the Jin:", JIN_START - COUP)
        """,
        rare = true,
    ),

)
