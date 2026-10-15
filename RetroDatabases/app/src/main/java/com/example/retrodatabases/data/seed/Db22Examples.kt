package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1820 to 1830: short programs for the number theory, analysis, surveying and railway arithmetic of the decade. */
val db22Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 22,
        kind = Kind.EXAMPLE,
        section = "Number theory",
        title = "Fermat's fifth-power equation searched by brute force",
        originalTitle = "Memoire sur l'impossibilite de quelques equations indeterminees du cinquieme degre (1825)",
        author = "Peter Gustav Lejeune Dirichlet, with Legendre",
        year = 1825,
        summary = "Dirichlet and Legendre showed in 1825 that x^5 + y^5 = z^5 has no solution in nonzero whole numbers, and a search over a small range finds none.",
        details = "Dirichlet's memoir was presented in mid-1825, and Legendre, one of the referees, gave a complete proof a few weeks later. Sources differ on whether the presentation was in June or in July, and the full argument was printed in September 1825. The example searches x <= y <= 30 for a fifth-power sum that is also a fifth power, and it finds none, as the theorem requires.",
        instructions = """
            1. Save the example as fermat5.py.
            2. Run it: python3 fermat5.py
        """,
        example = """
            # Search x^5 + y^5 = z^5 for positive whole numbers with x <= y <= 30.
            LIMIT = 30
            fifth_powers = {n ** 5: n for n in range(1, 200)}
            pairs = 0
            found = []
            for x in range(1, LIMIT + 1):
                for y in range(x, LIMIT + 1):
                    pairs += 1
                    s = x ** 5 + y ** 5
                    if s in fifth_powers:
                        found.append((x, y, fifth_powers[s]))
            print("pairs checked:", pairs)
            print("solutions with x <= y <= 30:", len(found))
        """,
        rare = true,
    ),

    entry(
        databaseId = 22,
        kind = Kind.EXAMPLE,
        section = "Series",
        title = "Abel's binomial series, partial sums at one quarter",
        originalTitle = "Recherches sur la serie binomiale (1826)",
        author = "Niels Henrik Abel",
        year = 1826,
        summary = "The binomial series for (1 + x) raised to one half converges to a square root, and the example computes its first partial sums at x = 1/4 as exact fractions.",
        details = "Abel's 1826 memoir on the binomial series gave a careful account of when such series converge and what they sum to. The example takes x = 1/4 and m = 1/2, so the sum is the square root of 5/4, and prints four partial sums as exact fractions. The errors alternate in sign, and the sums close in on the root.",
        instructions = """
            1. Save the example as binomial.py.
            2. Run it: python3 binomial.py
        """,
        example = """
            from fractions import Fraction as F

            # Binomial coefficient C(m, n) for a rational m, built term by term.
            def binom(m, n):
                result = F(1)
                for k in range(n):
                    result = result * (m - k) / (k + 1)
                return result

            m = F(1, 2)
            x = F(1, 4)
            partial = F(0)
            for n in range(4):
                term = binom(m, n) * x ** n
                partial += term
                print(f"n={n}: term {term}, partial sum {partial}")
            print("compare with sqrt(5/4) = 1.1180339887...")
        """,
    ),

    entry(
        databaseId = 22,
        kind = Kind.EXAMPLE,
        section = "Complex analysis",
        title = "Cauchy's residues of a rational function",
        originalTitle = "Calcul des residus (1825)",
        author = "Augustin-Louis Cauchy",
        year = 1825,
        summary = "Cauchy's residue calculus turns the integral of a function around a closed loop into a sum of residues at the poles inside, and the example computes those residues as exact fractions.",
        details = "The method evaluates a definite integral by summing residues at the poles inside a closed contour, so the integral around the loop is 2 pi i times that sum. The example takes 1 over (z - 1)(z - 2)(z - 3), finds the residues 1/2, -1 and 1/2, and checks that they add to zero, so the integral around a large circle vanishes.",
        instructions = """
            1. Save the example as residues.py.
            2. Run it: python3 residues.py
        """,
        example = """
            from fractions import Fraction as F

            # f(z) = 1 / ((z - 1)(z - 2)(z - 3)) has three simple poles.
            poles = [F(1), F(2), F(3)]

            def residue(pole):
                # At a simple pole the residue is 1 divided by the product of (pole - other pole) over the other poles.
                product = F(1)
                for other in poles:
                    if other != pole:
                        product *= pole - other
                return 1 / product

            res = {p: residue(p) for p in poles}
            for p in poles:
                print(f"residue at {p}: {res[p]}")
            total = sum(res.values())
            print("sum of residues:", total)
            print("integral around a large circle is 2 pi i times the sum, so it is zero:", total == 0)
        """,
    ),

    entry(
        databaseId = 22,
        kind = Kind.EXAMPLE,
        section = "Railway gauge",
        title = "Stephenson's track gauge in millimetres",
        originalTitle = "Stephenson's track gauge in millimetres",
        author = "George Stephenson",
        year = 1830,
        summary = "The gauge of 4 feet 8 and a half inches between the rails, converted exactly from inches to millimetres, is 1435.1 mm.",
        details = "The gauge is usually traced to the early colliery wagonways that George Stephenson worked on, and it was used on the Liverpool and Manchester Railway, which opened in 1830. The example converts the gauge and the older measure of 4 feet 8 inches to millimetres, using exact fractions for the 25.4 millimetres in an inch.",
        instructions = """
            1. Save the example as gauge.py.
            2. Run it: python3 gauge.py
        """,
        example = """
            from fractions import Fraction as F

            MM_PER_INCH = F(254, 10)   # exactly 25.4 millimetres to the inch
            gauge = 4 * 12 + F(17, 2)  # 4 feet 8 and a half inches, in inches: 56.5
            older = 4 * 12 + 8         # 4 feet 8 inches, in inches: 56
            print("4 ft 8 1/2 in:", float(gauge * MM_PER_INCH), "mm")
            print("4 ft 8 in:", float(older * MM_PER_INCH), "mm")
            print("difference:", float((gauge - older) * MM_PER_INCH), "mm")
        """,
    ),

    entry(
        databaseId = 22,
        kind = Kind.EXAMPLE,
        section = "Surveying",
        title = "Spherical excess of a survey triangle, exact in degrees",
        originalTitle = "Disquisitiones generales circa superficies curvas (1827)",
        author = "Carl Friedrich Gauss",
        year = 1827,
        summary = "On a sphere the three angles of a triangle add to more than 180 degrees by the spherical excess, which is also the triangle's area as a share of the whole sphere.",
        details = "Gauss's 1827 study of curved surfaces links the excess of a triangle on a surface to its curvature, which on a sphere gives the area of the triangle. The example takes three angles, finds the excess, and gives the triangle's share of the whole sphere as an exact fraction.",
        instructions = """
            1. Save the example as excess.py.
            2. Run it: python3 excess.py
        """,
        example = """
            from fractions import Fraction as F

            # Three angles, in degrees, of a triangle drawn on a sphere.
            angles = [F(100), F(60), F(50)]
            excess = sum(angles) - 180        # spherical excess in degrees
            print("angle sum:", sum(angles), "degrees")
            print("spherical excess:", excess, "degrees")
            # The whole sphere has excess 720 degrees (4 pi steradians), so the triangle covers excess / 720 of it.
            share = excess / 720
            print("share of the sphere:", share)
        """,
    ),

    entry(
        databaseId = 22,
        kind = Kind.EXAMPLE,
        section = "Thermodynamics",
        title = "Carnot's efficiency limit for two heat reservoirs",
        originalTitle = "Reflexions sur la puissance motrice du feu et sur les machines propres a developper cette puissance (1824)",
        author = "Sadi Carnot",
        year = 1824,
        summary = "Carnot argued that no engine working between two heat reservoirs can beat an ideal reversible cycle, and in modern units its best efficiency is one minus the cold temperature over the hot one.",
        details = "Carnot's 1824 memoir reasoned from the caloric theory of heat, which was later replaced, but the limit he derived survived into thermodynamics. The example computes the best efficiency for a few pairs of temperatures in kelvin as exact fractions, and 500 kelvin hot with 300 kelvin cold gives 2/5.",
        instructions = """
            1. Save the example as carnot.py.
            2. Run it: python3 carnot.py
        """,
        example = """
            from fractions import Fraction as F

            def carnot_efficiency(t_hot, t_cold):
                # Best possible efficiency of an engine between two reservoirs, with temperatures in kelvin.
                return 1 - F(t_cold, t_hot)

            for hot, cold in [(500, 300), (400, 300), (1000, 300), (600, 600)]:
                print(f"hot {hot} K, cold {cold} K: efficiency {carnot_efficiency(hot, cold)}")
            print("an engine at one third is allowed:", F(1, 3) <= carnot_efficiency(500, 300))
        """,
    ),
)
