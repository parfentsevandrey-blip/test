package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1520 to 1530: false position, the mixing of alloys, a pentagon's diagonal, a company's shares and a parabola's points. */
val db52Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 52,
        kind = Kind.GUIDE,
        section = "Inheritance",
        title = "How an estate is divided among heirs by halves and thirds",
        originalTitle = "How an estate is divided among heirs by halves and thirds",
        author = "Arithmetic books of the 1520s",
        year = 1527,
        summary = "A father's estate of 1200 guilders is divided by the fractions set in his will, and a short program gives each heir's share with exact fractions.",
        details = "Arithmetic books of the 1520s set problems of dividing an estate by fractions among heirs, and the shares must add up to the whole. The example gives a son one half, a daughter one third and a widow one sixth, which together make the whole estate, so the output has no rounding.",
        instructions = """
            1. Save the example as estate_shares.py.
            2. Run it: python3 estate_shares.py
        """,
        example = """
            from fractions import Fraction as F

            estate = 1200                                                         # guilders left by the father
            shares = {"son": F(1, 2), "daughter": F(1, 3), "widow": F(1, 6)}     # the fractions set by the will
            for heir, share in shares.items():
                print(f"{heir} receives {estate * share} guilders")
            print("total left:", sum(estate * s for s in shares.values()))
        """,
    ),

    entry(
        databaseId = 52,
        kind = Kind.GUIDE,
        section = "Arithmetic",
        title = "How a division is proved by multiplying back",
        originalTitle = "How a division is proved by multiplying back",
        author = "Merchant arithmetic of the 1520s",
        year = 1522,
        summary = "A division is proved by multiplying the quotient back by the divisor and adding the remainder, and a short program proves two divisions of 56425 and 56427 by 37.",
        details = "Arithmetic books of the 1520s taught the proof of a division by multiplying back, so that a reckoner could catch an error before a sum was paid. The example divides two numbers by 37, prints the quotient and remainder, and checks each answer by the proof, so the output is fixed.",
        instructions = """
            1. Save the example as division_proof.py.
            2. Run it: python3 division_proof.py
        """,
        example = """
            for a in (56425, 56427):
                q, r = a // 37, a % 37              # the quotient and the remainder of the division
                proof = q * 37 + r                  # multiply back by the divisor and add the remainder
                print(f"{a} divided by 37 gives {q} remainder {r}")
                print(f"proof: {q} times 37 plus {r} is {proof}, agrees: {proof == a}")
        """,
    ),

    entry(
        databaseId = 52,
        kind = Kind.GUIDE,
        section = "Geometry",
        title = "How a regular pentagon's diagonal follows from its side",
        originalTitle = "Underweysung der Messung",
        author = "Albrecht Duerer",
        year = 1525,
        summary = "The diagonal of a regular pentagon is the golden ratio times its side, about 1.618 times, and a short program prints the diagonal for three sides.",
        details = "Duerer's geometry book of 1525 gives constructions of regular polygons with compass and ruler, and the pentagon is one of them. The example uses the exact ratio, the golden ratio, and prints the diagonal to four decimal places, so the output is fixed for the three sides it checks.",
        instructions = """
            1. Save the example as pentagon_diagonal.py.
            2. Run it: python3 pentagon_diagonal.py
        """,
        example = """
            import math

            phi = (1 + math.sqrt(5)) / 2       # the diagonal of a regular pentagon over its side
            for side in (1, 2, 10):
                print(f"side {side}: diagonal {side * phi:.4f}")
        """,
    ),

    entry(
        databaseId = 52,
        kind = Kind.GUIDE,
        section = "Partnerships",
        title = "How a company shares its profit by the stakes",
        originalTitle = "Gesellschaftsrechnung, the company reckoning",
        author = "Peter Apian",
        year = 1527,
        summary = "The company rule shares a profit among partners in proportion to the money each put in, and a short program shares 1500 among three partners.",
        details = "Merchant arithmetic books of the 1520s set the company rule as a standard problem, and Apian's merchant reckoning of 1527 is one book of that kind. The example shares a profit of 1500 among three partners with exact fractions, so the shares add up to the whole profit with no rounding.",
        instructions = """
            1. Save the example as company_rule.py.
            2. Run it: python3 company_rule.py
        """,
        example = """
            from fractions import Fraction as F

            investments = {"Anna": 4, "Bernd": 6, "Clara": 10}   # the money each partner put in
            profit = 1500                                        # the profit of the year, in the same money
            total = sum(investments.values())
            for name, put in investments.items():
                share = F(profit) * put / total                  # the share is in proportion to the stake
                print(f"{name} puts in {put} and receives {share}")
            print("total shared:", sum(F(profit) * put / total for put in investments.values()))
        """,
    ),

    entry(
        databaseId = 52,
        kind = Kind.GUIDE,
        section = "Conics",
        title = "How Werner's conic sections give the points of a parabola",
        originalTitle = "Libellus super viginti duobus elementis conicis",
        author = "Johannes Werner",
        year = 1522,
        summary = "A parabola is the set of points as far from a focus as from a fixed line, and a short program checks three of its points with exact fractions.",
        details = "Werner's book of 1522 treats the conic sections, the curves that a plane cuts from a cone, and the example applies the defining property of the parabola to three of its points. The focus sits one unit above the origin and the directrix is the line y = -1, so each check uses whole numbers and fractions.",
        instructions = """
            1. Save the example as parabola_points.py.
            2. Run it: python3 parabola_points.py
        """,
        example = """
            from fractions import Fraction as F

            focus_height = 1                           # the focus is at (0, 1), the directrix is y = -1
            for x in (2, 4, 6):
                y = F(x * x, 4 * focus_height)         # the parabola x squared = 4 times the height of the focus times y
                d_focus = x * x + (y - focus_height) ** 2     # squared distance to the focus
                d_directrix = (y + focus_height) ** 2         # squared distance to the directrix
                print(f"x {x} gives y {y}, equal distances: {d_focus == d_directrix}")
        """,
    ),

)
