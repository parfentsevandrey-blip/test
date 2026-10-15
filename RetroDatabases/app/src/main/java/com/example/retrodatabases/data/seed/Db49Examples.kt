package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1550 to 1560: a darkened room's image, a sorted index, a numbered register, Tartaglia's sums, a square of a sum and a short prayer. */
val db49Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 49,
        kind = Kind.EXAMPLE,
        section = "Arithmetic",
        title = "The diagonal of Tartaglia's array gives the triangular numbers",
        originalTitle = "General trattato di numeri et misure",
        author = "Niccolo Tartaglia",
        year = 1556,
        summary = "The entry at position two of each row of Tartaglia's array is a triangular number, and a program checks the rows against the formula n times n minus 1 over 2.",
        details = "The array of binomial coefficients appears in the second part of Tartaglia's General trattato of 1556, and the example builds it row by row. The formula gives the same numbers as the array, so the output checks the two against each other.",
        instructions = """
            1. Save the example as tartaglia_triangular.py.
            2. Run it: python3 tartaglia_triangular.py
        """,
        example = """
            row = [1]
            for n in range(8):
                if n >= 2:
                    print(f"row {n}: entry at position 2 is {row[2]}, and {n} times {n - 1} over 2 is {n * (n - 1) // 2}")
                row = [1] + [row[i] + row[i + 1] for i in range(len(row) - 1)] + [1]
        """,
    ),

    entry(
        databaseId = 49,
        kind = Kind.EXAMPLE,
        section = "Censorship",
        title = "Looking up a name in a sorted index, as a censor would",
        originalTitle = "Index librorum prohibitorum",
        author = "Pope Paul IV",
        year = 1559,
        summary = "A sorted list of names is searched by halving, which finds a name or shows where it would fall, and a short program does this for two queries.",
        details = "The Pauline Index of 1559 was a list that had to be consulted name by name, and the example shows the halving search that makes such a list quick to check. The six names are a short sorted list chosen for the example.",
        instructions = """
            1. Save the example as sorted_index.py.
            2. Run it: python3 sorted_index.py
        """,
        example = """
            from bisect import bisect_left

            names = ["Aretino", "Bembo", "Calvin", "Erasmus", "Luther", "Machiavelli"]
            for query in ("Erasmus", "Dante"):
                i = bisect_left(names, query)
                if i < len(names) and names[i] == query:
                    print(f"{query}: found at position {i}")
                else:
                    print(f"{query}: not found, would sit at position {i}")
        """,
    ),

    entry(
        databaseId = 49,
        kind = Kind.EXAMPLE,
        section = "Dates",
        title = "Intervals between three instruments of the 1550s",
        originalTitle = "Instrument dates of the 1550s",
        author = "Mercator, Schissler and Gemini",
        year = 1551,
        summary = "The intervals between Mercator's globe of 1551, Schissler's compendium of 1556 and Gemini's astrolabe of 1559 are found by subtraction, and a short program prints them.",
        details = "The example counts the years from Mercator's celestial globe of 1551 to Schissler's compendium of 1556, which is 5, and from the compendium to Gemini's astrolabe of 1559, which is 3. The dates are the ones given for the three instruments, so the output never changes.",
        instructions = """
            1. Save the example as instrument_years.py.
            2. Run it: python3 instrument_years.py
        """,
        example = """
            dates = [("Mercator's celestial globe", 1551), ("Schissler's compendium", 1556), ("Gemini's London astrolabe", 1559)]
            for (name_a, year_a), (name_b, year_b) in zip(dates, dates[1:]):
                print(f"from {name_a} to {name_b}: {year_b - year_a} years")
            print("from the first to the last:", dates[-1][1] - dates[0][1], "years")
        """,
    ),

    entry(
        databaseId = 49,
        kind = Kind.EXAMPLE,
        section = "Arithmetic",
        title = "Each row of Tartaglia's array sums to a power of two",
        originalTitle = "General trattato di numeri et misure",
        author = "Niccolo Tartaglia",
        year = 1556,
        summary = "The entries of each row of Tartaglia's array add up to a power of two, and a program checks the first six rows by adding them.",
        details = "The array of binomial coefficients appears in the second part of Tartaglia's General trattato of 1556, and the example builds each row from the one above. The sums 1, 2, 4, 8, 16 and 32 come out of the same addition that makes the rows.",
        instructions = """
            1. Save the example as tartaglia_sums.py.
            2. Run it: python3 tartaglia_sums.py
        """,
        example = """
            row = [1]
            for n in range(6):
                print(f"row {n} sums to {sum(row)}, which is 2 to the power {n}")
                row = [1] + [row[i] + row[i + 1] for i in range(len(row) - 1)] + [1]
        """,
    ),

    entry(
        databaseId = 49,
        kind = Kind.EXAMPLE,
        section = "Algebra",
        title = "Peletier's square of a sum, with two numbers",
        originalTitle = "L'Algebre",
        author = "Jacques Peletier du Mans",
        year = 1554,
        summary = "The square of a sum is the square of each part plus twice their product, which a short program checks for two pairs of numbers.",
        details = "Peletier's L'Algebre of 1554 taught the rules of algebra in French, and the square of a sum is one of the rules that a reader meets early. The two pairs of numbers in the example are chosen for the check, and both sums come out as a perfect square.",
        instructions = """
            1. Save the example as peletier_square.py.
            2. Run it: python3 peletier_square.py
        """,
        example = """
            for a, b in ((3, 4), (5, 3)):
                print(f"a={a} b={b}: {a * a} + {2 * a * b} + {b * b} = {(a + b) ** 2}")
        """,
    ),

    entry(
        databaseId = 49,
        kind = Kind.EXAMPLE,
        section = "Algebra",
        title = "Peletier's difference of two squares",
        originalTitle = "L'Algebre",
        author = "Jacques Peletier du Mans",
        year = 1554,
        summary = "The product of a sum and a difference of two numbers equals the difference of their squares, which a short program checks for two pairs of numbers.",
        details = "Peletier's L'Algebre of 1554 taught the rules of algebra in French, and the difference of two squares is one of the rules a reader meets early. The two pairs in the example are chosen for the check, and both sides of the identity come out equal.",
        instructions = """
            1. Save the example as peletier_difference.py.
            2. Run it: python3 peletier_difference.py
        """,
        example = """
            for a, b in ((7, 3), (9, 4)):
                print(f"a={a} b={b}: ({a} + {b}) x ({a} - {b}) = {(a + b) * (a - b)}, and {a * a} - {b * b} = {a * a - b * b}")
        """,
    ),

)
