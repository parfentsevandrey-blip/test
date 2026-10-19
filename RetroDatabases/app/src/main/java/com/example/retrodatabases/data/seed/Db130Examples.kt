package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One example of 740 to 750: the Tang census of 742, its households and persons, with the average household size worked out by a short program. */
val db130Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 130,
        kind = Kind.EXAMPLE,
        section = "Tang",
        title = "The Tang census of 742, households and persons, with the average household size worked out in code",
        originalTitle = "Tianbao census returns",
        author = "The Tang government",
        year = 742,
        summary = "The Tang census of 742 is usually given as about 9.6 million households and 52.9 million people, and a short program works out the average household size.",
        details = "The census falls in the first year of the Tianbao era, and the totals usually quoted are 9,619,254 households and 52,880,488 people. Some historians argue that local officials distorted the returns to shift tax burdens, so the average is only as good as the figures.",
        rare = true,
        instructions = """
            1. Save the example as tang_census.py.
            2. Run it: python3 tang_census.py
        """,
        example = """
            households = 9619254
            people = 52880488
            print("households:", households)
            print("persons:", people)
            print("persons per household:", round(people / households, 2))
        """,
    ),

)
