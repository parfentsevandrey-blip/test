package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 900 to 910: the Tang dynasty's years from 618 to 907, and the years of Later Baekje and Former Shu, counted in code. */
val db114Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 114,
        kind = Kind.EXAMPLE,
        section = "China",
        title = "The Tang dynasty's years from its founding in 618 to its fall in 907",
        originalTitle = "Tang dynasty",
        author = "Li Yuan, Emperor Gaozu of Tang",
        year = 907,
        summary = "The Tang dynasty ran from its founding in 618 to its fall in 907, a span of 289 years, and a short program counts the span.",
        details = "Li Yuan took the throne in 618, and the dynasty ended in 907, when Zhu Wen founded his own state in its place. The example subtracts the two years and prints the span, so the result can be worked out by hand.",
        instructions = """
            1. Save the example as tang_years.py.
            2. Run it: python3 tang_years.py
        """,
        example = """
            FOUNDED = 618   # the founding of the Tang dynasty, as commonly given
            FELL = 907      # the fall of the Tang dynasty, as commonly given
            print("years the Tang lasted:", FELL - FOUNDED)
            print("years from the founding to 900:", 900 - FOUNDED)
        """,
    ),

    entry(
        databaseId = 114,
        kind = Kind.EXAMPLE,
        section = "Korea",
        title = "The years of Later Baekje from its proclamation in 900 to its fall in 936",
        originalTitle = "Hubaekje",
        author = "Gyeon Hwon and Wang Geon",
        year = 900,
        summary = "Later Baekje, proclaimed in 900 and ended in 936, lasted 36 years on the dates usually given, and a short program counts them.",
        details = "The start is 900, the year of the proclamation, but some accounts begin the state at 892, which would make the span 44 years. The end in 936 is the year the kingdom was taken over by Goryeo, and the example prints both spans.",
        instructions = """
            1. Save the example as baekje_years.py.
            2. Run it: python3 baekje_years.py
        """,
        example = """
            PROCLAIMED = 900    # Gyeon Hwon's proclamation, as commonly given
            FELL = 936          # Later Baekje taken over by Goryeo, as commonly given
            EARLY_START = 892   # the earlier start that some accounts give
            print("years from 900 to the fall:", FELL - PROCLAIMED)
            print("years from 892 to the fall:", FELL - EARLY_START)
        """,
    ),

    entry(
        databaseId = 114,
        kind = Kind.EXAMPLE,
        section = "China",
        title = "The years of Former Shu from its founding in 907 to its fall in 925",
        originalTitle = "Qian Shu",
        author = "Wang Jian",
        year = 907,
        summary = "Former Shu, founded in 907 and conquered in 925, lasted 18 years, and a short program counts them.",
        details = "Wang Jian set up the state at Chengdu in 907, and the Later Tang conquered it in 925. The example prints the span from the founding and the span from the start of the decade.",
        instructions = """
            1. Save the example as former_shu_years.py.
            2. Run it: python3 former_shu_years.py
        """,
        example = """
            FOUNDED = 907     # Wang Jian's founding of Former Shu, as commonly given
            CONQUERED = 925   # conquest by the Later Tang, as commonly given
            print("years Former Shu lasted:", CONQUERED - FOUNDED)
            print("years from 900 to the conquest:", CONQUERED - 900)
        """,
    ),

)
