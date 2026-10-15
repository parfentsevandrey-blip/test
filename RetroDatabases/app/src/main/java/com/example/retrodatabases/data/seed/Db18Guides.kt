package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1860 to 1870: the stability, kinetic, probability, observation and acoustic methods of the decade, each with a program that runs today. */
val db18Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 18,
        kind = Kind.GUIDE,
        section = "Control",
        title = "How Maxwell's governor is tested for stability",
        originalTitle = "On Governors (1868)",
        author = "James Clerk Maxwell",
        year = 1868,
        summary = "Maxwell linearised a governor-controlled engine and found that a third-order characteristic equation is stable when its coefficients are positive and the product of the first two exceeds the third.",
        details = "Maxwell's paper On Governors appeared in the Proceedings of the Royal Society in 1868 and began the mathematical theory of feedback stability. Routh and Hurwitz later gave complete tests, so the condition here covers only the third-order case. The example applies the condition to three sets of coefficients and prints which settings are stable.",
        instructions = """
            1. Save the example as governor.py.
            2. Run it: python3 governor.py
        """,
        example = """
            from fractions import Fraction

            # Characteristic equation of a governor loop: s^3 + a s^2 + b s + c = 0.
            # Maxwell's test: a, b and c positive, and a times b greater than c.
            def stable(a, b, c):
                return a > 0 and b > 0 and c > 0 and a * b > c

            cases = [
                ("slow governor", Fraction(3), Fraction(3), Fraction(1)),
                ("tight loop", Fraction(2), Fraction(2), Fraction(5)),
                ("high gain", Fraction(4), Fraction(2), Fraction(3)),
            ]
            for label, a, b, c in cases:
                print(f"{label}: a={a} b={b} c={c}, a*b={a * b}, stable: {stable(a, b, c)}")
        """,
    ),

    entry(
        databaseId = 18,
        kind = Kind.GUIDE,
        section = "Kinetic theory",
        title = "How Maxwell's speed distribution gives the share of fast molecules",
        originalTitle = "Illustrations of the Dynamical Theory of Gases (1860)",
        author = "James Clerk Maxwell",
        year = 1860,
        summary = "The speeds of gas molecules follow a bell-shaped law, and the share faster than a given speed has a closed form that uses the error function.",
        details = "Maxwell published the speed law in 1860 in the Philosophical Magazine, and his derivation assumed that each velocity component follows a normal curve independently. The example sets the spread of each component to one unit and prints, in closed form, the share faster than one unit, the mean speed and the root-mean-square speed.",
        instructions = """
            1. Save the example as maxwell_speed.py.
            2. Run it: python3 maxwell_speed.py
        """,
        example = """
            import math

            # Units: each velocity component has spread 1, so kT/m = 1.
            # Share of molecules faster than a: erfc(a / sqrt 2) + sqrt(2 / pi) * a * exp(-a * a / 2).
            def faster_than(a):
                return math.erfc(a / math.sqrt(2)) + math.sqrt(2 / math.pi) * a * math.exp(-a * a / 2)

            print(f"fraction faster than 1 unit: {faster_than(1.0):.4f}")
            print(f"fraction slower than 1 unit: {1 - faster_than(1.0):.4f}")
            print(f"mean speed: {math.sqrt(8 / math.pi):.4f} units")
            print(f"rms speed: {math.sqrt(3):.4f} units")
        """,
    ),

    entry(
        databaseId = 18,
        kind = Kind.GUIDE,
        section = "Probability",
        title = "How Chebyshev's inequality bounds the spread of data",
        originalTitle = "Des valeurs moyennes (1867)",
        author = "Pafnuty Chebyshev",
        year = 1867,
        summary = "For any set of values, the share lying at least k standard deviations from the mean is at most one over k squared, whatever the shape of the distribution.",
        details = "Chebyshev published the bound in 1867 in Liouville's journal, and Bienayme had stated a related inequality in 1853, so the priority is disputed. The example takes eight values with mean 5 and standard deviation 2, and checks the bound for four values of k.",
        instructions = """
            1. Save the example as chebyshev.py.
            2. Run it: python3 chebyshev.py
        """,
        example = """
            import math
            from fractions import Fraction

            data = [2, 4, 4, 4, 5, 5, 7, 9]
            n = len(data)
            mean = Fraction(sum(data), n)
            variance = sum((x - mean) ** 2 for x in data) / n
            sd = math.sqrt(variance)
            print(f"mean {mean}, variance {variance}, standard deviation {sd}")
            for k in [Fraction(1), Fraction(3, 2), Fraction(2), Fraction(3)]:
                # Chebyshev: at most 1 / k^2 of the values lie at least k standard deviations from the mean.
                threshold = k * sd
                count = sum(1 for x in data if abs(x - mean) >= threshold)
                share = Fraction(count, n)
                bound = 1 / k ** 2
                holds = share <= bound
                print(f"k={k}: {count} of {n} values at least {threshold} from the mean, share {share}, bound {bound}, holds: {holds}")
        """,
    ),

    entry(
        databaseId = 18,
        kind = Kind.GUIDE,
        section = "Observations",
        title = "How Chauvenet's criterion rejects a doubtful reading",
        originalTitle = "A Manual of Spherical and Practical Astronomy, volume 2 (1863)",
        author = "William Chauvenet",
        year = 1863,
        summary = "A reading is rejected when the expected number of readings at least as far from the mean, out of the sample size, falls below one half.",
        details = "Chauvenet gave the rule in an appendix to his astronomy manual of 1863, using the normal error curve. The example takes six readings, computes the sample mean and standard deviation, and applies the rule to each reading, which rejects one of them.",
        instructions = """
            1. Save the example as chauvenet.py.
            2. Run it: python3 chauvenet.py
        """,
        example = """
            import math

            readings = [9.0, 10.0, 10.0, 11.0, 10.0, 16.0]
            n = len(readings)
            mean = sum(readings) / n
            sample_sd = math.sqrt(sum((x - mean) ** 2 for x in readings) / (n - 1))
            print(f"mean {mean}, sample standard deviation {sample_sd:.4f}")
            for x in readings:
                z = (x - mean) / sample_sd
                # Chauvenet: reject when n times the two-sided normal tail beyond |z| is below one half.
                tail = math.erfc(abs(z) / math.sqrt(2))
                verdict = "reject" if n * tail < 0.5 else "keep"
                print(f"reading {x:.1f}: z = {z:.2f}, {verdict}")
        """,
    ),

    entry(
        databaseId = 18,
        kind = Kind.GUIDE,
        section = "Acoustics",
        title = "How Helmholtz's beats between two tones are counted",
        originalTitle = "Die Lehre von den Tonempfindungen als physiologische Grundlage fuer die Theorie der Musik (1863)",
        author = "Hermann von Helmholtz",
        year = 1863,
        summary = "Two tones close in frequency swell and fade in loudness, and the beat rate is the difference of their frequencies, which the example prints for each close pair of partials.",
        details = "Helmholtz's 1863 book explained that two tones or partials close in frequency produce beats, a slow swelling and fading of loudness, at the rate of their difference. The example takes two tones with fundamentals of 200 and 203 hertz, lists their first three partials, and prints each pair of partials that beat.",
        instructions = """
            1. Save the example as beats.py.
            2. Run it: python3 beats.py
        """,
        example = """
            # Two tones: each has a fundamental frequency in hertz, and its partials are whole-number multiples of it.
            fundamental_a = 200
            fundamental_b = 203
            partials_a = [fundamental_a * k for k in range(1, 4)]
            partials_b = [fundamental_b * k for k in range(1, 4)]
            print("partials of the first tone:", partials_a)
            print("partials of the second tone:", partials_b)
            print(f"the fundamentals beat {abs(fundamental_a - fundamental_b)} times per second")
            # Helmholtz: two partials that are close in frequency beat at the rate of their difference.
            for a in partials_a:
                for b in partials_b:
                    if abs(a - b) <= 10:
                        print(f"partials {a} and {b} beat {abs(a - b)} times per second")
        """,
    ),

)
