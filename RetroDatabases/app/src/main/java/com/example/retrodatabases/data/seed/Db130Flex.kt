package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 740 to 750: the three years from the Hirotsugu rebellion to the Great Buddha decree, and the 28 years of the Kaiyuan era. */
val db130Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 130,
        kind = Kind.FLEX,
        section = "Years",
        title = "Years from the Hirotsugu rebellion of 740 to the Great Buddha decree of 743",
        originalTitle = "Years between a rebellion and a decree",
        author = "Fujiwara no Hirotsugu",
        year = 740,
        summary = "Fujiwara no Hirotsugu rebelled in 740, and Emperor Shomu's Great Buddha decree came in 743, so a short program counts the three years between the two.",
        details = "The rebellion broke out in Kyushu in 740, and the decree was issued in 743, so the program uses the usual dates of both events. The span compares two dates only, and it does not claim that the decree was a response to the rebellion.",
        instructions = """
            1. Save the example as hirotsugu_buddha.py.
            2. Run it: python3 hirotsugu_buddha.py
        """,
        example = """
            REBELLION = 740  # Fujiwara no Hirotsugu's rebellion, as commonly dated
            DECREE = 743     # the decree for the Great Buddha, as commonly dated
            print("years from the Hirotsugu rebellion to the Great Buddha decree:", DECREE - REBELLION)
        """,
    ),

    entry(
        databaseId = 130,
        kind = Kind.FLEX,
        section = "Years",
        title = "Years from the first year of the Tang Kaiyuan era in 713 to its last year in 741",
        originalTitle = "Kaiyuan era, 713 to 741",
        author = "Emperor Xuanzong of Tang",
        year = 741,
        summary = "The Kaiyuan era of Tang China ran from 713 to 741, and a short program counts the 28 years between its first and last years.",
        details = "Xuanzong's era name Kaiyuan covered twenty-nine years by inclusive count, and the next era, Tianbao, began in 742, the year of the census. The program takes the two years as given and prints their difference, which is the span between them and not the count of years inclusive.",
        instructions = """
            1. Save the example as kaiyuan_span.py.
            2. Run it: python3 kaiyuan_span.py
        """,
        example = """
            KAIYUAN_FIRST = 713  # the first year of the Kaiyuan era, as commonly dated
            KAIYUAN_LAST = 741   # its last year, before Tianbao began in 742
            print("years from the first year of Kaiyuan to its last year:", KAIYUAN_LAST - KAIYUAN_FIRST)
        """,
    ),

)
