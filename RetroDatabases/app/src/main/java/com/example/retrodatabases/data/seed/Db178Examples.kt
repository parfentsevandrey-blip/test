package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 263: Liu Hui's doubling of an inscribed polygon up to 3,072 sides, read as the value of pi. */
val db178Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 178,
        kind = Kind.EXAMPLE,
        section = "Circles",
        title = "Liu Hui's polygon of 3,072 sides, doubled from a hexagon for the value of pi",
        originalTitle = "Gehuan shu",
        author = "Liu Hui",
        year = 263,
        summary = "Liu Hui doubled an inscribed polygon from six sides to 3,072 and read its perimeter as the value of pi, arriving at 3.1416.",
        details = "His commentary, dated to 263, gives 3.14 from the 96-sided polygon and 3.1416 from the 3,072-sided one. The example repeats the doubling with square roots and prints both values to four places, and this record is about pi as a value, not about the commentary as a book.",
        instructions = """
            1. Save the example as pi_polygon.py.
            2. Run it: python3 pi_polygon.py
        """,
        example = """
            import math

            # A regular hexagon inscribed in a circle of radius 1 has sides equal to the radius.
            sides = 6
            side = 1.0
            for step in range(1, 10):
                # the side of the polygon with twice as many sides: s2 = sqrt(2 - sqrt(4 - s * s))
                side = math.sqrt(2 - math.sqrt(4 - side * side))
                sides *= 2
                if sides in (96, 3072):
                    print(sides, "sides: pi about", f"{sides * side / 2:.4f}")
        """,
    ),

)
