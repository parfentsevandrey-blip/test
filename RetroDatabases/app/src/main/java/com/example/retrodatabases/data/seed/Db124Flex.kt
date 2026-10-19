package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 800 to 810: the years from the fall of the Barmakids to the death of Harun al-Rashid, and from Nikephoros I's accession to the Kusuko incident. */
val db124Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 124,
        kind = Kind.FLEX,
        section = "Years",
        title = "Years from the fall of the Barmakids in 803 to the death of Harun al-Rashid in 809",
        originalTitle = "Years between two Abbasid events",
        author = "Harun al-Rashid",
        year = 803,
        summary = "The fall of the Barmakids came in 803 and Harun al-Rashid died in 809, and a short program counts the six years between the two events.",
        details = "Harun died at Tus in Khurasan in 809, while on a campaign in that province, and the count uses the usual dates of both events. The span is an arithmetic comparison of two dates, not a claim about how the two events were linked.",
        instructions = """
            1. Save the example as barmakids_harun.py.
            2. Run it: python3 barmakids_harun.py
        """,
        example = """
            BARMAKIDS = 803    # the fall of the Barmakid family, as commonly dated
            HARUN_DEATH = 809  # the death of Harun al-Rashid at Tus, as commonly dated
            print("years from the fall of the Barmakids to the death of Harun:", HARUN_DEATH - BARMAKIDS)
        """,
    ),

    entry(
        databaseId = 124,
        kind = Kind.FLEX,
        section = "Years",
        title = "Years from Nikephoros I's accession in 802 to the Kusuko incident in 810",
        originalTitle = "Years between two unrelated events",
        author = "Nikephoros I",
        year = 802,
        summary = "Nikephoros I took the Byzantine throne in 802 and the Kusuko incident came in 810, and a short program counts the eight years between the two dates.",
        details = "The two events had no connection, and the span only compares their usual dates, one in Constantinople and one in Japan. The program prints the difference, which a reader can check against the two dates.",
        instructions = """
            1. Save the example as nikephoros_kusuko.py.
            2. Run it: python3 nikephoros_kusuko.py
        """,
        example = """
            NIKEPHOROS = 802  # Nikephoros I takes the Byzantine throne, as commonly dated
            KUSUKO = 810      # the Kusuko incident, as commonly dated
            print("years from Nikephoros I to the Kusuko incident:", KUSUKO - NIKEPHOROS)
        """,
    ),

)
