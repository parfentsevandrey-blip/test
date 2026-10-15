package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1760 to 1770: a posterior chance, an Euler step, a constrained maximum, a power-series inversion and an inverse-square power. */
val db28Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 28,
        kind = Kind.GUIDE,
        section = "Probability",
        title = "How Bayes's uniform prior gives a posterior chance",
        originalTitle = "An Essay towards solving a Problem in the Doctrine of Chances",
        author = "Thomas Bayes",
        year = 1763,
        summary = "With a uniform prior, the density of the chance after k successes in n trials is proportional to p^k (1 - p)^(n - k), and the program integrates it exactly.",
        details = "Bayes's essay of 1763 treats the position of a ball on a table as unknown and asks what the trials reveal about it, which is equivalent to a uniform prior on the chance. The example integrates the posterior polynomial exactly with fractions, and its value for seven successes in ten trials, 29/256, equals the chance that at least eight of eleven fair coins show heads. The essay was printed in the Philosophical Transactions in 1763, and accounts differ on whether the printing belongs to 1763 or 1764.",
        instructions = """
            1. Save the example as bayes_posterior.py.
            2. Run it: python3 bayes_posterior.py
        """,
        example = """
            from fractions import Fraction as F
            from math import comb

            def chance_at_most(k, n, x):
                # With a uniform prior, the posterior density of the chance p is proportional to p^k (1 - p)^(n - k).
                m = n - k
                coeffs = [0] * (n + 1)
                for j in range(m + 1):
                    coeffs[k + j] += comb(m, j) * (-1) ** j
                def integral(upper):
                    return sum(F(c) * F(upper) ** (i + 1) / (i + 1) for i, c in enumerate(coeffs))
                return integral(x) / integral(1)

            for k, n in [(2, 3), (1, 2), (7, 10), (3, 3)]:
                print(f"{k} successes in {n} trials, chance at most 1/2: {chance_at_most(k, n, F(1, 2))}")
        """,
    ),

    entry(
        databaseId = 28,
        kind = Kind.GUIDE,
        section = "Differential equations",
        title = "Euler's step method for the equation y' = y",
        originalTitle = "Institutionum calculi integralis",
        author = "Leonhard Euler",
        year = 1768,
        summary = "Each step moves the value forward by the step size times the slope, and the program applies the step to y' = y from y(0) = 1.",
        details = "Euler described the step in the first volume of his Institutionum calculi integralis in 1768. For y' = y with y(0) = 1 the exact answer at x = 1 is e, and each Euler step multiplies the value by 1 plus the step size, so with steps of 1/4 the value at x = 1 is (5/4) to the fourth power. The example prints the four steps and then a run with steps of 1/2 for comparison.",
        instructions = """
            1. Save the example as euler_step.py.
            2. Run it: python3 euler_step.py
        """,
        example = """
            from fractions import Fraction as F

            def euler(h, steps):
                # Euler's step for y' = y with y(0) = 1: each step adds h times the slope y.
                y = F(1)
                values = []
                for i in range(1, steps + 1):
                    y = y + h * y
                    values.append((h * i, y))
                return values

            for x, y in euler(F(1, 4), 4):
                print(f"y({x}) = {y}")
            x, y = euler(F(1, 2), 2)[-1]
            print(f"with step 1/2, y({x}) = {y}")
        """,
    ),

    entry(
        databaseId = 28,
        kind = Kind.GUIDE,
        section = "Maxima",
        title = "Lagrange's multiplier rule for a constrained maximum",
        originalTitle = "Essai d'une nouvelle methode pour determiner les maxima et les minima des formules integrales indefinies",
        author = "Joseph-Louis Lagrange",
        year = 1762,
        summary = "At a constrained maximum the gradient of the function is a multiple of the gradient of the constraint, and the program solves two such problems exactly.",
        details = "Lagrange introduced the multiplier in his 1762 essay on maxima and minima, which he wrote while in Turin. The rule says that the gradient of the function equals a multiplier times the gradient of the constraint, so for a product of two numbers with a fixed sum both numbers equal the multiplier. The example applies the rule to two numbers with sum 10 and to three numbers with sum 12, and it checks the first case by listing integer pairs.",
        instructions = """
            1. Save the example as lagrange_multiplier.py.
            2. Run it: python3 lagrange_multiplier.py
        """,
        example = """
            from fractions import Fraction as F

            def two_numbers(total):
                # Maximise x * y with x + y = total. Lagrange's rule: grad(x y) = lambda grad(x + y), so y = lambda and x = lambda.
                lam = F(total, 2)
                return lam, lam, lam * lam

            def three_numbers(total):
                # Maximise x * y * z with x + y + z = total: yz = xz = xy = lambda, so x = y = z.
                x = F(total, 3)
                return x, x, x, x ** 3

            x, y, value = two_numbers(10)
            print(f"two numbers summing to 10: x = {x}, y = {y}, product {value}")
            best = max(((a, 10 - a) for a in range(11)), key=lambda p: p[0] * p[1])
            print(f"check by listing: best integer pair {best}, product {best[0] * best[1]}")
            x, y, z, value = three_numbers(12)
            print(f"three numbers summing to 12: x = {x}, y = {y}, z = {z}, product {value}")
        """,
    ),

    entry(
        databaseId = 28,
        kind = Kind.GUIDE,
        section = "Series",
        title = "How Lagrange's inversion gives a power series",
        originalTitle = "Nouvelle methode pour resoudre les equations litterales par le moyen des series",
        author = "Joseph-Louis Lagrange",
        year = 1770,
        summary = "If w equals z times a function of w, Lagrange's formula gives each power of z in w, and the program prints the first coefficients for two choices of the function.",
        details = "Lagrange's 1770 memoir gave the formula now called Lagrange inversion, which expresses the solution of w = z phi(w) as a power series. The example takes phi(w) = 1 + c w squared, a flexible choice with the parameter c, and prints the coefficients of z up to the seventh power for c = 1 and c = 2. For c = 1 the coefficients at odd powers are the Catalan numbers.",
        instructions = """
            1. Save the example as lagrange_inversion.py.
            2. Run it: python3 lagrange_inversion.py
        """,
        example = """
            from fractions import Fraction as F
            from math import comb

            def inversion_coefficients(c, terms):
                # Lagrange's inversion for w = z (1 + c w^2): [z^n] w = (1/n) [w^(n-1)] (1 + c w^2)^n.
                out = []
                for n in range(1, terms + 1):
                    if n % 2 == 0:
                        out.append(F(0))
                    else:
                        k = (n - 1) // 2
                        out.append(F(comb(n, k) * c**k, n))
                return out

            print("c = 1:", [str(x) for x in inversion_coefficients(1, 7)])
            print("c = 2:", [str(x) for x in inversion_coefficients(2, 7)])
        """,
    ),

    entry(
        databaseId = 28,
        kind = Kind.GUIDE,
        section = "Electricity",
        title = "How two readings fix an inverse-square power",
        originalTitle = "The History and Present State of Electricity",
        author = "Joseph Priestley",
        year = 1767,
        summary = "If a force falls off as a power of distance, the ratio of two readings fixes the power, and the program finds the whole-number power that fits each pair of readings.",
        details = "Priestley's History and Present State of Electricity of 1767 argued by analogy with gravity that electric force falls off as the inverse square of distance. The example takes pairs of illustrative force readings at distances in a fixed ratio and finds the whole-number power that fits each pair, so the power two comes out for the first pair and the power three for the second. The fourth pair fits no whole power.",
        instructions = """
            1. Save the example as inverse_power.py.
            2. Run it: python3 inverse_power.py
        """,
        example = """
            from fractions import Fraction as F

            def fitted_power(r1, f1, r2, f2):
                # Find n with f1 / f2 = (r2 / r1) to the power n, for whole n from 0 to 6.
                ratio = F(f1, f2)
                for n in range(7):
                    if F(r2, r1) ** n == ratio:
                        return n
                return None

            readings = [
                ("distances 1 and 2, forces 4 and 1", 1, 4, 2, 1),
                ("distances 1 and 2, forces 8 and 1", 1, 8, 2, 1),
                ("distances 2 and 4, forces 16 and 1", 2, 16, 4, 1),
                ("distances 1 and 3, forces 5 and 1", 1, 5, 3, 1),
            ]
            for label, r1, f1, r2, f2 in readings:
                print(f"{label}: power {fitted_power(r1, f1, r2, f2)}")
        """,
    ),
)
