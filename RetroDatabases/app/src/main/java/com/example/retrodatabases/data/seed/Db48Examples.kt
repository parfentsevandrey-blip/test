package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1560 to 1570: synthetic division, rook placements, letter pairs, ratios, Psalm verse counts and Euclid's method. */
val db48Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 48,
        kind = Kind.EXAMPLE,
        section = "Algebra",
        title = "Synthetic division of a cubic with a known root",
        originalTitle = "Libro de algebra en arithmetica y geometria",
        author = "Pedro Nunes",
        year = 1567,
        summary = "Synthetic division divides a cubic by x minus a known root, and the quotient then gives the other roots of the cubic.",
        details = "Nunes's algebra of 1567 covers cubic equations, and the division shown here is a later shorthand for the same kind of reduction. The example divides x cubed minus six x squared plus eleven x minus six by x minus 1, then finds the roots of the quotient.",
        instructions = """
            1. Save the example as synthetic_division.py.
            2. Run it: python3 synthetic_division.py
        """,
        example = """
            def divide_by_root(coefficients, root):
                # Synthetic division: each new coefficient is the old one plus the root times the previous result.
                result = [coefficients[0]]
                for coefficient in coefficients[1:]:
                    result.append(coefficient + root * result[-1])
                return result[:-1], result[-1]

            cubic = [1, -6, 11, -6]          # x^3 - 6x^2 + 11x - 6
            quotient, remainder = divide_by_root(cubic, 1)
            print("quotient coefficients:", quotient)
            print("remainder:", remainder)
            roots = [x for x in range(-10, 11) if sum(c * x ** (len(quotient) - 1 - i) for i, c in enumerate(quotient)) == 0]
            print("roots of the quotient:", " and ".join(str(r) for r in roots))
        """,
    ),

    entry(
        databaseId = 48,
        kind = Kind.EXAMPLE,
        section = "Chess",
        title = "Non-attacking rooks on a chessboard",
        originalTitle = "Libro de la invencion liberal y arte del juego del axedrez",
        author = "Ruy Lopez de Segura",
        year = 1561,
        summary = "Rooks that never attack each other must sit in different rows and columns, so an n by n board takes n factorial placements of n rooks.",
        details = "Ruy Lopez's book of 1561 treats the game of chess, and the counting rule here is a simple one about the rook's lines of attack. The example prints the counts for four small boards and for the standard eight by eight board.",
        instructions = """
            1. Save the example as rook_count.py.
            2. Run it: python3 rook_count.py
        """,
        example = """
            from math import factorial

            # Rooks that never attack each other must sit in different rows and different columns.
            for size in (1, 2, 3, 4, 8):
                print(f"{size} x {size} board: count {factorial(size)}")
        """,
    ),

    entry(
        databaseId = 48,
        kind = Kind.EXAMPLE,
        section = "Ciphers",
        title = "Counting the letter pairs of a Porta-style alphabet",
        originalTitle = "De furtivis literarum notis",
        author = "Giambattista della Porta",
        year = 1563,
        summary = "A table that pairs letters needs one entry for each unordered pair, and the count for an alphabet of n letters is n times n minus one over two.",
        details = "Porta's cipher book of 1563 works with pairs of letters, and the number of pairs grows quickly with the size of the alphabet. The example counts the pairs for an alphabet of twenty letters and for the usual alphabet of twenty-six.",
        instructions = """
            1. Save the example as letter_pairs.py.
            2. Run it: python3 letter_pairs.py
        """,
        example = """
            from math import comb

            for alphabet in (20, 26):
                print(f"alphabet of {alphabet} letters: {comb(alphabet, 2)} pairs of letters")
        """,
    ),

    entry(
        databaseId = 48,
        kind = Kind.EXAMPLE,
        section = "Proportions",
        title = "Ratios of numbers in Cardano's proportions",
        originalTitle = "Opus novum de proportionibus numerorum",
        author = "Gerolamo Cardano",
        year = 1570,
        summary = "A ratio of two whole numbers is written in lowest terms, and two ratios are in proportion when their cross products agree.",
        details = "Cardano's book of 1570 deals with proportions of numbers, and the example uses exact fractions to write ratios in lowest terms. The second part checks whether two pairs of numbers are in proportion by comparing their cross products.",
        instructions = """
            1. Save the example as ratios.py.
            2. Run it: python3 ratios.py
        """,
        example = """
            from fractions import Fraction as F

            for a, b in ((12, 8), (9, 15), (7, 21)):
                print(f"{a} to {b} is {F(a, b)}")

            first = (12, 8)
            second = (15, 10)
            same = first[0] * second[1] == first[1] * second[0]
            print(f"{first[0]}:{first[1]} and {second[0]}:{second[1]} in proportion: {'yes' if same else 'no'}")
        """,
    ),

    entry(
        databaseId = 48,
        kind = Kind.EXAMPLE,
        section = "Psalms",
        title = "Verse counts of three Psalms",
        originalTitle = "The Geneva Bible",
        author = "Geneva translators",
        year = 1560,
        summary = "Once verses are numbered, the length of a psalm is simply its largest verse number, and three psalms show how far the lengths differ.",
        details = "The Geneva Bible of 1560 numbered the verses of every chapter, and the example uses the modern counts for three psalms. Psalm 23 has six verses, Psalm 117 has two, and Psalm 119 has 176, the longest chapter in the Bible.",
        instructions = """
            1. Save the example as psalm_verses.py.
            2. Run it: python3 psalm_verses.py
        """,
        example = """
            verses = {"Psalm 23": 6, "Psalm 117": 2, "Psalm 119": 176}

            for psalm, count in verses.items():
                print(f"{psalm}: {count} verses")
            print("total:", sum(verses.values()), "verses")
            print("longest:", max(verses, key=verses.get))
        """,
    ),

    entry(
        databaseId = 48,
        kind = Kind.EXAMPLE,
        section = "Arithmetic",
        title = "Euclid's method on the years 1564 and 1567",
        originalTitle = "Libro de algebra en arithmetica y geometria",
        author = "Pedro Nunes",
        year = 1567,
        summary = "Euclid's method finds the greatest common divisor of two numbers by repeated division with remainder, and the example runs it on two years.",
        details = "Nunes's book of 1567 treats arithmetic and geometry together, and Euclid's method is one that readers of Euclid knew well. The example prints each division step and then the greatest common divisor, which is 1 for these two years.",
        instructions = """
            1. Save the example as euclid_years.py.
            2. Run it: python3 euclid_years.py
        """,
        example = """
            def euclid(a, b):
                # Euclid's method: replace the pair (a, b) by (b, a mod b) until the remainder is 0.
                while b:
                    print(f"{a} = {a // b} x {b} + {a % b}")
                    a, b = b, a % b
                return a

            first, second = 1567, 1564
            print(f"gcd of {first} and {second} is {euclid(first, second)}")
        """,
    ),

)
