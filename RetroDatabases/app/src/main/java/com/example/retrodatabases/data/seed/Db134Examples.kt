package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One example of 700 to 710: the ten years of building the Umayyad Great Mosque of Damascus, counted in code. */
val db134Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 134,
        kind = Kind.EXAMPLE,
        section = "Umayyad",
        title = "The Umayyad Great Mosque of Damascus, built from 705 to 715, with its ten years of building counted in code",
        originalTitle = "al-Jami al-Umawi",
        author = "al-Walid I",
        year = 705,
        summary = "The Umayyad Great Mosque of Damascus was begun in 705 under al-Walid I, and a short program counts the ten years of its building and how many fall inside this decade.",
        details = "The work is usually dated from 705 to 715, and the program uses those commonly given dates for the start and the finish, so five of the ten years fall inside this decade.",
        instructions = """
            1. Save the example as mosque_years.py.
            2. Run it: python3 mosque_years.py
        """,
        example = """
            BUILD_START = 705  # work begun under al-Walid I, as commonly dated
            BUILD_END = 715    # the building finished, as commonly dated
            CUT = 710          # the end of this decade of the 700s
            print("years of building:", BUILD_END - BUILD_START)
            print("years of building up to 710:", CUT - BUILD_START)
            print("years of building after 710:", BUILD_END - CUT)
        """,
    ),

)
