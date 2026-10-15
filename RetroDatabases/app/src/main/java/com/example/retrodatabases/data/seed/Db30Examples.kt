package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1740 to 1750: a limit for e, a comma of tuning, an annuity, a sine series, a charge and a pawn race. */
val db30Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 30,
        kind = Kind.EXAMPLE,
        section = "Exponentials",
        title = "Euler's limit for the number e",
        originalTitle = "Introductio in analysin infinitorum",
        author = "Leonhard Euler",
        year = 1748,
        summary = "The number e is the limit of (1 + 1/n) to the power n as n grows, and the program prints that power for four values of n.",
        details = "Euler's Introductio of 1748 treats the exponential function and the number e, and the limit of (1 + 1/n) to the power n is the form by which e is often introduced. The example computes that power for n = 1, 2, 4 and 10, giving 2, 9/4, 625/256 and 25937424601/10000000000, which move towards e.",
        instructions = """
            1. Save the example as euler_e.py.
            2. Run it: python3 euler_e.py
        """,
        example = """
            from fractions import Fraction as F

            def compound(n):
                # A unit grown by 1/n in each of n equal parts: (1 + 1/n) to the power n.
                return (1 + F(1, n)) ** n

            for n in (1, 2, 4, 10):
                print(f"n = {n}: {compound(n)}")
            print("n = 10 as a decimal:", f"{float(compound(10)):.6f}")
            print("closer to e as n grows:", abs(compound(10) - 2.718281828) < abs(compound(2) - 2.718281828))
        """,
    ),

    entry(
        databaseId = 30,
        kind = Kind.EXAMPLE,
        section = "Curves",
        title = "Agnesi's witch curve, y = 8/(x^2 + 4)",
        originalTitle = "Istituzioni analitiche ad uso della gioventu italiana",
        author = "Maria Gaetana Agnesi",
        year = 1748,
        summary = "A curve that Agnesi described in her textbook of 1748, whose height over the point x is 8 over x squared plus 4, which the program evaluates exactly at four points.",
        details = "Agnesi's textbook of 1748 described this curve, which was later called the witch of Agnesi in English, after a mistranslation of its Italian name. The example takes the parameter a equal to 1, prints the height at x = 0, 1, 2 and 4, and checks the symmetry and the highest point.",
        instructions = """
            1. Save the example as agnesi.py.
            2. Run it: python3 agnesi.py
        """,
        example = """
            from fractions import Fraction as F

            def witch(x, a=1):
                # Agnesi's curve y = 8 a^3 / (x^2 + 4 a^2): the height over the point x.
                return F(8 * a ** 3) / (x * x + 4 * a * a)

            for x in (0, 1, 2, 4):
                print(f"x = {x}: y = {witch(F(x))}")
            print("symmetric about the axis:", witch(F(3)) == witch(F(-3)))
            print("highest point on these samples at x =", max((0, 1, 2, 4), key=lambda x: witch(F(x))))
        """,
    ),

    entry(
        databaseId = 30,
        kind = Kind.EXAMPLE,
        section = "Annuities",
        title = "Simpson's annuity of 1 a year for five years",
        originalTitle = "The Doctrine of Annuities and Reversions",
        author = "Thomas Simpson",
        year = 1742,
        summary = "The present value of a payment of one at the end of each year is a sum of powers of one over one plus the rate, which the program adds up exactly.",
        details = "Simpson's book of 1742 on annuities and reversions treated the present value of yearly payments that run for a fixed number of years. The example finds the present value of 1 paid at the end of each of five years at 10 per cent, which is 610510/161051, or 3.7908, and checks it against the closed form.",
        instructions = """
            1. Save the example as annuity.py.
            2. Run it: python3 annuity.py
        """,
        example = """
            from fractions import Fraction as F

            def annuity(rate, years):
                # Present value of 1 paid at the end of each year, discounted at the given rate.
                v = 1 / (1 + rate)
                return sum(v ** k for k in range(1, years + 1))

            pv = annuity(F(1, 10), 5)
            print("present value of 1 a year for 5 years at 10 per cent:", pv)
            print("as a decimal:", f"{float(pv):.4f}")
            print("closed form agrees:", pv == F(1, F(1, 10)) * (1 - F(10, 11) ** 5))
        """,
    ),

    entry(
        databaseId = 30,
        kind = Kind.EXAMPLE,
        section = "Series",
        title = "Maclaurin's series for the sine",
        originalTitle = "A Treatise of Fluxions",
        author = "Colin Maclaurin",
        year = 1742,
        summary = "The sine of an angle is written as an alternating sum of odd powers divided by factorials, which the program sums term by term for one half.",
        details = "Maclaurin's Treatise of Fluxions of 1742 gives expansions of this kind, and the series bears his name although the method of expanding functions was older. The example sums the first one, two and three terms for x = 1/2, giving 1/2, 23/48 and 1841/3840, and prints the third sum as a decimal.",
        instructions = """
            1. Save the example as maclaurin_sine.py.
            2. Run it: python3 maclaurin_sine.py
        """,
        example = """
            from fractions import Fraction as F
            from math import factorial

            def sine_series(x, terms):
                # Maclaurin's series: sin x = x - x^3/3! + x^5/5! - ..., summed over the first given terms.
                return sum(F((-1) ** k * x ** (2 * k + 1), factorial(2 * k + 1)) for k in range(terms))

            x = F(1, 2)
            for terms in (1, 2, 3):
                print(f"{terms} term(s): {sine_series(x, terms)}")
            print("first three terms as a decimal:", f"{float(sine_series(x, 3)):.6f}")
        """,
    ),

    entry(
        databaseId = 30,
        kind = Kind.EXAMPLE,
        section = "Complex numbers",
        title = "Euler's identity: e to the i pi plus one is zero (1748)",
        originalTitle = "Introductio in analysin infinitorum",
        author = "Leonhard Euler",
        year = 1748,
        summary = "Euler's identity gives e to the power i pi as minus one, so that the modulus of e to the i pi plus one is zero, which the program checks with cmath.",
        details = "Euler's Introductio of 1748 relates the exponential to the sine and cosine of an imaginary argument, which gives e to the i pi as minus one. The example computes e to the i pi plus one in floating point, takes its modulus, and rounds it to nine decimals, which gives 0.0 because the error is about 1.2 times 10 to the minus 16.",
        instructions = """
            1. Save the example as euler_identity.py.
            2. Run it: python3 euler_identity.py
        """,
        example = """
            import cmath
            import math

            # exp(i pi) = cos(pi) + i sin(pi) = -1, so exp(i pi) + 1 should be zero.
            z = cmath.exp(1j * math.pi) + 1
            print("modulus of e^(i pi) + 1 rounded to nine decimals:", round(abs(z), 9))
            print("modulus below 1e-15:", abs(z) < 1e-15)
        """,
    ),

    entry(
        databaseId = 30,
        kind = Kind.EXAMPLE,
        section = "Chess",
        title = "Philidor's pawns in a race to promote",
        originalTitle = "L'Analyse du jeu des echecs",
        author = "Francois-Andre Danican Philidor",
        year = 1749,
        summary = "A pawn needs one move for each rank it must cross, so the program counts the moves for four pawns and names the one that promotes first.",
        details = "Philidor's Analysis of 1749 argued that pawns are the soul of chess. The example counts the moves for pawns on e2, d3, c5 and g7 to reach the eighth rank, which are 6, 5, 3 and 1, so the pawn on g7 promotes first.",
        instructions = """
            1. Save the example as pawn_race.py.
            2. Run it: python3 pawn_race.py
        """,
        example = """
            TOP = 8   # the eighth rank, where a white pawn promotes

            def moves_to_promote(rank):
                # A white pawn on the given rank (1 to 8) needs one move per rank to reach the eighth.
                return TOP - rank

            starts = {"e": 2, "d": 3, "c": 5, "g": 7}   # file letter and starting rank
            race = sorted(starts.items(), key=lambda item: moves_to_promote(item[1]))
            for file, rank in starts.items():
                print(f"{file}{rank}: {moves_to_promote(rank)}")
            print("first to promote:", race[0][0] + str(race[0][1]))
            print("total moves for all four pawns:", sum(moves_to_promote(r) for r in starts.values()))
        """,
    ),
)
