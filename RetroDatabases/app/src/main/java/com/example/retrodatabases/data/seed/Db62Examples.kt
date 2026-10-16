package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1420 to 1430: herringbone brick counts, apparent size in perspective, a ring of carts, the lunar and solar years, a rota of scholars and the compass points of a portolan chart. */
val db62Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 62,
        kind = Kind.EXAMPLE,
        section = "Brickwork",
        title = "Counting the bricks of herringbone courses",
        originalTitle = "Spina di pesce",
        author = "Builders of the dome of Florence",
        year = 1420,
        summary = "A herringbone course of n pairs of bricks uses 2n bricks, so four such courses use 8n bricks, and a short program prints the count for three sizes.",
        details = "The dome of Florence was begun in 1420 with herringbone courses in its masonry. The counts here are illustrative pair numbers chosen for the example, not figures from the cathedral's accounts.",
        instructions = """
            1. Save the example as herringbone_count.py.
            2. Run it: python3 herringbone_count.py
        """,
        example = """
            COURSES = 4

            def bricks(pairs_per_course):
                # Each pair of bricks makes one herringbone unit, so a course of n pairs has 2n bricks.
                return 2 * pairs_per_course * COURSES

            for pairs in (12, 20, 30):
                print(f"{pairs} pairs per course: {bricks(pairs)} bricks in four courses")
        """,
    ),

    entry(
        databaseId = 62,
        kind = Kind.EXAMPLE,
        section = "Perspective",
        title = "How a tile recedes in perspective",
        originalTitle = "Trinita",
        author = "Masaccio and the Florentine painters",
        year = 1427,
        summary = "In linear perspective a tile of fixed size looks as wide as one over its distance from the eye, so a short program prints the apparent width of five tiles.",
        details = "Masaccio's Trinity of about 1427 uses one vanishing point for its receding forms. The example uses the simple rule that apparent width falls with distance, and it prints exact fractions for five depths.",
        instructions = """
            1. Save the example as tile_perspective.py.
            2. Run it: python3 tile_perspective.py
        """,
        example = """
            from fractions import Fraction

            for depth in range(1, 6):
                # A tile of unit width at distance d looks 1/d as wide as it is.
                print(f"tile at depth {depth} appears {Fraction(1, depth)} of its true width")
        """,
    ),

    entry(
        databaseId = 62,
        kind = Kind.EXAMPLE,
        section = "Field fortifications",
        title = "Counting the carts on the ring of a wagon fort",
        originalTitle = "Wagenburg",
        author = "Hussite field commanders",
        year = 1420,
        summary = "A rectangular laager of a by b wagons has a border ring of wagons, and a short program counts the wagons on that ring for three sizes.",
        details = "The Hussite wagon fort of about 1420 was a ring of carts. The example counts the border of a rectangle of wagons with a loop, and the sizes are illustrative, not the sizes of any battle.",
        instructions = """
            1. Save the example as wagon_ring.py.
            2. Run it: python3 wagon_ring.py
        """,
        example = """
            def ring(a, b):
                # Count the wagons on the border of an a by b rectangle of wagons.
                return sum(1 for r in range(a) for c in range(b) if r in (0, a - 1) or c in (0, b - 1))

            for a, b in [(4, 3), (6, 5), (10, 4)]:
                print(f"a {a} by {b} laager: {ring(a, b)} wagons on the ring")
        """,
    ),

    entry(
        databaseId = 62,
        kind = Kind.EXAMPLE,
        section = "Calendars",
        title = "Nineteen years against 235 lunar months",
        originalTitle = "Ciclo di diciannove anni",
        author = "Calendar makers of the Middle Ages",
        year = 1424,
        summary = "Nineteen Julian years hold about 6940 days, while 235 lunar months hold about 6939.7 days, a short program prints both and their difference.",
        details = "The nineteen-year cycle was used to bring the lunar months and the solar year into step. The example uses the mean length of the lunar month, 29.530588 days, and it prints the values rounded to one decimal place.",
        instructions = """
            1. Save the example as nineteen_years.py.
            2. Run it: python3 nineteen_years.py
        """,
        example = """
            MONTH = 29.530588             # mean length of the lunar month in days
            julian_days = 19 * 365 + 5    # nineteen years with five leap days
            lunar_days = 235 * MONTH      # 235 lunar months make the same span
            print(f"19 Julian years: {julian_days} days")
            print(f"235 lunations: {round(lunar_days, 1)} days")
            print(f"difference: {round(julian_days - lunar_days, 1)} days")
        """,
    ),

    entry(
        databaseId = 62,
        kind = Kind.EXAMPLE,
        section = "Rotas",
        title = "A weekly rota of twelve scholars",
        originalTitle = "Turno dei dotti",
        author = "The scholars of the royal hall",
        year = 1420,
        summary = "Twelve scholars can be arranged in four groups of three that take turns week by week, and a short program prints the groups on duty for five weeks.",
        details = "A rota of this kind spreads the duties of a learned hall evenly over the weeks. The example uses twelve scholars and four groups, and it prints the first five weeks in order.",
        instructions = """
            1. Save the example as scholar_rota.py.
            2. Run it: python3 scholar_rota.py
        """,
        example = """
            groups = [[1, 2, 3], [4, 5, 6], [7, 8, 9], [10, 11, 12]]

            for week in range(1, 6):
                # The four groups take turns in order, and the rota starts again after the fourth.
                print(f"week {week}: scholars {groups[(week - 1) % 4]}")
        """,
    ),

    entry(
        databaseId = 62,
        kind = Kind.EXAMPLE,
        section = "Number theory",
        title = "The divisors of the year 1420",
        originalTitle = "Divisores",
        author = "Medieval arithmetic teachers",
        year = 1420,
        summary = "The divisors of 1420 are the numbers that divide it exactly, and a short program lists them in order.",
        details = "The year 1420 has twelve divisors because it factors as 2 squared times 5 times 71, and the example lists them with a simple loop. The count is exact, so the output never changes.",
        instructions = """
            1. Save the example as divisors.py.
            2. Run it: python3 divisors.py
        """,
        example = """
            number = 1420
            divisors = [d for d in range(1, number + 1) if number % d == 0]
            print(f"divisors of {number}: {divisors}")
        """,
    ),

)
