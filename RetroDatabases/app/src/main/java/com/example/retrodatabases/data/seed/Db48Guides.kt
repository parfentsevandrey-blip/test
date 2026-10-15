package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1560 to 1570: Julian and Gregorian dates of 1564, a model Porta table, a planetary conjunction, Mercator's stretch and linear equations. */
val db48Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 48,
        kind = Kind.GUIDE,
        section = "Calendars",
        title = "How a Julian date becomes a Gregorian date in 1564",
        originalTitle = "Julian calendar",
        author = "Julius Caesar",
        year = 1564,
        summary = "Dates in 1564 under the Julian calendar were ten days behind Gregorian dates, and a short program converts two famous dates of that year.",
        details = "Italy and most of Catholic Europe still used the Julian calendar in 1564, before the Gregorian reform of 1582. The example converts Galileo's birth on 15 February 1564 and Michelangelo's death on 18 February 1564, both as commonly given in the Julian calendar, into Gregorian dates by day numbers.",
        instructions = """
            1. Save the example as julian_1564.py.
            2. Run it: python3 julian_1564.py
        """,
        example = """
            from datetime import date

            def julian_day(year, month, day):
                # Day number of a date in the Julian calendar.
                a = (14 - month) // 12
                y = year + 4800 - a
                m = month + 12 * a - 3
                return day + (153 * m + 2) // 5 + 365 * y + y // 4 - 32083

            def gregorian_from_day(number):
                # The Gregorian date for a day number.
                a = number + 32044
                b = (4 * a + 3) // 146097
                c = a - 146097 * b // 4
                d = (4 * c + 3) // 1461
                e = c - 1461 * d // 4
                m = (5 * e + 2) // 153
                day = e - (153 * m + 2) // 5 + 1
                month = m + 3 - 12 * (m // 10)
                year = 100 * b + d - 4800 + m // 10
                return year, month, day

            for julian in ((1564, 2, 15), (1564, 2, 18)):
                year, month, day = gregorian_from_day(julian_day(*julian))
                shift = (date(year, month, day) - date(1564, julian[1], julian[2])).days
                print(f"Julian {julian[0]}-{julian[1]:02d}-{julian[2]:02d} is Gregorian {year}-{month:02d}-{day:02d}, shift {shift} days")
        """,
    ),

    entry(
        databaseId = 48,
        kind = Kind.GUIDE,
        section = "Ciphers",
        title = "How a key letter chooses one of thirteen Porta rows",
        originalTitle = "De furtivis literarum notis",
        author = "Giambattista della Porta",
        year = 1563,
        summary = "A key letter chooses one of thirteen rows, and the guide builds three rows of a model table in the spirit of Porta's cipher.",
        details = "Porta's book of 1563 described a table of paired letters, and this model is a simplified version rather than the book's own table. The example prints three rows and checks that applying a row twice gives back the plain letters.",
        instructions = """
            1. Save the example as porta_rows.py.
            2. Run it: python3 porta_rows.py
        """,
        example = """
            ALPHABET = "abcdefghijklmnopqrstuvwxyz"

            def encode(letter, row):
                # The first half of the alphabet moves into the second half, shifted by the row; the second half moves back.
                x = ALPHABET.index(letter)
                if x < 13:
                    return ALPHABET[13 + (x + row) % 13]
                return ALPHABET[(x - 13 - row) % 13]

            for key in ("a", "c", "m"):
                row = ALPHABET.index(key) % 13
                table = "".join(encode(letter, row) for letter in ALPHABET)
                reciprocal = all(encode(encode(letter, row), row) == letter for letter in ALPHABET)
                print(f"key {key}: {table}, reciprocal: {'yes' if reciprocal else 'no'}")
        """,
    ),

    entry(
        databaseId = 48,
        kind = Kind.GUIDE,
        section = "Astronomy",
        title = "How two planets' periods give the interval between conjunctions",
        originalTitle = "Conjunction of Jupiter and Saturn",
        author = "Tycho Brahe",
        year = 1563,
        summary = "Two planets with periods of a and b years meet again after 1 divided by the difference of their yearly rates, and a short program works this out for three pairs of periods.",
        details = "Tycho Brahe, then a student, recorded a conjunction of Jupiter and Saturn in 1563, which is one of his earliest recorded observations. The example uses round illustrative periods of twelve and thirty years, which give a conjunction every twenty years, close to the real interval of about twenty years.",
        instructions = """
            1. Save the example as conjunctions.py.
            2. Run it: python3 conjunctions.py
        """,
        example = """
            from fractions import Fraction as F

            def conjunction_interval(period_a, period_b):
                # The faster planet gains one full lap on the slower one every 1 / (1/a - 1/b) years.
                return 1 / abs(F(1, period_a) - F(1, period_b))

            for a, b in ((12, 30), (4, 6), (12, 24)):
                print(f"periods {a} and {b} years: conjunction every {conjunction_interval(a, b)} years")
        """,
    ),

    entry(
        databaseId = 48,
        kind = Kind.GUIDE,
        section = "Navigation",
        title = "How Mercator's projection stretches the latitudes",
        originalTitle = "Nova et aucta orbis terrae descriptio ad usum navigantium emendate accommodata",
        author = "Gerardus Mercator",
        year = 1569,
        summary = "On Mercator's projection the stretch of a parallel is the secant of its latitude, and the height of a latitude is the inverse hyperbolic tangent of its sine.",
        details = "Mercator's map of 1569 did not state these formulas, which were worked out later, and the rule is usually credited to Edward Wright in 1599. The example prints the stretch and the height for three latitudes, with the height measured in radians of the equator scale.",
        instructions = """
            1. Save the example as mercator_stretch.py.
            2. Run it: python3 mercator_stretch.py
        """,
        example = """
            import math

            for latitude in (30, 45, 60):
                phi = math.radians(latitude)
                stretch = 1 / math.cos(phi)
                height = math.atanh(math.sin(phi))
                print(f"latitude {latitude}: stretch {stretch:.3f}, height {height:.3f}")
        """,
    ),

    entry(
        databaseId = 48,
        kind = Kind.GUIDE,
        section = "Algebra",
        title = "How to solve a linear equation by balancing both sides",
        originalTitle = "Libro de algebra en arithmetica y geometria",
        author = "Pedro Nunes",
        year = 1567,
        summary = "Balancing both sides of an equation moves the unknown to one side, and a short program solves four linear equations in this way.",
        details = "Pedro Nunes's algebra of 1567 covers linear equations, and the solver here uses the same balancing step in exact fractions. The example prints four equations with their solutions, one of them a fraction.",
        instructions = """
            1. Save the example as balance_equations.py.
            2. Run it: python3 balance_equations.py
        """,
        example = """
            from fractions import Fraction as F

            def show(a, b):
                # Writes a linear term with its sign, such as 3x + 5 or 2x - 7.
                return f"{a}x + {b}" if b >= 0 else f"{a}x - {-b}"

            def solve(a, b, c, d):
                # Move the x terms to the left and the constants to the right: (a - c) x = d - b.
                return F(d - b, a - c)

            for a, b, c, d in ((3, 5, 0, 20), (2, -7, 3, -11), (2, 1, 0, 4), (5, 1, 2, -8)):
                left = show(a, b)
                right = str(d) if c == 0 else show(c, d)
                print(f"{left} = {right}: x = {solve(a, b, c, d)}")
        """,
    ),

)
