package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1273 to 1278: days between the deaths of Rumi and Aquinas, the election of Rudolf and the Marchfeld, and Aquinas and the Paris condemnation. */
val db77Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 77,
        kind = Kind.FLEX,
        section = "Deaths",
        title = "Rumi's death, then Aquinas's, counted in days",
        originalTitle = "Days between two deaths",
        author = "Rumi and Thomas Aquinas",
        year = 1274,
        summary = "Rumi died at Konya on 17 December 1273 and Thomas Aquinas on 7 March 1274, and a short program counts the days between the two deaths.",
        details = "Both dates are given as commonly stated, and the example uses fixed dates in the Python calendar, so the count is the same on every run. The span crosses the turn of the year and runs through February 1274, which has no leap day.",
        instructions = """
            1. Save the example as rumi_aquinas.py.
            2. Run it: python3 rumi_aquinas.py
        """,
        example = """
            from datetime import date

            rumi = date(1273, 12, 17)      # Rumi's death at Konya, as commonly given
            aquinas = date(1274, 3, 7)     # Aquinas's death at Fossanova, as commonly given
            print("days from Rumi's death to Aquinas's death:", (aquinas - rumi).days)
        """,
    ),

    entry(
        databaseId = 77,
        kind = Kind.FLEX,
        section = "Elections",
        title = "The election at Frankfurt to the Marchfeld, in days",
        originalTitle = "Days between two events",
        author = "Rudolf of Habsburg and Ottokar II of Bohemia",
        year = 1278,
        summary = "Rudolf of Habsburg was elected king at Frankfurt on 1 October 1273, and his victory at the Marchfeld came on 26 August 1278, and a short program counts the days between them.",
        details = "Both dates are given as commonly stated, and the example uses fixed dates in the Python calendar, so the count stays the same on every run. The span runs over five years and takes in the leap day of 1276.",
        instructions = """
            1. Save the example as marchfeld.py.
            2. Run it: python3 marchfeld.py
        """,
        example = """
            from datetime import date

            rudolf = date(1273, 10, 1)      # Rudolf's election at Frankfurt, as commonly given
            marchfeld = date(1278, 8, 26)   # the battle of the Marchfeld, as commonly given
            print("days from the election to the battle:", (marchfeld - rudolf).days)
        """,
    ),

    entry(
        databaseId = 77,
        kind = Kind.FLEX,
        section = "Teaching",
        title = "From Aquinas to the Paris condemnation, counted in days",
        originalTitle = "Days between two dated events",
        author = "Thomas Aquinas and Stephen Tempier",
        year = 1277,
        summary = "Thomas Aquinas died on 7 March 1274 and the Paris condemnation was issued on 7 March 1277, and a short program counts the days between the two dates.",
        details = "The two dates fall on the same day of the year, so the count is a whole number of years, and it takes in the leap day of 29 February 1276. The example uses fixed dates in the Python calendar, so the count is the same on every run.",
        instructions = """
            1. Save the example as condemnation.py.
            2. Run it: python3 condemnation.py
        """,
        example = """
            from datetime import date

            aquinas = date(1274, 3, 7)        # Aquinas's death, as commonly given
            condemnation = date(1277, 3, 7)   # the Paris condemnation, as commonly given
            print("days from Aquinas's death to the condemnation:", (condemnation - aquinas).days)
        """,
    ),

)
