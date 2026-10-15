package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1680 to 1689: a hearth-tax roll, a quadratic solver, a herbarium register and a marine premium table. */
val db36Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 36,
        kind = Kind.PROJECT,
        section = "Taxation",
        title = "A hearth-tax roll tallied for one parish",
        originalTitle = "Hearth money",
        author = "Parliament of England",
        year = 1689,
        summary = "A hearth-tax roll lists each household with its hearths, and the program totals the hearths and the tax and picks out the largest households.",
        details = "The hearth tax charged each hearth, so the count of hearths on the roll was the base for the whole charge. The roll and the rate of two shillings per hearth in the example are illustrative.",
        instructions = """
            1. Save the example as hearth_roll.py.
            2. Run it: python3 hearth_roll.py
        """,
        example = """
            # A hearth-tax roll lists each household with its hearths; the tax is counted per hearth.
            roll = [
                ("Mill Lane", 2),
                ("Crown inn", 6),
                ("Church Row", 1),
                ("Brook House", 4),
                ("Tanners yard", 3),
            ]
            TAX_PER_HEARTH = 2   # shillings per hearth, an illustrative rate for this example

            hearths = sum(count for _, count in roll)
            print("households:", len(roll))
            print("hearths:", hearths)
            print("tax at the example rate:", hearths * TAX_PER_HEARTH, "shillings")
            print("average hearths per household:", hearths / len(roll))
            for name, count in roll:
                if count >= 4:
                    print("large household:", name)
        """,
    ),

    entry(
        databaseId = 36,
        kind = Kind.PROJECT,
        section = "Algebra",
        title = "Solving a quadratic with imaginary roots, as Wallis's picture needs",
        originalTitle = "A Treatise of Algebra, both Historical and Practical",
        author = "John Wallis",
        year = 1685,
        summary = "A program solves quadratic equations with negative discriminants and writes the imaginary roots in the form a plus or minus b times i.",
        details = "Wallis's Algebra of 1685 suggested a picture for imaginary roots, and the program computes the roots that such a picture would need. The three equations are chosen so that the square roots of the discriminants are whole numbers, and the output shows roots with and without an imaginary part.",
        instructions = """
            1. Save the example as wallis_quadratic.py.
            2. Run it: python3 wallis_quadratic.py
        """,
        example = """
            from fractions import Fraction as F
            from math import isqrt

            def roots(a, b, c):
                # Roots of a x^2 + b x + c = 0: the real part is -b/(2a); the root of the discriminant gives the offset.
                disc = b * b - 4 * a * c
                real = F(-b, 2 * a)
                root = isqrt(abs(disc))
                assert root * root == abs(disc)   # the example's equations have whole-number square roots
                offset = F(root, 2 * a)
                if disc >= 0:
                    return [str(real + offset), str(real - offset)]
                return [f"{real}+{offset}i", f"{real}-{offset}i"]

            for a, b, c in ((1, -2, 5), (1, -6, 13), (1, -5, 6)):
                print(f"x^2 {b:+d}x {c:+d}:", " and ".join(roots(a, b, c)))
        """,
    ),

    entry(
        databaseId = 36,
        kind = Kind.PROJECT,
        section = "Botany",
        title = "A herbarium register of pressed plants by genus",
        originalTitle = "Methodus plantarum nova",
        author = "John Ray",
        year = 1682,
        summary = "A herbarium register files each pressed specimen under its genus, and the program counts the specimens in each genus and totals them.",
        details = "Ray's Methodus plantarum nova of 1682 grouped plants by their parts and by their genera, and this program follows the genus grouping for a small register of dried specimens. The six specimens and their common names are invented, and the genus is the only sorting key.",
        instructions = """
            1. Save the example as herbarium_register.py.
            2. Run it: python3 herbarium_register.py
        """,
        example = """
            # Each specimen is a (genus, common name) pair; the names are invented for the example.
            specimens = [
                ("Ranunculus", "buttercup"),
                ("Viola", "violet"),
                ("Ranunculus", "crowfoot"),
                ("Galium", "bedstraw"),
                ("Viola", "pansy"),
                ("Ranunculus", "celery-leaved buttercup"),
            ]

            counts = {}
            for genus, _ in specimens:
                counts[genus] = counts.get(genus, 0) + 1

            for genus in sorted(counts):
                print(f"{genus}: {counts[genus]} specimen(s)")
            print("total specimens:", len(specimens), "genera:", len(counts))
        """,
    ),

    entry(
        databaseId = 36,
        kind = Kind.PROJECT,
        section = "Insurance",
        title = "A marine insurance premium table for three voyages",
        originalTitle = "A marine insurance premium table for three voyages",
        author = "Edward Lloyd",
        year = 1688,
        summary = "A marine premium is the sum insured times a rate per cent, and the program prints the premium for three ships and adds them up.",
        details = "Edward Lloyd's coffee house was where ship owners and underwriters met in the late 1680s, and the premium is the payment an underwriter received for taking on a ship's risk. The rates and sums in the example are invented, and they show only the arithmetic of a premium.",
        instructions = """
            1. Save the example as premium_table.py.
            2. Run it: python3 premium_table.py
        """,
        example = """
            from fractions import Fraction as F

            # A premium is the sum insured times the rate, with the rate given in per cent.
            voyages = [
                ("ship Hope", 1000, 5),
                ("ship Faith", 2400, 3),
                ("ship Charity", 800, 6),
            ]
            total = F(0)
            for name, insured, rate in voyages:
                premium = F(insured * rate, 100)
                total += premium
                print(f"{name}: insured {insured} pounds at {rate} per cent, premium {premium} pounds")
            print("total premium:", total, "pounds")
        """,
    ),

)
