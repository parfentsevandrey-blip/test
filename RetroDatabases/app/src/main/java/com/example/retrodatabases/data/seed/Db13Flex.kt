package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1910 to 1920: a digit-filtered harmonic sum, a sum over twin primes and an escape-time iteration, each in a few lines. */
val db13Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 13,
        kind = Kind.FLEX,
        section = "Digits",
        title = "The harmonic series without the digit 9",
        originalTitle = "Harmonic series with the digit 9 left out, a convergent series (Kempner, 1914)",
        author = "Aristide Joseph Kempner",
        year = 1914,
        summary = "Adding the reciprocals of every whole number that contains no digit 9 gives a finite total, although the full harmonic series grows without bound.",
        details = "Kempner's series is a standard curiosity of 1914: the numbers with no 9 thin out fast enough that their reciprocals converge. The example counts the numbers with one, two and three digits that contain no 9, which are 8, 72 and 648, that is 8 times 9 to the power of (digits - 1). It then sums the reciprocals of the one-digit ones exactly, as 761/280.",
        instructions = """
            1. Save the example as kempner.py.
            2. Run it: python3 kempner.py
        """,
        example = """
            from fractions import Fraction


            def has_nine(n):
                return "9" in str(n)


            counts = []
            for digits in (1, 2, 3):
                block = [n for n in range(10 ** (digits - 1), 10 ** digits) if not has_nine(n)]
                counts.append(len(block))
                print(f"{digits}-digit numbers without a 9: {len(block)}")
            print("counts equal 8 times 9 to the power of (digits - 1):", counts == [8 * 9 ** d for d in range(3)])
            print("sum of reciprocals of the one-digit ones:", sum(Fraction(1, n) for n in range(1, 10) if not has_nine(n)))
        """,
    ),

    entry(
        databaseId = 13,
        kind = Kind.FLEX,
        section = "Primes",
        title = "Twin primes and the sum of their reciprocals",
        originalTitle = "Brun's sieve and the convergence of the reciprocal sum of twin primes (Brun, 1919)",
        author = "Viggo Brun",
        year = 1919,
        summary = "The reciprocals of all twin primes below 100 add up to about 1.331, and Brun's 1919 work showed that the sum over every twin prime is finite.",
        details = "Brun's result shows that the reciprocals of twin primes add up to a finite total, now called Brun's constant, which is about 1.902. Most sources date the proof to 1919. The example lists the eight twin prime pairs below 100 and sums the reciprocals of both members exactly, then prints the sum to three decimals.",
        rare = true,
        instructions = """
            1. Save the example as brun.py.
            2. Run it: python3 brun.py
        """,
        example = """
            from fractions import Fraction


            def is_prime(n):
                return n > 1 and all(n % d for d in range(2, int(n ** 0.5) + 1))


            pairs = [(p, p + 2) for p in range(2, 100) if is_prime(p) and is_prime(p + 2)]
            total = sum(Fraction(1, p) + Fraction(1, q) for p, q in pairs)
            print("twin prime pairs below 100:", len(pairs))
            print("sum of reciprocals:", f"{float(total):.3f}")
            print("largest pair:", pairs[-1])
        """,
    ),

    entry(
        databaseId = 13,
        kind = Kind.FLEX,
        section = "Iteration",
        title = "Iterating z squared plus c: the escape test",
        originalTitle = "Memoir on the iteration of rational functions, Journal de Mathematiques Pures et Appliquees (Julia, 1918)",
        author = "Gaston Julia",
        year = 1918,
        summary = "Julia's 1918 memoir asks which starting points stay bounded under repeated iteration, and the escape test answers it point by point for z squared plus c.",
        details = "Julia's 1918 memoir on the iteration of rational functions won the Academy's prize that year, and Fatou published on the same subject in 1917 and 1919. The example iterates z squared plus c from zero and reports the step at which the orbit leaves the disc of radius 2, or that it stays bounded, for six values of c.",
        instructions = """
            1. Save the example as julia.py.
            2. Run it: python3 julia.py
        """,
        example = """
            def escape_time(c, limit=50):
                # Iterate z = z * z + c from zero; report the step at which |z| goes beyond 2, or None if it never does.
                z = 0j
                for n in range(1, limit + 1):
                    z = z * z + c
                    if abs(z) > 2:
                        return n
                return None


            for c in [0, -1, 1, -2, 0.5, 1j]:
                n = escape_time(c)
                print(f"c = {c}: " + ("bounded" if n is None else f"escapes at step {n}"))
        """,
    ),
)
