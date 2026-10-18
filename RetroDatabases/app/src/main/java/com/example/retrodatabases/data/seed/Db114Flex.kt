package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 900 to 910: the nine years from Later Baekje's proclamation to the Fatimid founding, and the 29 years from Former Shu's founding to the fall of Later Baekje. */
val db114Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 114,
        kind = Kind.FLEX,
        section = "Years",
        title = "Years from Later Baekje's proclamation to the Fatimid founding",
        originalTitle = "Years between two founding dates",
        author = "Gyeon Hwon and Ubayd Allah al-Mahdi",
        year = 900,
        summary = "Later Baekje was proclaimed in 900 and the Fatimid caliphate began in 909, and a short program counts the nine years between the two dates.",
        details = "The Fatimid date is 909 by the usual dating, although some accounts give January 910 for the declaration, which would make the span ten years. The example uses the two years as commonly given and prints the difference.",
        instructions = """
            1. Save the example as founding_years.py.
            2. Run it: python3 founding_years.py
        """,
        example = """
            BAEKJE = 900    # Gyeon Hwon's proclamation, as commonly given
            FATIMID = 909   # the start of the Fatimid caliphate, as commonly given
            print("years from Later Baekje to the Fatimid founding:", FATIMID - BAEKJE)
        """,
    ),

    entry(
        databaseId = 114,
        kind = Kind.FLEX,
        section = "Years",
        title = "Years from the founding of Former Shu to the fall of Later Baekje",
        originalTitle = "Years between two state falls",
        author = "Wang Jian and Wang Geon",
        year = 907,
        summary = "Former Shu was founded in 907 and Later Baekje fell in 936, and a short program counts the 29 years between the two events.",
        details = "Both years are the usual dates, and the example prints the difference between them. The two events come from different states, so the span belongs to neither ruler alone.",
        instructions = """
            1. Save the example as former_shu_fall.py.
            2. Run it: python3 former_shu_fall.py
        """,
        example = """
            FORMER_SHU = 907    # Wang Jian's founding of Former Shu
            BAEKJE_FALL = 936   # Later Baekje taken over by Goryeo
            print("years from Former Shu to the fall of Later Baekje:", BAEKJE_FALL - FORMER_SHU)
        """,
    ),

)
