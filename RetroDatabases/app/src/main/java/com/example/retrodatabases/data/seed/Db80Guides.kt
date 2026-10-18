package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1240 to 1250: the celestial element's rectangle, written as a short program. */
val db80Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 80,
        kind = Kind.GUIDE,
        section = "Algebra",
        title = "How the celestial element finds the sides of a rectangle",
        originalTitle = "Tian yuan",
        author = "Li Ye",
        year = 1248,
        summary = "The celestial element stands for the short side of a rectangle, and a short program finds both sides from the area and the difference between them.",
        details = "With the short side x and the long side x plus d, the area x times (x plus d) gives a quadratic, and the example takes its positive root with a whole-number square root. Li Ye's book solves such problems by its own procedures, so the example is a modern check on illustrative areas.",
        instructions = """
            1. Save the example as rectangle.py.
            2. Run it: python3 rectangle.py
        """,
        example = """
            from math import isqrt

            def sides(area, difference):
                # the celestial element x is the short side, and the long side is x + difference
                # x * (x + difference) = area, so root * root = difference * difference + 4 * area
                root = isqrt(difference * difference + 4 * area)
                assert root * root == difference * difference + 4 * area
                short = (root - difference) // 2
                return short, short + difference

            for area, difference in [(35, 2), (60, 4), (12, 1)]:
                short, long = sides(area, difference)
                assert short * long == area
                print("area", area, "difference", difference, "sides", short, long)
        """,
    ),

)
