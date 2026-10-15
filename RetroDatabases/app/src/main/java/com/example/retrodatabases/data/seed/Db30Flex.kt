package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1740 to 1750: a balance of inertia on a slope, the drag of air on a ball and a cask's volume by parabolas. */
val db30Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 30,
        kind = Kind.FLEX,
        section = "Mechanics",
        title = "D'Alembert's principle on an inclined plane",
        originalTitle = "Traite de dynamique",
        author = "Jean le Rond d'Alembert",
        year = 1743,
        summary = "A block on a slope is in balance when its inertial force, taken opposite to its acceleration, cancels the part of its weight along the plane.",
        details = "D'Alembert's Traite de dynamique of 1743 turned problems of motion into problems of balance by adding the inertial force, which is the principle now named after him. The example takes a slope with sine 3/5, a block of mass 2 under gravity 10, and finds the acceleration of 6 that makes the inertial force cancel the weight's part along the plane.",
        instructions = """
            1. Save the example as dalembert_principle.py.
            2. Run it: python3 dalembert_principle.py
        """,
        example = """
            from fractions import Fraction as F

            G = F(10)          # gravity in the example's units
            SIN = F(3, 5)      # sine of the slope
            MASS = 2           # mass of the block, in kilograms

            weight_along = MASS * G * SIN     # the part of the weight along the plane
            a = weight_along / MASS           # acceleration down the plane, found from the balance
            print("acceleration down the plane:", a)
            print("inertial force balances the weight:", weight_along - MASS * a == 0)
        """,
    ),

    entry(
        databaseId = 30,
        kind = Kind.FLEX,
        section = "Gunnery",
        title = "Robins's air drag on a ball",
        originalTitle = "New Principles of Gunnery",
        author = "Benjamin Robins",
        year = 1742,
        summary = "Air resistance that grows with the square of the speed slows a ball along its path, and the program prints the speed at two distances and the distance at which the speed halves.",
        details = "Robins's book of 1742 reported experiments on the resistance of air to fast balls, and how resistance rises with speed was central to gunnery. The example uses the simple square law, under which the speed falls as v0 over one plus k v0 s, with v0 = 4 and k/m = 1/2, and finds that the speed halves at a distance of 1/2 unit.",
        instructions = """
            1. Save the example as robins_drag.py.
            2. Run it: python3 robins_drag.py
        """,
        example = """
            from fractions import Fraction as F

            V0 = F(4)             # launch speed, in the example's units
            K_OVER_M = F(1, 2)    # drag constant divided by the mass of the ball

            def speed_after(s):
                # With drag proportional to the square of the speed, 1/v = 1/v0 + (k/m) s, so v = v0 / (1 + (k/m) v0 s).
                return V0 / (1 + K_OVER_M * V0 * s)

            def distance_to_halve():
                # The distance at which 1 + (k/m) v0 s equals 2, which is when the speed is half the launch speed.
                return (2 - 1) / (K_OVER_M * V0)

            print("speed after 1/2 unit:", speed_after(F(1, 2)))
            print("speed after 1 unit:", speed_after(F(1)))
            print("distance to halve the speed:", distance_to_halve())
            print("speed at the halving point is half the launch speed:", speed_after(distance_to_halve()) == V0 / 2)
        """,
    ),

    entry(
        databaseId = 30,
        kind = Kind.FLEX,
        section = "Continued fractions",
        title = "Euler's continued fraction for e, from the 1748 Introductio",
        originalTitle = "Introductio in analysin infinitorum",
        author = "Leonhard Euler",
        year = 1748,
        summary = "The continued fraction of e has the terms 2, 1, 2, 1, 1, 4 and so on, and the program prints its first ten terms with exact fractions.",
        details = "Euler's Introductio of 1748 gives the continued fraction of e, in which every third term after the first is an even number, 2, 4, 6 and so on, and the terms between are 1. The example replaces e by the exact sum of 1/k! for k up to 20, finds the first ten terms, and checks the tenth convergent, 1457/536.",
        instructions = """
            1. Save the example as euler_cf.py.
            2. Run it: python3 euler_cf.py
        """,
        example = """
            from fractions import Fraction as F

            from math import factorial

            # e as an exact rational: the sum of 1/k! for k up to 20, accurate far beyond the ten terms printed.
            e_approx = sum(F(1, factorial(k)) for k in range(21))

            def continued_fraction(x, count):
                # Take the integer part, then invert the fractional part, using exact fractions.
                terms = []
                for _ in range(count):
                    a = x.numerator // x.denominator
                    terms.append(a)
                    frac = x - a
                    if frac == 0:
                        break
                    x = 1 / frac
                return terms

            def convergent(terms):
                # The convergent p/q from p_k = a_k p_(k-1) + p_(k-2), and the same recurrence for q.
                p2, p1 = 0, 1
                q2, q1 = 1, 0
                for a in terms:
                    p2, p1 = p1, a * p1 + p2
                    q2, q1 = q1, a * q1 + q2
                return F(p1, q1)

            terms = continued_fraction(e_approx, 10)
            print("first ten terms:", terms)
            print("tenth convergent:", convergent(terms))
            print("as a decimal:", f"{float(convergent(terms)):.5f}")
        """,
    ),
)
