package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 249: the years from the death of Gordian III to the seizure of power by Sima Yi. */
val db180Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 180,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Years from the death of Gordian III to Sima Yi's coup",
        originalTitle = "Years between two events",
        author = "Roman and Wei court chroniclers",
        year = 249,
        summary = "Gordian III died in 244 and Sima Yi seized power in 249, and a short program counts the years between the two events.",
        details = "Both years are the commonly given ones, and the example takes their plain difference, which is a count in whole years. It uses only the two years, so it says nothing about the months in which either event happened.",
        instructions = """
            1. Save the example as gordian_sima.py.
            2. Run it: python3 gordian_sima.py
        """,
        example = """
            gordian_death = 244   # death of Gordian III near Misiche, as commonly given
            sima_coup = 249       # Sima Yi's seizure of power at the Gaoping Tombs, as commonly given
            print("years from Gordian's death to Sima Yi's coup:", sima_coup - gordian_death)
        """,
    ),

)
