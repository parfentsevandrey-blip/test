package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1910 to 1920: short programs that check the decade's formulas, tables and counting methods. */
val db13Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 13,
        kind = Kind.EXAMPLE,
        section = "Number theory",
        title = "Carmichael numbers below 1000",
        originalTitle = "Note on a new number theory function (Carmichael, 1910)",
        author = "Written as a teaching example",
        year = 1910,
        summary = "A Carmichael number is composite yet passes Fermat's test for every base coprime to it, and the only one below 1000 is 561.",
        details = "Carmichael's 1910 note identified 561 as a composite number that passes Fermat's test for every base coprime to it. The example checks every odd number below 1000 in two ways, by testing all bases and by Korselt's criterion, and both give 561 alone. For 561 the prime factors are 3, 11 and 17, and the numbers 2, 10 and 16 each divide 560.",
        instructions = """
            1. Save the example as carmichael.py.
            2. Run it: python3 carmichael.py
        """,
        example = """
            import math


            def is_prime(n):
                return n > 1 and all(n % d for d in range(2, int(n ** 0.5) + 1))


            def fermat_for_all_bases(n):
                # A composite n that passes Fermat's test for every base coprime to n is a Carmichael number.
                return not is_prime(n) and all(pow(a, n - 1, n) == 1 for a in range(2, n) if math.gcd(a, n) == 1)


            def korselt(n):
                # Korselt's criterion: n is squarefree and p - 1 divides n - 1 for every prime p dividing n.
                primes = [p for p in range(2, n + 1) if n % p == 0 and is_prime(p)]
                squarefree = all(n % (p * p) != 0 for p in primes)
                return len(primes) >= 2 and squarefree and all((n - 1) % (p - 1) == 0 for p in primes)


            odd = range(3, 1000, 2)
            print("Fermat test:", [n for n in odd if fermat_for_all_bases(n)])
            print("Korselt:", [n for n in odd if korselt(n)])
            print("3 * 11 * 17 = 561:", 3 * 11 * 17 == 561)
        """,
    ),

    entry(
        databaseId = 13,
        kind = Kind.EXAMPLE,
        section = "Statistics",
        title = "Fisher's F statistic for three small groups",
        originalTitle = "Variance split into parts, from Fisher's 1918 paper on Mendelian inheritance (Fisher, 1918)",
        author = "Written as a teaching example",
        year = 1918,
        summary = "Splitting the squared deviations of three small groups into a between-group part and a within-group part gives an F statistic of 7.75 on 2 and 6 degrees of freedom.",
        details = "Fisher's 1918 paper on the correlation between relatives is credited with introducing the term variance and the analysis of variance. The example computes that split for three groups of three values. The between-group sum of squares is 62 and the within-group sum is 24, and dividing each by its degrees of freedom gives F = 31 / 4 = 7.75.",
        instructions = """
            1. Save the example as anova.py.
            2. Run it: python3 anova.py
        """,
        example = """
            groups = {"A": [4, 6, 8], "B": [5, 7, 9], "C": [10, 12, 14]}
            values = [x for g in groups.values() for x in g]
            grand = sum(values) / len(values)
            means = {name: sum(g) / len(g) for name, g in groups.items()}
            ss_between = sum(len(g) * (means[name] - grand) ** 2 for name, g in groups.items())
            ss_within = sum((x - means[name]) ** 2 for name, g in groups.items() for x in g)
            df_between = len(groups) - 1
            df_within = len(values) - len(groups)
            f = (ss_between / df_between) / (ss_within / df_within)
            print("sum of squares between:", round(ss_between, 6))
            print("sum of squares within:", round(ss_within, 6))
            print("F with", df_between, "and", df_within, "degrees of freedom:", round(f, 4))
        """,
    ),

    entry(
        databaseId = 13,
        kind = Kind.EXAMPLE,
        section = "Tabulating",
        title = "A card tabulator that counts punched-card columns",
        originalTitle = "Punched-card tabulators of the 1910s, such as the Powers machine",
        author = "Written as a teaching example",
        year = 1914,
        summary = "A tabulator senses the holes in each column of a card and advances one counter per row, so the totals of a deck come out column by column.",
        details = "The Powers tabulator, whose first printing machine dates from 1914, works this way. The example takes a deck of four cards with three columns, each holding one digit as a single hole, and prints for each column the count of cards that show each digit.",
        instructions = """
            1. Save the example as card_tab.py.
            2. Run it: python3 card_tab.py
        """,
        example = """
            deck = [
                (1, 4, 7),
                (1, 4, 2),
                (3, 4, 7),
                (1, 5, 7),
            ]
            COLUMNS = 3
            counters = [[0] * 10 for _ in range(COLUMNS)]
            for card in deck:
                # Each column of a card has one hole, in the row given by its digit; that counter advances by one.
                for column, row in enumerate(card):
                    counters[column][row] += 1
            for column, counts in enumerate(counters, start=1):
                shown = ", ".join(f"{row}:{n}" for row, n in enumerate(counts) if n)
                print(f"column {column}: {shown}")
            print("cards read:", len(deck))
        """,
    ),

    entry(
        databaseId = 13,
        kind = Kind.EXAMPLE,
        section = "Telephony",
        title = "Blocked calls in a telephone trunk group",
        originalTitle = "Solution of some problems in the theory of probabilities of significance in automatic telephone exchanges (Erlang, 1917)",
        author = "Written as a teaching example",
        year = 1917,
        summary = "The recursive form of Erlang's loss formula gives the share of calls that find every line busy, for a given traffic and number of lines.",
        details = "Erlang's 1917 paper on automatic telephone exchanges gave the loss formula that telephone engineers used for decades. The recursion here gives the same values as the closed form. The example prints the share of blocked calls for two erlangs of traffic on one, two and three lines, and with two lines the exact value is 2/5.",
        instructions = """
            1. Save the example as erlang_b.py.
            2. Run it: python3 erlang_b.py
        """,
        example = """
            from fractions import Fraction


            def erlang_b(offered, lines):
                # Recursion on the number of lines: each line added lowers the share of calls that find every line busy.
                b = Fraction(1)
                for m in range(1, lines + 1):
                    b = offered * b / (m + offered * b)
                return b


            for lines in range(1, 4):
                print(f"lines {lines}: blocked {float(erlang_b(2, lines)):.4f}")
            print(f"exact for 2 erlangs on 2 lines: {erlang_b(2, 2)}")
        """,
    ),

    entry(
        databaseId = 13,
        kind = Kind.EXAMPLE,
        section = "Number theory",
        title = "Highly composite numbers below 100",
        originalTitle = "Highly composite numbers (Ramanujan, 1915)",
        author = "Written as a teaching example",
        year = 1915,
        summary = "A highly composite number has more divisors than any smaller number, and below 100 there are nine of them.",
        details = "Ramanujan introduced highly composite numbers in a 1915 paper in the Proceedings of the London Mathematical Society, which ran to 62 pages and was the longest paper he wrote. The example counts divisors and prints each number that beats every smaller one, with its divisor count. The list begins 1, 2, 4, 6, 12, 24, 36, 48 and 60.",
        instructions = """
            1. Save the example as hcn.py.
            2. Run it: python3 hcn.py
        """,
        example = """
            def divisor_count(n):
                return sum(1 for d in range(1, n + 1) if n % d == 0)


            records = []
            best = 0
            for n in range(1, 100):
                count = divisor_count(n)
                if count > best:
                    best = count
                    records.append((n, count))
            print(records)
        """,
    ),

    entry(
        databaseId = 13,
        kind = Kind.EXAMPLE,
        section = "Approximation",
        title = "Bernstein polynomials approximate a function",
        originalTitle = "Proof of the Weierstrass approximation theorem by probability (Bernstein, 1912)",
        author = "Written as a teaching example",
        year = 1912,
        summary = "Bernstein polynomials weight the values of a function by binomial probabilities, and for the function t squared they have the closed form x squared plus x(1 - x)/n.",
        details = "Bernstein's 1912 two-page note proved the Weierstrass approximation theorem with these polynomials, using probability, and it appeared in the communications of the Kharkov Mathematical Society. The example checks the closed form for the function t squared at x = 1/2, for n = 2, 4 and 10, and each line prints True.",
        rare = true,
        instructions = """
            1. Save the example as bernstein.py.
            2. Run it: python3 bernstein.py
        """,
        example = """
            from fractions import Fraction
            from math import comb


            def bernstein(f, n, x):
                # The n-th Bernstein polynomial of f at x, weighting the values f(k / n) by binomial probabilities.
                return sum(f(Fraction(k, n)) * comb(n, k) * x ** k * (1 - x) ** (n - k) for k in range(n + 1))


            def f(t):
                return t * t


            x = Fraction(1, 2)
            for n in (2, 4, 10):
                value = bernstein(f, n, x)
                closed = x * x + x * (1 - x) / n
                print(f"n={n}: {value}, closed form {closed}, equal: {value == closed}")
        """,
    ),
)
