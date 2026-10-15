package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1800 to 1810: three sums of triangles, a count of binary forms and the periods of decimal fractions. */
val db24Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 24,
        kind = Kind.FLEX,
        section = "Real roots",
        title = "Budan's bound on the roots of a cubic",
        originalTitle = "Nouvelle methode pour la resolution des equations numeriques d'un degre quelconque (1807)",
        author = "Francois Budan",
        year = 1807,
        summary = "Budan's bound counts the sign changes of a polynomial and its derivatives at the two ends of an interval, and the program checks the bound on a cubic with roots 1, 2 and 3.",
        details = "Budan published the bound in 1807, and Fourier later gave a fuller form of the result, so the record uses 1807 for Budan's publication. The example evaluates the cubic and its first three derivatives in exact fractions over four intervals, and compares the sign-change count with the roots inside each interval.",
        instructions = """
            1. Save the example as budan.py.
            2. Run it: python3 budan.py
        """,
        example = """
            from fractions import Fraction as F

            # P(x) = x^3 - 6x^2 + 11x - 6, with its first three derivatives in order.
            def values(x):
                return [x**3 - 6*x**2 + 11*x - 6, 3*x**2 - 12*x + 11, 6*x - 12, F(6)]

            def variations(seq):
                # Sign changes in a sequence of values, with zeros left out.
                signs = [v > 0 for v in seq if v != 0]
                return sum(1 for s, t in zip(signs, signs[1:]) if s != t)

            roots = [1, 2, 3]
            print("P at the roots:", [int(values(F(r))[0]) for r in roots])
            for a, b in [(F(0), F(4)), (F(0), F(3, 2)), (F(3, 2), F(5, 2)), (F(1, 2), F(7, 2))]:
                bound = variations(values(a)) - variations(values(b))
                inside = sum(1 for r in roots if a < r <= b)
                print(f"interval ({a}, {b}]: Budan bound {bound}, roots inside {inside}")
        """,
    ),

    entry(
        databaseId = 24,
        kind = Kind.FLEX,
        section = "Binary forms",
        title = "Reduced quadratic forms of discriminant minus 23",
        originalTitle = "Disquisitiones Arithmeticae, binary quadratic forms (1801)",
        author = "Carl Friedrich Gauss",
        year = 1801,
        summary = "Gauss's theory of binary quadratic forms counts the reduced forms of a discriminant, and the program lists the three reduced forms of discriminant -23.",
        details = "Gauss treated binary quadratic forms in the Disquisitiones of 1801, and the count of reduced forms for a discriminant gives the class number of that discriminant. The example finds the reduced forms of -23 by search, using the bounds of the reduction rule.",
        instructions = """
            1. Save the example as reduced_forms.py.
            2. Run it: python3 reduced_forms.py
        """,
        example = """
            # A binary form (a, b, c) stands for a x^2 + b x y + c y^2, and its discriminant is b*b - 4*a*c.
            D = -23

            def reduced_forms(D):
                forms = []
                for a in range(1, 6):
                    for b in range(-a + 1, a + 1):
                        if (b * b - D) % (4 * a) == 0:
                            c = (b * b - D) // (4 * a)
                            if c >= a and (c != a or b >= 0):
                                forms.append((a, b, c))
                return forms

            forms = reduced_forms(D)
            print("reduced forms of discriminant", D, ":", forms)
            print("count:", len(forms))
        """,
    ),

    entry(
        databaseId = 24,
        kind = Kind.FLEX,
        section = "Mixed gases",
        title = "Dalton's partial pressures in a mixture of gases",
        originalTitle = "Essay on the constitution of mixed gases (1801)",
        author = "John Dalton",
        year = 1801,
        summary = "Dalton's law says the pressures of the gases in a mixture add up to the total, each gas counting as if it filled the space alone, and the program checks this in exact fractions.",
        details = "Dalton stated the law in 1801, and his essays on mixed gases were printed at Manchester in 1802, so accounts differ on the year. The example takes a rounded air mixture at one atmosphere, then splits 760 mmHg among three gases in the parts 2, 3 and 5.",
        instructions = """
            1. Save the example as dalton_pressures.py.
            2. Run it: python3 dalton_pressures.py
        """,
        example = """
            from fractions import Fraction as F

            # Dalton's law: each gas in a mixture exerts the pressure it would exert alone, so the pressures add up.
            total = F(1)   # total pressure, in atmospheres
            shares = {"nitrogen": F(4, 5), "oxygen": F(1, 5)}
            partial = {gas: share * total for gas, share in shares.items()}
            for gas, p in partial.items():
                print(f"{gas}: {p} atm")
            print("sum of partial pressures:", sum(partial.values()), "atm")
            print("total matches:", sum(partial.values()) == total)
            # A mixture of three gases at 760 mmHg, in the parts 2, 3 and 5.
            parts = {"A": 2, "B": 3, "C": 5}
            mmhg = 760
            for gas, n in parts.items():
                print(f"gas {gas}: {F(n, sum(parts.values())) * mmhg} mmHg")
        """,
    ),
)
