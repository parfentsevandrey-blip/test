package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1900 to 1910: short programs for the tests, paradoxes, models and measures of the decade. */
val db14Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 14,
        kind = Kind.EXAMPLE,
        section = "Statistics",
        title = "Pearson's chi-square for a die rolled 120 times",
        originalTitle = "On the criterion that a given system of deviations from the probable in the case of a correlated system of variables is such that it can be reasonably supposed to have arisen from random sampling (1900)",
        author = "Written as a teaching example",
        year = 1900,
        summary = "Compares observed face counts with the counts expected from a fair die, computes the chi-square statistic and checks it against a table value.",
        details = "Karl Pearson's test of 1900 is worked on 120 rolls. The observed counts give a statistic of 5 with five degrees of freedom, below the 5 percent critical value of 11.07, so a fair die is not rejected. The example prints each step.",
        instructions = """
            1. Save the example as chi_square.py.
            2. Run it: python3 chi_square.py
        """,
        example = """
            from fractions import Fraction

            observed = [25, 17, 15, 23, 24, 16]
            expected = Fraction(sum(observed), len(observed))
            print(f"expected count per face: {expected}")
            chi2 = sum((o - expected) ** 2 / expected for o in observed)
            print(f"chi-square = {chi2}")
            df = len(observed) - 1
            print(f"degrees of freedom = {df}")
            critical = 11.07  # chi-square table value for 5 degrees of freedom at the 5 percent level
            print(f"critical value at 5 percent = {critical}")
            print("fair die rejected:", chi2 > critical)
        """,
    ),

    entry(
        databaseId = 14,
        kind = Kind.EXAMPLE,
        section = "Paradoxes",
        title = "Russell's paradox on a small finite universe",
        originalTitle = "Russell's paradox, found in 1901 (letter to Frege, 1902)",
        author = "Written as a teaching example",
        year = 1901,
        summary = "The set of all names that do not contain themselves is built on a finite universe, and adding that set to the universe gives a contradiction.",
        details = "A universe of three names is given, one of which is a set containing itself. The example builds the set of names that do not contain themselves, places it in the universe, and shows that the definition and the stored membership disagree. Russell found the same contradiction in 1901 for sets in general.",
        instructions = """
            1. Save the example as russell.py.
            2. Run it: python3 russell.py
        """,
        example = """
            # A finite universe of names. Each name maps to the set of names it contains.
            members = {
                "empty": set(),
                "one": {"empty"},
                "Q": {"Q"},
            }

            # Russell's set: the names that do not contain themselves.
            R = {x for x in members if x not in members[x]}
            print("R = {" + ", ".join(sorted(R)) + "}")

            # Add R to the universe with R as its contents, then read the definition again for the name R.
            members["R"] = R
            definition_says = "R" not in members["R"]
            print("definition puts R in R:", definition_says)
            print("R is in R as stored:", "R" in R)
            print("contradiction:", definition_says != ("R" in R))
        """,
    ),

    entry(
        databaseId = 14,
        kind = Kind.EXAMPLE,
        section = "Linear algebra",
        title = "Gram-Schmidt on three small integer vectors",
        originalTitle = "Orthogonalisation process, as set out by Erhard Schmidt (1907)",
        author = "Written as a teaching example",
        year = 1907,
        summary = "Each vector loses its parts along the earlier ones, and the three results are orthogonal, computed in exact fractions.",
        details = "Three vectors with integer entries are orthogonalised one at a time, and every result is kept as an exact fraction. The example prints the three orthogonal vectors and checks that all three pairs have dot product zero.",
        instructions = """
            1. Save the example as gram_schmidt.py.
            2. Run it: python3 gram_schmidt.py
        """,
        example = """
            from fractions import Fraction

            def dot(u, v):
                return sum(x * y for x, y in zip(u, v))

            def orthogonalise(vectors):
                basis = []
                for v in vectors:
                    w = [Fraction(x) for x in v]
                    for u in basis:
                        c = dot(w, u) / dot(u, u)
                        w = [wi - c * ui for wi, ui in zip(w, u)]
                    basis.append(w)
                return basis

            vectors = [(1, 1, 0), (1, 0, 1), (0, 1, 1)]
            basis = orthogonalise(vectors)
            for i, u in enumerate(basis, start=1):
                print(f"u{i} = " + " ".join(str(x) for x in u))
            pairs = [(0, 1), (0, 2), (1, 2)]
            print("orthogonal check:", all(dot(basis[i], basis[j]) == 0 for i, j in pairs))
        """,
    ),

    entry(
        databaseId = 14,
        kind = Kind.EXAMPLE,
        section = "Population genetics",
        title = "Hardy-Weinberg after one generation of mating",
        originalTitle = "Mendelian proportions in a mixed population (Hardy, 1908)",
        author = "Written as a teaching example",
        year = 1908,
        summary = "After one generation of random mating the genotype frequencies settle at the squares and products of the allele frequency, and they stay there.",
        details = "Starting frequencies of 1/2, 1/5 and 3/10 give an allele frequency of 3/5. After one generation the frequencies become 9/25, 12/25 and 4/25, and a second generation leaves them unchanged. The example prints the frequencies in exact fractions.",
        instructions = """
            1. Save the example as hardy_weinberg.py.
            2. Run it: python3 hardy_weinberg.py
        """,
        example = """
            from fractions import Fraction as F

            def show(g):
                return " ".join(f"{k}={v}" for k, v in g.items())

            def allele_p(g):
                return g["AA"] + g["Aa"] / 2

            def mate(g):
                # Random mating: the next generation's genotype frequencies follow from the allele frequency p.
                p = allele_p(g)
                q = 1 - p
                return {"AA": p * p, "Aa": 2 * p * q, "aa": q * q}

            gen0 = {"AA": F(1, 2), "Aa": F(1, 5), "aa": F(3, 10)}
            gen1 = mate(gen0)
            gen2 = mate(gen1)
            print(f"generation 0: {show(gen0)}, p = {allele_p(gen0)}")
            print(f"generation 1: {show(gen1)}, p = {allele_p(gen1)}")
            print(f"generation 2: {show(gen2)}, p = {allele_p(gen2)}")
            print("equilibrium reached after one generation:", gen1 == gen2)
        """,
    ),

    entry(
        databaseId = 14,
        kind = Kind.EXAMPLE,
        section = "Radiation",
        title = "Planck's peak wavelength by a numeric search",
        originalTitle = "Planck's law of black-body radiation (1900)",
        author = "Written as a teaching example",
        year = 1900,
        summary = "Planck's law gives the spectrum of a hot body, and a golden-section search finds the wavelength of its brightest point.",
        details = "The example searches for the maximum of Planck's spectral radiance at 5800 kelvin, close to the surface temperature of the Sun. It checks the product of the peak wavelength and the temperature against Wien's constant. The search gives 499.6 nm, and the constant matches the published value to four digits.",
        instructions = """
            1. Save the example as planck.py.
            2. Run it: python3 planck.py
        """,
        example = """
            import math

            h = 6.62607015e-34      # Planck constant, J s
            c = 299792458.0         # speed of light, m/s
            k = 1.380649e-23        # Boltzmann constant, J/K
            T = 5800.0              # roughly the surface temperature of the Sun, K

            def radiance(lam):
                # Planck's law: spectral radiance per unit wavelength at wavelength lam, in metres.
                return (2 * h * c * c / lam ** 5) / (math.exp(h * c / (lam * k * T)) - 1)

            # Golden-section search for the maximum between 100 nm and 2000 nm.
            lo, hi = 100e-9, 2000e-9
            g = (math.sqrt(5) - 1) / 2
            a = hi - g * (hi - lo)
            b = lo + g * (hi - lo)
            for _ in range(200):
                if radiance(a) < radiance(b):
                    lo = a
                else:
                    hi = b
                a = hi - g * (hi - lo)
                b = lo + g * (hi - lo)
            peak = (lo + hi) / 2
            print(f"peak wavelength at {T:.0f} K: {peak * 1e9:.1f} nm")
            print(f"Wien constant from the search: {peak * T * 1000:.4f} (millimetre kelvin)")
        """,
    ),

    entry(
        databaseId = 14,
        kind = Kind.EXAMPLE,
        section = "Statistics",
        title = "Yule's Q for two-by-two tables",
        originalTitle = "On the association of attributes in statistics (Yule, 1900)",
        author = "Written as a teaching example",
        year = 1900,
        summary = "Yule's Q is +1 for perfect association, 0 for independence and -1 for reversed association, and comes from the four cells of a table.",
        details = "The example computes Q with exact fractions for four tables: a first table, an independent table, a perfect table and a reversed table. The first table gives 5/7.",
        rare = true,
        instructions = """
            1. Save the example as yule.py.
            2. Run it: python3 yule.py
        """,
        example = """
            from fractions import Fraction

            def yule_q(a, b, c, d):
                # Yule's Q for the table [[a, b], [c, d]]: +1 for perfect association, 0 for independence, -1 for reversed.
                return Fraction(a * d - b * c, a * d + b * c)

            tables = [
                ("first table", (30, 10, 20, 40)),
                ("independent table", (10, 10, 10, 10)),
                ("perfect table", (20, 0, 0, 20)),
                ("reversed table", (10, 30, 40, 20)),
            ]
            for label, (a, b, c, d) in tables:
                print(f"{label}: Q = {yule_q(a, b, c, d)}")
        """,
    ),
)
