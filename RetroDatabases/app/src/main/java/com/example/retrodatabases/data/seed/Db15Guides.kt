package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1890 to 1900: operator methods, series, stability, p-adic lifting and integrals, each with a program that runs today. */
val db15Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 15,
        kind = Kind.GUIDE,
        section = "Operational calculus",
        title = "How Heaviside's expansion theorem inverts a rational operator",
        originalTitle = "On operators in physical mathematics (1892)",
        author = "Oliver Heaviside",
        year = 1892,
        summary = "Heaviside replaces differentiation by a symbol p, so a rational function of p inverts into a sum of exponentials, one for each pole.",
        details = "The example takes 1 over (p+1)(p+2), finds the coefficient of each simple pole with exact fractions, and checks the sum at t = 1 against the exponentials. The method was controversial for years, and later work gave it a rigorous footing.",
        instructions = """
            1. Save the example as heaviside.py.
            2. Run it: python3 heaviside.py
        """,
        example = """
            from fractions import Fraction
            import math

            # F(p) = 1 / ((p + 1)(p + 2)) = 1 / (p^2 + 3p + 2); the poles are p = -1 and p = -2.
            poles = [-1, -2]

            def derivative(p):
                # Derivative of the denominator p^2 + 3p + 2.
                return 2 * p + 3

            # Heaviside's expansion: each simple pole gives N(p) / D'(p) times exp(p t), with N(p) = 1 here.
            coefficients = {}
            for p in poles:
                coefficients[p] = Fraction(1, derivative(p))
                print(f"coefficient at p = {p}: {coefficients[p]}")

            t = 1.0
            value = sum(float(coefficients[p]) * math.exp(p * t) for p in poles)
            print(f"f(1) = {value:.6f}")
        """,
    ),

    entry(
        databaseId = 15,
        kind = Kind.GUIDE,
        section = "Fourier series",
        title = "How a partial Fourier sum overshoots a jump",
        originalTitle = "Letters on the overshoot at a jump, Nature (1898 and 1899)",
        author = "Josiah Willard Gibbs, after Albert Michelson",
        year = 1899,
        summary = "Adding more terms of a Fourier series for a square wave overshoots the jump by a fixed fraction of its height, which the example computes from sin t over t.",
        details = "Michelson and Stratton saw the overshoot in a harmonic analyser in 1898, and Gibbs explained it in Nature in 1898 and 1899, and accounts differ on the dates of his letters. The example integrates sin t over t from 0 to pi with Simpson's rule, then multiplies by 2 over pi to get the limit of the overshoot ratio.",
        instructions = """
            1. Save the example as gibbs.py.
            2. Run it: python3 gibbs.py
        """,
        example = """
            import math

            def sinc(t):
                # sin t / t, with the value 1 at t = 0.
                if t == 0:
                    return 1.0
                return math.sin(t) / t

            def simpson(f, a, b, n):
                # Composite Simpson rule with n even intervals.
                h = (b - a) / n
                total = f(a) + f(b)
                for k in range(1, n):
                    weight = 4 if k % 2 == 1 else 2
                    total += weight * f(a + k * h)
                return total * h / 3

            si = simpson(sinc, 0.0, math.pi, 1000)
            print(f"Si(pi) = {si:.6f}")
            gibbs = 2 / math.pi * si
            print(f"Gibbs constant (2/pi) Si(pi) = {gibbs:.4f}")
            print(f"overshoot above the jump level: {gibbs - 1:.4f}")
        """,
    ),

    entry(
        databaseId = 15,
        kind = Kind.GUIDE,
        section = "Stability",
        title = "How a decreasing function shows a rest point is stable",
        originalTitle = "The General Problem of the Stability of Motion (1892)",
        author = "Aleksandr Lyapunov",
        year = 1892,
        summary = "A function that shrinks at every step of the motion is a Lyapunov function, and its decrease shows that nearby states are drawn back to the rest point.",
        details = "Lyapunov's 1892 doctoral thesis introduced this direct method, and the example applies it to the map x goes to x minus x cubed over 4, with the function V equal to x squared. It uses exact fractions and checks that V falls at each of the first two steps.",
        instructions = """
            1. Save the example as lyapunov.py.
            2. Run it: python3 lyapunov.py
        """,
        example = """
            from fractions import Fraction

            def step(x):
                # One step of the discrete system x -> x - x^3 / 4, which has a rest point at 0.
                return x - x ** 3 / 4

            x = Fraction(1)
            previous = x * x
            print(f"x0 = {x}, V0 = {previous}")
            for n in (1, 2):
                x = step(x)
                V = x * x
                print(f"x{n} = {x}, V{n} = {V}, decreased: {V < previous}")
                previous = V
        """,
    ),

    entry(
        databaseId = 15,
        kind = Kind.GUIDE,
        section = "Number theory",
        title = "How Hensel's lifting finds a square root modulo 625",
        originalTitle = "Ueber eine neue Begruendung der Theorie der algebraischen Zahlen (1897)",
        author = "Kurt Hensel",
        year = 1897,
        summary = "A root of a polynomial modulo a prime can be lifted to modulo its square, cube and fourth power, one step of accuracy at a time.",
        details = "Hensel introduced p-adic numbers in 1897, and this lifting step is the basic tool for building them. The example lifts a square root of 6 from modulo 5 through 25, 125 and 625, and reaches 516 with exact integer arithmetic.",
        instructions = """
            1. Save the example as hensel.py.
            2. Run it: python3 hensel.py
        """,
        example = """
            a = 6
            p = 5
            # First root modulo p: the first residue whose square leaves the same remainder as a.
            x = next(r for r in range(p) if (r * r - a) % p == 0)
            print(f"mod {p}: x = {x}")
            modulus = p
            for k in range(2, 5):
                new_modulus = modulus * p
                # Lift: try x + t * modulus for t = 0 to p - 1 until the square is right modulo the new modulus.
                for t in range(p):
                    y = x + t * modulus
                    if (y * y - a) % new_modulus == 0:
                        x = y
                        break
                else:
                    raise ValueError("no lift")
                modulus = new_modulus
                print(f"mod {modulus}: x = {x}")
            print("check:", (x * x - a) % modulus == 0)
        """,
    ),

    entry(
        databaseId = 15,
        kind = Kind.GUIDE,
        section = "Continued fractions",
        title = "How a Stieltjes integral is summed by weights",
        originalTitle = "Recherches sur les fractions continues (1894)",
        author = "Thomas Joannes Stieltjes",
        year = 1894,
        summary = "A Stieltjes integral sums a function times the step in another increasing function, and for x against x squared it gives 2/3.",
        details = "Stieltjes introduced the integral in his 1894 memoir on continued fractions. The example uses right endpoints on n equal parts with exact fractions, and shows that the sums approach 2/3 as n grows.",
        instructions = """
            1. Save the example as stieltjes.py.
            2. Run it: python3 stieltjes.py
        """,
        example = """
            from fractions import Fraction

            def stieltjes_sum(n):
                # Integral of f(x) = x against g(x) = x squared on [0, 1], with right endpoints of n equal parts.
                total = Fraction(0)
                for i in range(1, n + 1):
                    right, left = Fraction(i, n), Fraction(i - 1, n)
                    total += right * (right ** 2 - left ** 2)
                return total

            for n in (2, 4, 10, 100):
                s = stieltjes_sum(n)
                print(f"n = {n}: sum {s} = {float(s):.5f}")
            print(f"limit: 2/3 = {2 / 3:.5f}")
        """,
    ),
)
