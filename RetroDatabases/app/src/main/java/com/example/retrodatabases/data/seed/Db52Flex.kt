package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1520 to 1530: the days of Duerer's life, the days from Luther's theses to the Edict of Worms, and the days of the decade. */
val db52Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 52,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days in Duerer's life, from birth to death",
        originalTitle = "Days in Duerer's life, from birth to death",
        author = "Albrecht Duerer",
        year = 1528,
        summary = "Duerer was born on 21 May 1471 and died on 6 April 1528, and a short program counts the days of his life and his age in whole years.",
        details = "The birth date and the death date are the ones commonly given for Duerer at Nuremberg, and the death came in the year his canon of proportion was printed. The example uses Python's calendar arithmetic with fixed dates, so the count of days never changes.",
        instructions = """
            1. Save the example as duerer_days.py.
            2. Run it: python3 duerer_days.py
        """,
        example = """
            from datetime import date

            born = date(1471, 5, 21)     # Duerer's birth at Nuremberg, as commonly given
            died = date(1528, 4, 6)      # his death at Nuremberg, as commonly given
            years = died.year - born.year - ((died.month, died.day) < (born.month, born.day))
            print("days alive:", (died - born).days)
            print("age in whole years:", years)
        """,
    ),

    entry(
        databaseId = 52,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from Luther's theses to the Edict of Worms",
        originalTitle = "Days from Luther's theses to the Edict of Worms",
        author = "Martin Luther and Charles V",
        year = 1521,
        summary = "Luther's ninety-five theses of 31 October 1517 came four years before the Edict of Worms of 8 May 1521, and a short program counts the days between them.",
        details = "The theses are traditionally dated 31 October 1517, and the edict is dated at Worms on 8 May 1521, which are the dates commonly given for both. The example counts the days with fixed dates and splits the total into whole weeks and the days left over.",
        instructions = """
            1. Save the example as theses_days.py.
            2. Run it: python3 theses_days.py
        """,
        example = """
            from datetime import date

            theses = date(1517, 10, 31)   # the traditional date of the ninety-five theses
            edict = date(1521, 5, 8)      # the Edict of Worms, dated at Worms
            days = (edict - theses).days
            print("days from the theses to the edict:", days)
            print("weeks and days:", divmod(days, 7))
        """,
    ),

    entry(
        databaseId = 52,
        kind = Kind.FLEX,
        section = "Weights",
        title = "Grains in a troy pound, by its parts",
        originalTitle = "Grains in a troy pound, by its parts",
        author = "Troy weight, as used for silver and gold",
        year = 1526,
        summary = "A troy pound holds 5760 grains, counted from its parts of twelve ounces, twenty pennyweights and twenty-four grains, and a short program prints the three counts.",
        details = "Troy weight was the standard for precious metals in England in the 1500s, and the pound was split into twelve ounces, each ounce into twenty pennyweights and each pennyweight into twenty-four grains. The example multiplies these parts in order, so the counts are whole numbers that never change.",
        instructions = """
            1. Save the example as troy_grains.py.
            2. Run it: python3 troy_grains.py
        """,
        example = """
            GRAINS_PER_PENNYWEIGHT = 24
            PENNYWEIGHTS_PER_OUNCE = 20
            OUNCES_PER_POUND = 12
            grains_per_ounce = GRAINS_PER_PENNYWEIGHT * PENNYWEIGHTS_PER_OUNCE
            grains_per_pound = grains_per_ounce * OUNCES_PER_POUND
            print("grains in an ounce:", grains_per_ounce)
            print("grains in a pound:", grains_per_pound)
            print("pennyweights in a pound:", PENNYWEIGHTS_PER_OUNCE * OUNCES_PER_POUND)
        """,
    ),

)
