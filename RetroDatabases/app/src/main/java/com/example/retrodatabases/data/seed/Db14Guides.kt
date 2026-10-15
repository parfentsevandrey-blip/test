package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1900 to 1910: the numerical and statistical methods of the decade, each with a program that runs today. */
val db14Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 14,
        kind = Kind.GUIDE,
        section = "Numerical methods",
        title = "How a Runge-Kutta step is computed",
        originalTitle = "Beitrag zur naeherungsweisen Integration totaler Differentialgleichungen (1901)",
        author = "Wilhelm Kutta",
        year = 1901,
        summary = "The classical fourth-order step averages four slopes, and for y' = y it multiplies the value by a fixed polynomial in the step size.",
        details = "Kutta's 1901 fourth-order method is shown here in exact fractions. The example takes two steps of size one half for y' = y, shows that one step multiplies the value by 211/128, and compares the result with e.",
        instructions = """
            1. Save the example as rk4.py.
            2. Run it: python3 rk4.py
        """,
        example = """
            from fractions import Fraction

            def rk4_step(y, h):
                # Classical fourth-order Runge-Kutta step for y' = y: four slopes, weighted 1, 2, 2 and 1.
                f = lambda v: v
                k1 = f(y)
                k2 = f(y + h / 2 * k1)
                k3 = f(y + h / 2 * k2)
                k4 = f(y + h * k3)
                return y + h / 6 * (k1 + 2 * k2 + 2 * k3 + k4)

            h = Fraction(1, 2)
            print("one step factor R(h) =", rk4_step(Fraction(1), h))
            y = Fraction(1)
            for step in range(1, 3):
                y = rk4_step(y, h)
                print(f"after step {step}: y = {y}")
            print("decimal:", float(y))
            e = 2.718281828459045
            print(f"error against e: {e - float(y):.6f}")
        """,
    ),

    entry(
        databaseId = 14,
        kind = Kind.GUIDE,
        section = "Integration",
        title = "How a Lebesgue integral adds up by heights",
        originalTitle = "Integrale, longueur, aire (1902)",
        author = "Henri Lebesgue",
        year = 1902,
        summary = "Instead of slicing the x-axis, the integral adds each value times the length of the set where the function takes that value.",
        details = "The example takes a step function with two values on [0, 1], measures the set where each value is taken, and adds value times measure, using exact fractions. The sum is 11/4, the same as the sum over the pieces, which the example checks.",
        instructions = """
            1. Save the example as lebesgue.py.
            2. Run it: python3 lebesgue.py
        """,
        example = """
            from fractions import Fraction

            # A step function on [0, 1], given as pieces (start, end, value) that cover the interval.
            pieces = [
                (Fraction(0), Fraction(1, 2), 2),
                (Fraction(1, 2), Fraction(3, 4), 5),
                (Fraction(3, 4), Fraction(1), 2),
            ]

            # Level sets: the measure of each value is the total length of the pieces that take it.
            measure = {}
            for start, end, value in pieces:
                measure[value] = measure.get(value, Fraction(0)) + (end - start)

            total = Fraction(0)
            for value in sorted(measure):
                print(f"level {value}: measure {measure[value]}")
                total += value * measure[value]
            print(f"Lebesgue sum: {total} = {float(total)}")

            # The same number, summed piece by piece.
            pieces_sum = sum(value * (end - start) for start, end, value in pieces)
            print("piece sum agrees:", pieces_sum == total)
        """,
    ),

    entry(
        databaseId = 14,
        kind = Kind.GUIDE,
        section = "Stochastic processes",
        title = "How a Markov chain settles on a stationary distribution",
        originalTitle = "Extension of the law of large numbers to dependent quantities (1906)",
        author = "Andrey Markov",
        year = 1906,
        summary = "Repeated steps of a two-state chain carry any starting spread to the same limit, which power iteration finds.",
        details = "The example runs a two-state chain with exact decimals for the first three steps and prints the distribution after 60 steps. It also checks that the limit is unchanged by one more step.",
        instructions = """
            1. Save the example as markov.py.
            2. Run it: python3 markov.py
        """,
        example = """
            from fractions import Fraction as F

            # Two states, sunny and rainy. Row i holds the chances of moving from state i to each state.
            P = [
                [F(9, 10), F(1, 10)],
                [F(1, 2), F(1, 2)],
            ]

            def step(dist):
                return [sum(dist[i] * P[i][j] for i in range(2)) for j in range(2)]

            dist = [F(1), F(0)]
            for n in range(1, 4):
                dist = step(dist)
                print(f"step {n}: " + " ".join(str(float(x)) for x in dist))
            for _ in range(57):
                dist = step(dist)
            print("after 60 steps: " + " ".join(f"{float(x):.4f}" for x in dist))
            stationary = [F(5, 6), F(1, 6)]
            print("stationary check:", step(stationary) == stationary)
        """,
    ),

    entry(
        databaseId = 14,
        kind = Kind.GUIDE,
        section = "Statistics",
        title = "How a t-test checks a sample mean",
        originalTitle = "The probable error of a mean (1908)",
        author = "William Sealy Gosset (Student)",
        year = 1908,
        summary = "A sample mean is compared with a claimed value, and the sample's spread shows how far apart the two are in standard errors.",
        details = "The example computes the t statistic for two claimed means from a five-value sample. It compares each with the 5 percent two-sided critical value for four degrees of freedom, so one claim is rejected and one is kept.",
        instructions = """
            1. Save the example as t_test.py.
            2. Run it: python3 t_test.py
        """,
        example = """
            import math

            sample = [5, 7, 9, 6, 8]
            n = len(sample)
            mean = sum(sample) / n
            s = math.sqrt(sum((x - mean) ** 2 for x in sample) / (n - 1))
            se = s / math.sqrt(n)
            critical = 2.7764  # two-sided 5 percent point of Student's t for 4 degrees of freedom, from a table
            print(f"mean {mean}, sample sd {s:.4f}, standard error {se:.4f}")
            for claimed in (5, 6):
                t = (mean - claimed) / se
                verdict = "reject" if abs(t) > critical else "keep"
                print(f"claimed mean {claimed}: t = {t:.4f}, df = {n - 1}, critical {critical}: {verdict}")
        """,
    ),

    entry(
        databaseId = 14,
        kind = Kind.GUIDE,
        section = "Numerical methods",
        title = "How Richardson extrapolation removes the leading error",
        originalTitle = "The approximate arithmetical solution by finite differences of physical problems involving differential equations, with an application to the stresses in a masonry dam (1910)",
        author = "Lewis Fry Richardson",
        year = 1910,
        summary = "Two estimates with steps h and h/2 combine to cancel the leading error term, and the step can be repeated to cancel the next one.",
        details = "The example applies the trapezoid rule to the integral of x to the fourth power from 0 to 1, with 1, 2 and 4 intervals, using exact fractions. It extrapolates twice and reaches exactly 1/5. Some sources date Richardson's volume of Philosophical Transactions to 1910 and others to 1911.",
        instructions = """
            1. Save the example as richardson.py.
            2. Run it: python3 richardson.py
        """,
        example = """
            from fractions import Fraction

            def trapezoid(f, n):
                # Trapezoid rule for f on [0, 1] with n equal intervals, in exact fractions.
                h = Fraction(1, n)
                total = (f(Fraction(0)) + f(Fraction(1))) / 2
                for k in range(1, n):
                    total += f(k * h)
                return total * h

            def f(x):
                return x ** 4

            t1 = trapezoid(f, 1)
            t2 = trapezoid(f, 2)
            t4 = trapezoid(f, 4)
            print(f"T(1) = {t1}")
            print(f"T(2) = {t2}")
            print(f"T(4) = {t4}")
            exact = Fraction(1, 5)
            r12 = (4 * t2 - t1) / 3
            r24 = (4 * t4 - t2) / 3
            r = (16 * r24 - r12) / 15
            print(f"first extrapolation: {r12} error {r12 - exact}")
            print(f"second extrapolation: {r24} error {r24 - exact}")
            print(f"combined: {r} exact: {r == exact}")
        """,
    ),
)
