package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1860 to 1870: a sorting demon, a prime count, a factor table and a test on pair-number systems. */
val db18Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 18,
        kind = Kind.PROJECT,
        section = "Thermodynamics",
        title = "Maxwell's sorting demon in a small simulation",
        originalTitle = "Letter to P. G. Tait, 11 December 1867",
        author = "James Clerk Maxwell",
        year = 1867,
        summary = "A small being opens a door between two chambers only for fast molecules from the left and slow ones from the right, which warms one side without work, as a thought experiment about the second law.",
        details = "Maxwell described the being in a letter to Peter Guthrie Tait dated 11 December 1867, and the idea was printed later in his Theory of Heat in 1871. The example is a deterministic version with ten molecules and fixed speeds, and it checks that the total speed is unchanged while the right chamber ends faster on average.",
        instructions = """
            1. Save the example as demon.py.
            2. Run it: python3 demon.py
        """,
        example = """
            from fractions import Fraction as F

            # Ten molecules as speeds in arbitrary units, five in each chamber.
            left = [3, 8, 1, 9, 4]
            right = [7, 2, 6, 5, 10]
            FAST = 6    # the demon passes a molecule from left to right when it is at least this fast
            SLOW = 4    # and from right to left when it is at most this slow

            def average(speeds):
                return F(sum(speeds), len(speeds))

            print(f"start: left {len(left)} molecules, average speed {average(left)}; right {len(right)} molecules, average speed {average(right)}")
            new_left = [v for v in left if v < FAST] + [v for v in right if v <= SLOW]
            new_right = [v for v in right if v > SLOW] + [v for v in left if v >= FAST]
            print(f"after the demon: left {len(new_left)} molecules, average speed {average(new_left)}; right {len(new_right)} molecules, average speed {average(new_right)}")
            print(f"total speed before {sum(left) + sum(right)}, after {sum(new_left) + sum(new_right)}")
            print("right chamber is faster on average: " + str(average(new_right) > average(new_left)))
        """,
    ),

    entry(
        databaseId = 18,
        kind = Kind.PROJECT,
        section = "Number theory",
        title = "Meissel's count of primes below a large number",
        originalTitle = "Meissel's formula for the prime counting function (1870)",
        author = "Ernst Meissel",
        year = 1870,
        summary = "Meissel rewrote Legendre's count so that the main sum needs only the primes up to the cube root of x, which made counts for large x practical by hand.",
        details = "The formula rearranges Legendre's phi-function count, and Meissel's improvement is dated to 1870. Lehmer later called Meissel's large calculation one of the most impressive of the nineteenth century, though slightly inaccurate. The example checks the formula against a sieve for the counts up to 1000, 10000 and 100000.",
        instructions = """
            1. Save the example as meissel.py.
            2. Run it: python3 meissel.py
        """,
        example = """
            import math

            LIMIT = 100000

            def prime_flags(n):
                flags = [True] * (n + 1)
                flags[0] = flags[1] = False
                for i in range(2, math.isqrt(n) + 1):
                    if flags[i]:
                        for j in range(i * i, n + 1, i):
                            flags[j] = False
                return flags

            FLAGS = prime_flags(LIMIT)
            PRIMES = [v for v in range(LIMIT + 1) if FLAGS[v]]
            PI = [0] * (LIMIT + 1)            # PI[v] is the number of primes up to v
            for v in range(1, LIMIT + 1):
                PI[v] = PI[v - 1] + (1 if FLAGS[v] else 0)

            def phi(x, a, memo):
                # Legendre's phi: whole numbers up to x with no prime factor among the first a primes.
                if a == 0:
                    return x
                key = (x, a)
                if key not in memo:
                    memo[key] = phi(x, a - 1, memo) - phi(x // PRIMES[a - 1], a - 1, memo)
                return memo[key]

            def cube_root(x):
                r = round(x ** (1 / 3))
                while r ** 3 > x:
                    r -= 1
                while (r + 1) ** 3 <= x:
                    r += 1
                return r

            def meissel_pi(x):
                b = PI[math.isqrt(x)]          # primes up to the square root of x
                c = PI[cube_root(x)]           # primes up to the cube root of x
                total = phi(x, c, {}) + c - 1 + (b + c - 1) * (b - c) // 2
                for i in range(c + 1, b + 1):  # the primes p_i with c < i <= b
                    total -= PI[x // PRIMES[i - 1]]
                return total

            for x in (1000, 10000, 100000):
                value = meissel_pi(x)
                print(f"pi({x}) by Meissel's formula: {value}, sieve count {PI[x]}, agree: {value == PI[x]}")
        """,
    ),

    entry(
        databaseId = 18,
        kind = Kind.PROJECT,
        section = "Factor tables",
        title = "Dase's factor table, the smallest factor of each number",
        originalTitle = "Factoren-Tafeln fuer alle Zahlen der siebenten Million (1862)",
        author = "Zacharias Dase and Hermann Rosenberg",
        year = 1862,
        summary = "A table gives the smallest factor of each number in a long range, found by trial division, so a reader sees at once which numbers are prime.",
        details = "Dase died in Hamburg on 11 September 1861, and the seventh-million volume, which covers 6000001 to 7002000, appeared in 1862 with Rosenberg completing the work. The example runs trial division on the first ten numbers of that range and prints the smallest factor of each.",
        rare = true,
        instructions = """
            1. Save the example as dase.py.
            2. Run it: python3 dase.py
        """,
        example = """
            import math

            def smallest_factor(n):
                # Trial division: the smallest divisor from 2 upward, or n itself when n is prime.
                for d in range(2, math.isqrt(n) + 1):
                    if n % d == 0:
                        return d
                return n

            primes = 0
            for n in range(6000001, 6000011):
                f = smallest_factor(n)
                if f == n:
                    primes += 1
                print(f"{n}: smallest factor {f}")
            print(f"primes in the window: {primes}")
        """,
    ),

    entry(
        databaseId = 18,
        kind = Kind.PROJECT,
        section = "Pair numbers",
        title = "Hankel's permanence test on three pair-number systems",
        originalTitle = "Theorie der complexen Zahlensysteme (1867)",
        author = "Hermann Hankel",
        year = 1867,
        summary = "Hankel asked which laws of ordinary arithmetic survive when a new kind of number is built from pairs, and the program tests three such systems for zero divisors.",
        details = "Hankel's 1867 book asked which formal laws of ordinary arithmetic survive when new kinds of number are built from pairs, and he called this the principle of permanence. The example builds three two-dimensional systems in which the new unit squares to minus one, zero or plus one, and counts the pairs of nonzero numbers whose product is zero. Only the systems whose unit squares to zero or to plus one have such pairs.",
        instructions = """
            1. Save the example as pair_systems.py.
            2. Run it: python3 pair_systems.py
        """,
        example = """
            # Pairs (a, b) stand for a + b e, where e squares to s. The product is
            # (a + b e)(c + d e) = (ac + s bd) + (ad + bc) e.
            def mul(x, y, s):
                a, b = x
                c, d = y
                return (a * c + s * b * d, a * d + b * c)

            # Nonzero pairs with coefficients -1, 0 or 1.
            elements = [(a, b) for a in (-1, 0, 1) for b in (-1, 0, 1) if (a, b) != (0, 0)]
            systems = [("complex numbers, e squared -1", -1), ("dual numbers, e squared 0", 0), ("split-complex numbers, e squared 1", 1)]
            for name, s in systems:
                pairs = [(x, y) for x in elements for y in elements if mul(x, y, s) == (0, 0)]
                print(f"{name}: {len(pairs)} zero-divisor pairs")
                if pairs:
                    x, y = pairs[0]
                    print(f"  first pair: {x} times {y} gives {mul(x, y, s)}")
        """,
    ),

)
