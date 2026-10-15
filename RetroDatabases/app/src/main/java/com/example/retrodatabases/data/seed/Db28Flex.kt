package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1760 to 1770: Waring's power sums, a cubic's resolvent and a printer's page count. */
val db28Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 28,
        kind = Kind.FLEX,
        section = "Algebra",
        title = "Waring's power sums of three numbers",
        originalTitle = "Miscellanea Analytica de aequationibus algebraicis et curvarum proprietatibus",
        author = "Edward Waring",
        year = 1762,
        summary = "The fourth power sum of three numbers can be written with their elementary sums, and the program checks the formula for two triples of numbers.",
        details = "Waring gave formulas for power sums in terms of elementary symmetric sums in his Miscellanea Analytica of 1762. The example takes the formula for the fourth power sum, e1 to the fourth minus 4 e1 squared e2 plus 2 e2 squared plus 4 e1 e3, and evaluates it for two triples of numbers, checking each against the direct sum of fourth powers.",
        instructions = """
            1. Save the example as waring_power.py.
            2. Run it: python3 waring_power.py
        """,
        example = """
            from itertools import combinations

            def waring_p4(numbers):
                # Waring's formula for the fourth power sum of three numbers: e1^4 - 4 e1^2 e2 + 2 e2^2 + 4 e1 e3.
                e1 = sum(numbers)
                e2 = sum(a * b for a, b in combinations(numbers, 2))
                e3 = numbers[0] * numbers[1] * numbers[2]
                formula = e1**4 - 4 * e1**2 * e2 + 2 * e2**2 + 4 * e1 * e3
                direct = sum(x**4 for x in numbers)
                return e1, e2, e3, formula, direct

            for numbers in ((1, 2, 3), (2, 3, 5)):
                e1, e2, e3, formula, direct = waring_p4(numbers)
                print(f"numbers {numbers}: elementary sums {e1} {e2} {e3}, fourth power sum {formula} by the formula and {direct} directly, agree: {formula == direct}")
        """,
    ),

    entry(
        databaseId = 28,
        kind = Kind.FLEX,
        section = "Algebra",
        title = "Lagrange's resolvent for a cubic",
        originalTitle = "Reflexions sur la resolution algebrique des equations",
        author = "Joseph-Louis Lagrange",
        year = 1770,
        summary = "A cubic's Lagrange resolvent uses the cube roots of one, and the program checks the resolvent of x^3 - 7x + 6 against the coefficients of the cubic.",
        details = "Lagrange's Reflexions on the algebraic solution of equations treat cubics with resolvents built from the cube roots of one. The example works with numbers of the form a + b w, where w is a cube root of one, so that every step stays exact. For the cubic x^3 - 7x + 6 with roots 1, 2 and -3, the cubes of the two resolvents have sum minus 162 and product 9261, which are minus 27 times the coefficients of the cubic.",
        instructions = """
            1. Save the example as resolvent.py.
            2. Run it: python3 resolvent.py
        """,
        example = """
            def mul(p, q):
                # Multiply a + b w and c + d w, where w is a cube root of one and w^2 = -1 - w.
                a, b = p
                c, d = q
                return (a * c - b * d, a * d + b * c - b * d)

            def power(p, n):
                result = (1, 0)
                for _ in range(n):
                    result = mul(result, p)
                return result

            def add(p, q):
                return (p[0] + q[0], p[1] + q[1])

            roots = (1, 2, -3)
            p, q = -7, 6                      # the cubic is x^3 + p x + q = x^3 - 7x + 6
            r1, r2, r3 = roots
            u = (r1 - r3, r2 - r3)            # r1 + w r2 + w^2 r3
            v = (r1 - r2, r3 - r2)            # r1 + w^2 r2 + w r3
            u3, v3 = power(u, 3), power(v, 3)
            print("u^3 + v^3 =", add(u3, v3)[0], "and u^3 v^3 =", mul(u3, v3)[0])
            print("-27 q =", -27 * q, "and -27 p^3 =", -27 * p**3)
            ok = add(u3, v3)[0] == -27 * q and mul(u3, v3)[0] == -27 * p**3 and add(u3, v3)[1] == 0 and mul(u3, v3)[1] == 0
            print("both agree:", ok)
        """,
    ),

    entry(
        databaseId = 28,
        kind = Kind.FLEX,
        section = "Printing",
        title = "Printer's arithmetic: pages from folded sheets",
        originalTitle = "Printer's arithmetic: pages from folded sheets",
        author = "Printers of the period",
        year = 1768,
        summary = "A printed sheet folded into four leaves in quarto or eight leaves in octavo gives eight or sixteen pages, and the program counts the pages of two print runs.",
        details = "Printers counted a book by its folded sheets, since each leaf carries two pages. The example counts the pages of runs of 10 and 25 sheets in quarto and in octavo. The same arithmetic applies to the multi-volume encyclopedias of the 1760s, whose formats the example does not assume.",
        instructions = """
            1. Save the example as sheets.py.
            2. Run it: python3 sheets.py
        """,
        example = """
            def pages(sheets, leaves_per_sheet):
                # Each leaf carries two pages: a quarto sheet folds into 4 leaves and an octavo sheet into 8.
                return sheets * leaves_per_sheet * 2

            for sheets in (10, 25):
                print(f"{sheets} sheets folded to quarto: {pages(sheets, 4)} pages")
                print(f"{sheets} sheets folded to octavo: {pages(sheets, 8)} pages")
        """,
    ),
)
