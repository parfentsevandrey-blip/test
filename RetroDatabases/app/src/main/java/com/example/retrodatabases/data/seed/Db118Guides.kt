package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One guide of 860 to 870: how a Xiantong reign year becomes a calendar year, so that year 9 is 868. */
val db118Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 118,
        kind = Kind.GUIDE,
        section = "Dates",
        title = "How a Xiantong reign year becomes a calendar year, so that year 9 is 868",
        originalTitle = "Xiantong nianhao",
        author = "Tang court dating practice",
        year = 868,
        summary = "A year of the Xiantong era becomes a calendar year by adding it to the era's first year, 860, and a short program turns the colophon's ninth year into 868.",
        details = "Xiantong was the era name used under the Tang emperor Yizong, and its first year is 860, so the ninth year is 868, the year on the Diamond Sutra colophon. The rule is calendar year equals first year plus reign year minus one, and the example applies it to the ninth year and prints the result.",
        rare = true,
        instructions = """
            1. Save the example as xiantong_year.py.
            2. Run it: python3 xiantong_year.py
        """,
        example = """
            ERA_FIRST_YEAR = 860   # the first year of the Xiantong era
            REIGN_YEAR = 9         # the ninth year, as written on the colophon
            print("calendar year:", ERA_FIRST_YEAR + REIGN_YEAR - 1)
        """,
    ),

)
