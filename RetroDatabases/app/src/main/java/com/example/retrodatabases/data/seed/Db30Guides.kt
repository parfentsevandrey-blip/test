package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1740 to 1750: area by parabolas, a vibrating string, a conjecture of primes, the flattening of the Earth and the stability of a ship. */
val db30Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 30,
        kind = Kind.GUIDE,
        section = "Integration",
        title = "Simpson's rule for the area under a curve",
        originalTitle = "Mathematical Dissertations on a Variety of Physical and Analytical Subjects",
        author = "Thomas Simpson",
        year = 1743,
        summary = "Three ordinates of a curve are joined by a parabola, and the area under that parabola is taken as the area under the curve.",
        details = "Simpson's book of 1743 gives the rule that carries his name, although Cotes and others had similar rules earlier, so accounts differ on how much credit is his. The example applies the rule to x to the fourth power from 0 to 2 with two strips and with four, giving 20/3 and 77/12 against the exact 32/5, and checks that the rule is exact for a cubic.",
        instructions = """
            1. Save the example as simpson.py.
            2. Run it: python3 simpson.py
        """,
        example = """
            from fractions import Fraction as F

            def simpson(f, a, b, n):
                # n must be even. The end ordinates count once, the odd ones four times and the even ones twice.
                h = F(b - a, n)
                total = f(a) + f(b)
                for k in range(1, n):
                    x = a + k * h
                    total += (4 if k % 2 else 2) * f(x)
                return total * h / 3

            def f(x):
                return x ** 4

            print("n = 2:", simpson(f, F(0), F(2), 2))
            print("n = 4:", simpson(f, F(0), F(2), 4))
            print("exact:", F(2 ** 5, 5))
            g = lambda x: x ** 3 + 2 * x
            print("cubic exact with n = 2:", simpson(g, F(0), F(2), 2) == F(2 ** 4, 4) + 2 * F(2 ** 2, 2))
        """,
    ),

    entry(
        databaseId = 30,
        kind = Kind.GUIDE,
        section = "Waves",
        title = "D'Alembert's vibrating string",
        originalTitle = "Recherches sur la courbe que forme une corde tendue mise en vibration",
        author = "Jean le Rond d'Alembert",
        year = 1746,
        summary = "A string released from rest splits into two copies of its starting shape that travel in opposite directions, and the sum of the copies satisfies the wave equation.",
        details = "D'Alembert's memoir on the vibrating string appeared in the Berlin Academy's memoirs, and histories date it to 1746 or to 1747. The example takes an initial shape x squared plus x and a wave speed of 2, and checks at one point that the second differences give u_xx = 2 and u_tt = 8, so that the wave equation holds.",
        instructions = """
            1. Save the example as dalembert.py.
            2. Run it: python3 dalembert.py
        """,
        example = """
            from fractions import Fraction as F

            C = 2                       # wave speed, in the example's units

            def phi(s):
                # Initial displacement of the string, a parabola: phi(s) = s^2 + s.
                return s * s + s

            def u(x, t):
                # d'Alembert's solution for a string released from rest: half the sum of two travelling copies of phi.
                return F(phi(x - C * t) + phi(x + C * t), 2)

            h = F(1, 1000)              # a small step for the second differences
            x0, t0 = F(1, 3), F(1, 5)   # a point on the string at a time

            uxx = (u(x0 + h, t0) - 2 * u(x0, t0) + u(x0 - h, t0)) / h ** 2
            utt = (u(x0, t0 + h) - 2 * u(x0, t0) + u(x0, t0 - h)) / h ** 2

            print("displacement at t = 0 matches phi:", u(x0, 0) == phi(x0))
            print("uxx:", uxx)
            print("utt:", utt)
            print("wave equation holds:", utt == C * C * uxx)
        """,
    ),

    entry(
        databaseId = 30,
        kind = Kind.GUIDE,
        section = "Number theory",
        title = "Goldbach's conjecture for even numbers",
        originalTitle = "Letter to Euler, 1742",
        author = "Christian Goldbach",
        year = 1742,
        summary = "Every even number from 4 upwards is a sum of two primes, which the program checks for each even number up to 30 by listing its pairs.",
        details = "In his letter of 1742 to Euler, Goldbach proposed that every number greater than 2 is a sum of three primes. Euler replied that this is equivalent to the claim that every even number is a sum of two primes, which is the form the example checks.",
        instructions = """
            1. Save the example as goldbach.py.
            2. Run it: python3 goldbach.py
        """,
        example = """
            def is_prime(n):
                return n > 1 and all(n % d != 0 for d in range(2, int(n ** 0.5) + 1))

            def pairs(n):
                # Goldbach's pairs: primes p <= q with p + q = n.
                return [(p, n - p) for p in range(2, n // 2 + 1) if is_prime(p) and is_prime(n - p)]

            for n in range(4, 31, 2):
                print(f"{n}: {len(pairs(n))}")
            print("every even number from 4 to 30 has a pair:", all(pairs(n) for n in range(4, 31, 2)))
            print("pairs for 30:", pairs(30))
        """,
    ),

    entry(
        databaseId = 30,
        kind = Kind.GUIDE,
        section = "Geodesy",
        title = "Clairaut's relation for the flattening of the Earth",
        originalTitle = "Theorie de la figure de la Terre",
        author = "Alexis Clairaut",
        year = 1743,
        summary = "The gravity excess at the pole, as a fraction of the equator value, is five halves of the centrifugal ratio minus the flattening, so the program finds the pole's gravity from the two ratios.",
        details = "Clairaut's book of 1743 gave this relation for a rotating body in equilibrium, and it became the standard link between the flattening of the Earth and its gravity. The example takes a centrifugal ratio of 1/200 and a flattening of 1/300, finds the gravity excess as 11/1200, and computes the pole gravity from an equator value of 49/5.",
        instructions = """
            1. Save the example as clairaut.py.
            2. Run it: python3 clairaut.py
        """,
        example = """
            from fractions import Fraction as F

            M = F(1, 200)                  # ratio of the centrifugal force to gravity at the equator
            FLAT = F(1, 300)               # flattening, (equator - pole) / equator
            EXCESS = F(5, 2) * M - FLAT    # Clairaut's relation: (g_pole - g_equator) / g_equator

            print("gravity excess at the pole, as a fraction of the equator value:", EXCESS)
            print("as a decimal:", f"{float(EXCESS):.4f}")
            G_EQ = F(49, 5)                # gravity at the equator in the example's units
            G_POLE = G_EQ * (1 + EXCESS)
            print("gravity at the pole:", G_POLE)
        """,
    ),

    entry(
        databaseId = 30,
        kind = Kind.GUIDE,
        section = "Naval architecture",
        title = "Bouguer's metacentre for a box-shaped ship",
        originalTitle = "Traite du navire, de sa construction et de ses mouvemens",
        author = "Pierre Bouguer",
        year = 1746,
        summary = "A ship stays upright when its metacentre lies above its centre of gravity, and the program finds the metacentric height of a box-shaped hull for three loads.",
        details = "Bouguer's treatise of 1746 introduced the metacentre, the point where the line of buoyancy crosses the centre line when a ship heels. The example takes the height of the metacentre above the centre of buoyancy as I over V, which is 3/2 for a box of breadth 6 and draught 2, and subtracts the height of the centre of gravity for three loads.",
        instructions = """
            1. Save the example as bouguer.py.
            2. Run it: python3 bouguer.py
        """,
        example = """
            from fractions import Fraction as F

            def metacentric_height(length, breadth, draft, kg):
                # Box-shaped hull: waterplane second moment I = L b^3 / 12, displaced volume V = L b d.
                I = F(length * breadth ** 3, 12)
                V = length * breadth * draft
                BM = I / V                      # metacentre above the centre of buoyancy
                BG = kg - F(draft, 2)           # centre of gravity above the centre of buoyancy
                return BM - BG

            for kg in (F(1), F(3, 2), F(5, 2)):
                gm = metacentric_height(10, 6, 2, kg)
                print(f"centre of gravity at {kg} m: GM = {gm} m, stable: {gm > 0}")
        """,
    ),
)
