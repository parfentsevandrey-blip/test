package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1760 to 1770: Pell's equation by continued fractions, a cosine law, a level premium and a letter count. */
val db28Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 28,
        kind = Kind.PROJECT,
        section = "Number theory",
        title = "Lagrange's solutions of Pell's equation by continued fractions",
        originalTitle = "Pell's equation solved by continued fractions",
        author = "Joseph-Louis Lagrange",
        year = 1768,
        summary = "Lagrange showed that Pell's equation is solved from the continued fraction of the square root, and the program finds the fundamental solutions for 13.",
        details = "Lagrange's work of 1768 showed that every solution of Pell's equation can be derived from a fundamental one, and that the continued fraction of the square root gives it. The example runs the square-root expansion for 13 by integer steps, reports the period, and reads off the first convergents where the value is minus one and where it is plus one. The pair 649 and 180 is the fundamental solution of x squared minus 13 y squared equals 1.",
        instructions = """
            1. Save the example as pell_fraction.py.
            2. Run it: python3 pell_fraction.py
        """,
        example = """
            from math import isqrt

            D = 13
            a0 = isqrt(D)
            # Continued fraction of sqrt(D) by integer steps: m, d and a follow the standard recurrence.
            m, d, a = 0, 1, a0
            terms = [a0]
            while True:
                m = d * a - m
                d = (D - m * m) // d
                a = (a0 + m) // d
                terms.append(a)
                if a == 2 * a0:
                    break
            period = len(terms) - 1
            print("period length:", period)

            def a_k(k):
                # The partial quotients: the first period comes from the list, then it repeats.
                return terms[k] if k < len(terms) else terms[1 + (k - 1) % period]

            p_prev, p = 1, a0      # p_{-1} and p_0
            q_prev, q = 0, 1       # q_{-1} and q_0
            found = {}
            k = 0
            while len(found) < 2:
                if k > 0:
                    p, p_prev = a_k(k) * p + p_prev, p
                    q, q_prev = a_k(k) * q + q_prev, q
                value = p * p - D * q * q
                if value in (1, -1) and value not in found:
                    found[value] = (k, p, q)
                k += 1
            for value in (-1, 1):
                k, p, q = found[value]
                print(f"first convergent with p^2 - {D} q^2 = {value}: index {k}, {p}/{q}")
        """,
    ),

    entry(
        databaseId = 28,
        kind = Kind.PROJECT,
        section = "Optics",
        title = "Lambert's cosine law for a tilted surface",
        originalTitle = "Photometria, sive de mensura et gradibus luminis, colorum et umbrae",
        author = "Johann Heinrich Lambert",
        year = 1760,
        summary = "Light falling on a tilted surface is reduced by the cosine of the tilt, and the program computes the illuminance for three tilts with exact fractions.",
        details = "Lambert's Photometria of 1760 gives the cosine law for light on a surface, which is the light on a level surface times the cosine of the angle of tilt. The example takes tilts whose cosines come from whole-number right triangles, so every illuminance is an exact fraction. It compares each with the level surface, where the cosine is one.",
        instructions = """
            1. Save the example as lambert_cosine.py.
            2. Run it: python3 lambert_cosine.py
        """,
        example = """
            from fractions import Fraction as F

            # Lambert's cosine law: light on a tilted patch is the light on a level patch times the cosine of the tilt.
            # The cosines come from right triangles with whole sides, so each value is an exact fraction.
            I, d = 100, 5   # intensity of the source and its distance, illustrative values
            for side, cos in (("3-4-5", F(3, 5)), ("5-12-13", F(5, 13)), ("8-15-17", F(8, 17))):
                print(f"tilt from triangle {side}: cos = {cos}, illuminance = {F(I) * cos / d**2}")
            print("level patch, cos = 1:", F(I) / d**2)
        """,
        rare = true,
    ),

    entry(
        databaseId = 28,
        kind = Kind.PROJECT,
        section = "Insurance",
        title = "A level premium at three per cent from a toy life table",
        originalTitle = "A level premium at three per cent from a toy life table",
        author = "The Society for Equitable Assurances and its actuaries",
        year = 1762,
        summary = "A level premium is the present value of the benefit divided by the present value of the premiums, and the program computes it exactly for a two-year policy with a toy table.",
        details = "The Equitable Society's premiums of 1762 were level sums worked out from a mortality table with 3 per cent interest, and the same method, the present value of the benefit divided by the present value of the premiums, applies to any table. The example uses an illustrative table of 100, 90 and 70 survivors, which is not a historical table, and it prints the three values in fractions.",
        instructions = """
            1. Save the example as level_premium.py.
            2. Run it: python3 level_premium.py
        """,
        example = """
            from fractions import Fraction as F

            l = {0: 100, 1: 90, 2: 70}     # survivors at ages 0, 1 and 2 (an illustrative table, not a historical one)
            v = 1 / (1 + F(3, 100))        # discount factor at three per cent

            def benefit_value():
                # One unit paid at the end of the year of death, for a life insured at age 0 over two years.
                return sum(v ** (x + 1) * (l[x] - l[x + 1]) for x in (0, 1)) / l[0]

            def premium_annuity():
                # Premiums of one unit paid at the start of each year while the life is alive.
                return sum(v ** x * l[x] for x in (0, 1)) / l[0]

            premium = benefit_value() / premium_annuity()
            print("expected present value of the benefit:", benefit_value())
            print("present value of the premium stream per unit:", premium_annuity())
            print("level premium:", premium)
        """,
    ),

    entry(
        databaseId = 28,
        kind = Kind.PROJECT,
        section = "Alphabets",
        title = "Counting the letters a spelling reform must cover",
        originalTitle = "Counting the letters a spelling reform must cover",
        author = "Benjamin Franklin",
        year = 1768,
        summary = "A count of the letters in a test sentence gives the number of letters a reformed alphabet would have to handle, and the program prints the totals.",
        details = "Franklin's scheme of 1768 proposed new letters for the sounds of English, so a reform first has to know which letters a text uses. The project counts the letters of a short pangram written for the example and reports the total, the number of distinct letters and the most frequent letter. The counts are the start of any such survey and do not survey English as a whole.",
        instructions = """
            1. Save the example as letter_count.py.
            2. Run it: python3 letter_count.py
        """,
        example = """
            text = "the quick brown fox jumps over the lazy dog"
            letters = [ch for ch in text if ch.isalpha()]
            counts = {}
            for ch in letters:
                counts[ch] = counts.get(ch, 0) + 1
            top = max(sorted(counts), key=lambda c: counts[c])
            print("letters:", len(letters))
            print("distinct letters:", len(counts))
            print("most frequent letter:", top, "appears", counts[top], "times")
        """,
    ),
)
