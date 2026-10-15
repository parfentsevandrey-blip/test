package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1850 to 1860: number-theory methods and a signal-code numbering, each with a program that runs today. */
val db19Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 19,
        kind = Kind.GUIDE,
        section = "Number theory",
        title = "How Riemann's zeta function takes negative values",
        originalTitle = "Ueber die Anzahl der Primzahlen unter einer gegebenen Grosse (1859)",
        author = "Bernhard Riemann",
        year = 1859,
        summary = "Riemann's functional equation ties the zeta function at negative integers to Bernoulli numbers, so zeta(-1) = -1/12 follows from exact fractions.",
        details = "Riemann's 1859 memoir on the number of primes introduced the functional equation that extends the zeta function over the whole complex plane. The example computes Bernoulli numbers exactly and prints the values of zeta at -1, -3, -5, -7 and -9 as fractions.",
        instructions = """
            1. Save the example as riemann.py.
            2. Run it: python3 riemann.py
        """,
        example = """
            from fractions import Fraction
            from math import comb

            def bernoulli(n):
                # Bernoulli numbers B_0 to B_n from the recurrence: the sum over k of C(m+1, k) B_k is zero, with B_1 = -1/2.
                B = [Fraction(1)]
                for m in range(1, n + 1):
                    total = sum(comb(m + 1, k) * B[k] for k in range(m))
                    B.append(-total / (m + 1))
                return B

            B = bernoulli(10)
            # Riemann's functional equation gives zeta(1 - n) = -B_n / n for even n.
            for n in (2, 4, 6, 8, 10):
                print(f"zeta({1 - n}) = {-B[n] / n}")
            print(f"B_2 = {B[2]}, B_4 = {B[4]}")
        """,
    ),

    entry(
        databaseId = 19,
        kind = Kind.GUIDE,
        section = "Primes",
        title = "How Chebyshev's prime counts prove Bertrand's postulate",
        originalTitle = "Memoire sur les nombres premiers (1852)",
        author = "Pafnuty Chebyshev",
        year = 1852,
        summary = "Chebyshev's counting bounds for primes imply that a prime lies between n and 2n for every n above 1, which settles Bertrand's postulate.",
        details = "Chebyshev published this proof in his 1852 memoir on prime numbers, after Bertrand had stated the postulate in 1845. The example sieves the primes up to 2000, prints the prime counts at 10, 100, 500 and 1000, and checks the postulate for every n up to 1000.",
        instructions = """
            1. Save the example as chebyshev.py.
            2. Run it: python3 chebyshev.py
        """,
        example = """
            LIMIT = 1000

            def sieve(limit):
                # flags[n] is True when n is prime.
                flags = [True] * (limit + 1)
                flags[0] = flags[1] = False
                for i in range(2, int(limit ** 0.5) + 1):
                    if flags[i]:
                        for j in range(i * i, limit + 1, i):
                            flags[j] = False
                return flags

            flags = sieve(2 * LIMIT)
            primes = [n for n in range(2, LIMIT + 1) if flags[n]]

            def pi(x):
                return sum(1 for p in primes if p <= x)

            for x in (10, 100, 500, 1000):
                print(f"pi({x}) = {pi(x)}")
            # Bertrand's postulate: for every n at least 2 some prime p satisfies n < p <= 2n.
            ok = all(any(flags[p] for p in range(n + 1, 2 * n + 1)) for n in range(2, LIMIT + 1))
            print("Bertrand's postulate holds for 2 <= n <= 1000:", ok)
        """,
    ),

    entry(
        databaseId = 19,
        kind = Kind.GUIDE,
        section = "Number theory",
        title = "How Kummer's criterion sorts primes below 100",
        originalTitle = "Journal fuer die reine und angewandte Mathematik, volume 40, pages 130 to 138 (1850)",
        author = "Ernst Eduard Kummer",
        year = 1850,
        summary = "Kummer's criterion calls an odd prime irregular when it divides the numerator of a Bernoulli number, and below 100 only 37, 59 and 67 qualify.",
        details = "Kummer's 1850 work proved Fermat's last theorem for the exponents he called regular primes. The example computes Bernoulli numbers exactly and sorts the odd primes below 100 by Kummer's test.",
        rare = true,
        instructions = """
            1. Save the example as kummer.py.
            2. Run it: python3 kummer.py
        """,
        example = """
            from fractions import Fraction
            from math import comb

            def bernoulli(n):
                B = [Fraction(1)]
                for m in range(1, n + 1):
                    total = sum(comb(m + 1, k) * B[k] for k in range(m))
                    B.append(-total / (m + 1))
                return B

            def is_prime(n):
                if n < 2:
                    return False
                i = 2
                while i * i <= n:
                    if n % i == 0:
                        return False
                    i += 1
                return True

            LIMIT = 100
            B = bernoulli(LIMIT)
            odd_primes = [p for p in range(3, LIMIT) if is_prime(p)]
            irregular = []
            for p in odd_primes:
                # Kummer's criterion: p is irregular when it divides the numerator of some B_k with k even and 2 <= k <= p - 3.
                if any(B[k].numerator % p == 0 for k in range(2, p - 2, 2)):
                    irregular.append(p)
            print("odd primes below 100:", len(odd_primes))
            print("irregular primes below 100:", " ".join(str(p) for p in irregular))
            print("regular odd primes below 100:", len(odd_primes) - len(irregular))
        """,
    ),

    entry(
        databaseId = 19,
        kind = Kind.GUIDE,
        section = "Finite differences",
        title = "How Boole's summation adds powers of integers",
        originalTitle = "A Treatise on the Calculus of Finite Differences (1860)",
        author = "George Boole",
        year = 1860,
        summary = "Boole's finite-difference calculus writes a sum of powers as a polynomial in the upper limit, and its coefficients are Bernoulli numbers.",
        details = "Boole's 1860 treatise gives summation formulas in which Bernoulli numbers appear as coefficients, a result known earlier to Bernoulli and Euler. The example evaluates the sums of the first, second and third powers of the integers 1 to 10 by the formula and checks them against direct addition.",
        rare = true,
        instructions = """
            1. Save the example as boole.py.
            2. Run it: python3 boole.py
        """,
        example = """
            from fractions import Fraction
            from math import comb

            def bernoulli(n):
                B = [Fraction(1)]
                for m in range(1, n + 1):
                    total = sum(comb(m + 1, k) * B[k] for k in range(m))
                    B.append(-total / (m + 1))
                return B

            B = bernoulli(4)

            def power_sum(p, n):
                # Sum of k^p for k = 1 to n: (1/(p+1)) times the sum over j of C(p+1, j) B_j n^(p+1-j), with B_1 = +1/2.
                Bp = list(B)
                Bp[1] = Fraction(1, 2)
                return sum(comb(p + 1, j) * Bp[j] * n ** (p + 1 - j) for j in range(p + 1)) / (p + 1)

            n = 10
            for p in (1, 2, 3):
                direct = sum(k ** p for k in range(1, n + 1))
                print(f"sum of k^{p} for k = 1 to {n}: {power_sum(p, n)} (direct {direct})")
            print("the formula agrees with the direct sums:", all(power_sum(p, n) == sum(k ** p for k in range(1, n + 1)) for p in (1, 2, 3)))
        """,
    ),

    entry(
        databaseId = 19,
        kind = Kind.GUIDE,
        section = "Signal codes",
        title = "How a four-letter code book is numbered",
        originalTitle = "Commercial Code of Signals for the Use of All Nations (1857)",
        author = "Board of Trade, London",
        year = 1857,
        summary = "A four-letter code book gives each message one group of four letters, and 26 to the power four gives 456976 possible groups.",
        details = "The Commercial Code of Signals let ships send common messages by flags or lamps using short letter groups. The example numbers messages in base 26, prints sample groups and checks that a thousand messages receive distinct groups.",
        instructions = """
            1. Save the example as code_book.py.
            2. Run it: python3 code_book.py
        """,
        example = """
            LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"

            def group(n):
                # Write message number n in base 26 and read its four digits as four letters.
                out = ""
                for _ in range(4):
                    out = LETTERS[n % 26] + out
                    n //= 26
                return out

            print("four-letter groups available:", 26 ** 4)
            for n in (0, 25, 26, 27, 676, 456975):
                print(f"message {n}: {group(n)}")
            groups = [group(n) for n in range(1000)]
            print("first 1000 messages get distinct groups:", len(set(groups)) == len(groups))
        """,
    ),
)
