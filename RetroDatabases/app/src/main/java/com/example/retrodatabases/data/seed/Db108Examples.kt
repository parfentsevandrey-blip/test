package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 960 to 970: the years of Otto I's reign as king and as emperor, counted in code. */
val db108Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 108,
        kind = Kind.EXAMPLE,
        section = "Germany and Italy",
        title = "How many years Otto I ruled as king before his coronation as emperor, counted in code",
        originalTitle = "How many years Otto I ruled as king before his coronation as emperor, counted in code",
        author = "Otto I",
        year = 962,
        summary = "A short program counts the years between Otto I's crowning as king in 936 and his coronation as emperor in 962, and the years he ruled as emperor until 973.",
        details = "Otto I was crowned king at Aachen in 936, crowned emperor at Rome in 962, and died in 973. The count is in calendar years, so the program gives a year difference and not the exact time between the two events.",
        instructions = """
            1. Save the example as otto.py.
            2. Run it: python3 otto.py
        """,
        example = """
            king_year = 936        # crowning as king at Aachen, as commonly stated
            emperor_year = 962     # coronation as emperor at Rome, as commonly stated
            death_year = 973       # death of Otto I, as commonly stated
            print("years as king before the coronation:", emperor_year - king_year)
            print("years as emperor until his death:", death_year - emperor_year)
        """,
    ),

)
