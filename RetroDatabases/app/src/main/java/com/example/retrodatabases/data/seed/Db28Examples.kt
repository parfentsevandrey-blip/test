package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1760 to 1770: latent heat, close pairs of stars, a least-deviation line, four squares, compound interest and a ship's run. */
val db28Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 28,
        kind = Kind.EXAMPLE,
        section = "Heat",
        title = "Black's latent heat in an ice and water mixture",
        originalTitle = "Black's latent heat in an ice and water mixture",
        author = "Joseph Black",
        year = 1761,
        summary = "Heat taken in by melting ice does not change its temperature, so the program balances the heat lost by water against the ice it melts, with an illustrative latent heat.",
        details = "Black announced in his lectures of 1761 that melting ice takes in heat without a rise in temperature, which he called latent heat. The example balances the heat that 100 grams of water gives up as it cools from 80 degrees to 0 against the ice it melts, using an illustrative latent heat of 80 units rather than any historical figure. It then finds how much ice a smaller heat would melt.",
        instructions = """
            1. Save the example as latent_heat.py.
            2. Run it: python3 latent_heat.py
        """,
        example = """
            from fractions import Fraction as F

            LATENT = 80          # heat units taken in to melt one gram of ice (illustrative, not a historical figure)
            water_mass = 100     # grams of water
            start, end = 80, 0   # the water cools from 80 degrees to 0

            heat_given = water_mass * (start - end)   # heat the water gives up, in units of gram degrees
            ice_melted = F(heat_given, LATENT)        # grams of ice that this heat can melt
            print("heat given up by the water:", heat_given)
            print("grams of ice melted:", ice_melted)
            print("grams melted by a heat of 2000:", F(2000, LATENT))
        """,
    ),

    entry(
        databaseId = 28,
        kind = Kind.EXAMPLE,
        section = "Astronomy",
        title = "Michell's chance of a close pair of stars",
        originalTitle = "An inquiry into the probable parallax, and magnitude of the fixed stars",
        author = "John Michell",
        year = 1767,
        summary = "On a sphere, the chance that a second random star falls within an angle of a first is (1 minus the cosine of the angle) divided by two, and the program evaluates it exactly.",
        details = "Michell argued in 1767 that close pairs and clusters of stars are unlikely to be chance alignments, and he tested this with a simple model of stars scattered at random over the sphere. The example uses the chance (1 minus the cosine of the angle) over two for a random star to fall within a given angle of another, with angles whose cosines are whole fractions, so each chance is exact. It also multiplies two of these chances for two independent pairs.",
        instructions = """
            1. Save the example as michell_pairs.py.
            2. Run it: python3 michell_pairs.py
        """,
        example = """
            from fractions import Fraction as F

            def chance_within(cos_angle):
                # Chance that a random star on the sphere lies within an angle of a given star: (1 - cos) / 2.
                return (1 - cos_angle) / 2

            angles = [("cos 4/5", F(4, 5)), ("cos 3/5", F(3, 5)), ("cos 12/13", F(12, 13))]
            for label, c in angles:
                print(f"{label}: chance {chance_within(c)}")
            print("two such pairs together:", chance_within(F(4, 5)) * chance_within(F(3, 5)))
        """,
    ),

    entry(
        databaseId = 28,
        kind = Kind.EXAMPLE,
        section = "Statistics",
        title = "Boscovich's least-deviation line through four points",
        originalTitle = "Boscovich's least-deviation line through four points",
        author = "Roger Boscovich",
        year = 1760,
        summary = "A straight line fitted to four points by making the sum of the vertical deviations as small as possible, found from the lines through pairs of points.",
        details = "Boscovich proposed fitting a line by least absolute deviations in his study of the Earth's shape, and accounts date the proposal to 1757 and the geometric procedure to 1760. The example checks every line through a pair of the four points, keeps the one with the smallest total deviation, and compares it with the least squares slope.",
        instructions = """
            1. Save the example as boscovich_line.py.
            2. Run it: python3 boscovich_line.py
        """,
        example = """
            from fractions import Fraction as F
            from itertools import combinations

            points = [(0, 0), (1, 1), (2, 1), (3, 3)]

            def deviation(a, b):
                # Sum of the absolute vertical deviations of all points from the line y = a + b x.
                return sum(abs(y - (a + b * x)) for x, y in points)

            best = None
            for (x1, y1), (x2, y2) in combinations(points, 2):
                b = F(y2 - y1, x2 - x1)          # slope of the line through the two points
                a = y1 - b * x1                  # intercept of that line
                total = deviation(a, b)
                if best is None or total < best[0]:
                    best = (total, a, b)
            total, a, b = best
            print(f"least deviation line: y = {a} + {b} x, total deviation {total}")
            n = len(points)
            xbar = F(sum(x for x, _ in points), n)
            ybar = F(sum(y for _, y in points), n)
            slope = sum((x - xbar) * (y - ybar) for x, y in points) / sum((x - xbar) ** 2 for x, _ in points)
            print(f"least squares slope for comparison: {slope}")
        """,
    ),

    entry(
        databaseId = 28,
        kind = Kind.EXAMPLE,
        section = "Number theory",
        title = "Lagrange's four squares for 7, 15 and 23",
        originalTitle = "Demonstration d'un theoreme d'arithmetique",
        author = "Joseph-Louis Lagrange",
        year = 1770,
        summary = "Every whole number is a sum of four squares, and the program finds the largest-first representation of three small numbers and checks each sum.",
        details = "Lagrange proved in 1770 that every whole number is a sum of four squares, a result that had been stated before him without proof. The example searches for the representation with the largest squares first, for 7, 15 and 23, and checks that the four squares add to each number.",
        instructions = """
            1. Save the example as four_squares.py.
            2. Run it: python3 four_squares.py
        """,
        example = """
            from math import isqrt

            def four_squares(n):
                # Find a >= b >= c >= d >= 0 with a^2 + b^2 + c^2 + d^2 = n, trying the largest values first.
                for a in range(isqrt(n), -1, -1):
                    for b in range(min(a, isqrt(n - a * a)), -1, -1):
                        for c in range(min(b, isqrt(n - a * a - b * b)), -1, -1):
                            rest = n - a * a - b * b - c * c
                            d = isqrt(rest)
                            if d <= c and d * d == rest:
                                return (a, b, c, d)
                return None

            for n in (7, 15, 23):
                t = four_squares(n)
                print(f"{n}: {t}, squares add to {sum(x * x for x in t)}")
        """,
    ),

    entry(
        databaseId = 28,
        kind = Kind.EXAMPLE,
        section = "Interest",
        title = "Compound interest at three per cent over three years",
        originalTitle = "Compound interest at three per cent over three years",
        author = "Interest tables of the period",
        year = 1762,
        summary = "Compound interest at 3 per cent multiplies a sum by 103/100 each year, and the program prints the factors for one to three years exactly.",
        details = "The Society for Equitable Assurances used 3 per cent interest in its early premium basis. Compounding multiplies a sum by 1.03 each year, which the example writes as the fraction 103/100 and raises to the powers one, two and three. It then applies the three-year factor to a sum of 1000.",
        instructions = """
            1. Save the example as compound_interest.py.
            2. Run it: python3 compound_interest.py
        """,
        example = """
            from fractions import Fraction as F

            rate = F(3, 100)
            for years in (1, 2, 3):
                factor = (1 + rate) ** years
                print(f"factor after {years} years: {factor}")
            print("1000 at three per cent for three years:", 1000 * (1 + rate) ** 3)
        """,
    ),

    entry(
        databaseId = 28,
        kind = Kind.EXAMPLE,
        section = "Navigation",
        title = "Average speed from a ship's daily log in the Gulf Stream",
        originalTitle = "Average speed from a ship's daily log in the Gulf Stream",
        author = "Benjamin Franklin and Timothy Folger",
        year = 1769,
        summary = "The average run of a ship over several days is the total of the daily runs divided by the number of days, and the program turns it into miles an hour.",
        details = "Franklin and Folger's chart of the Gulf Stream was drawn from the logs of ships' passages, and a daily log gives the distance run each day. The example uses three illustrative daily runs, not the entries of any real voyage, and computes the average run, the speed in miles an hour and the days needed for 600 miles.",
        instructions = """
            1. Save the example as ship_log.py.
            2. Run it: python3 ship_log.py
        """,
        example = """
            from fractions import Fraction as F

            daily_runs = [96, 120, 144]   # miles run on three days (illustrative values, not a real log)
            average_day = F(sum(daily_runs), len(daily_runs))
            print("average run per day:", average_day, "miles")
            print("average speed per hour:", average_day / 24, "miles")
            print("days to cover 600 miles at that speed:", F(600) / average_day)
        """,
    ),
)
