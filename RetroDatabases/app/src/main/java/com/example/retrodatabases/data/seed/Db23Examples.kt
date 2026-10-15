package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1810 to 1820: Euler counts for star polyhedra, probability by hand, a factor sieve, unit-fraction periods and a sort. */
val db23Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 23,
        kind = Kind.EXAMPLE,
        section = "Polyhedra",
        title = "Poinsot's star polyhedra and Euler's count",
        originalTitle = "Memoire sur les polygones et les polyedres (1810)",
        author = "Louis Poinsot",
        year = 1810,
        summary = "Poinsot's four star polyhedra are counted by vertices, edges and faces, and two of their Euler numbers differ from the value 2 of a sphere.",
        details = "Poinsot described the four regular star polyhedra in 1810, adding two to the two that Kepler had found. The example counts the vertices, edges and faces of each and prints the Euler number V minus E plus F.",
        instructions = """
            1. Save the example as poinsot_euler.py.
            2. Run it: python3 poinsot_euler.py
        """,
        example = """
            polyhedra = [
                ("small stellated dodecahedron", 12, 30, 12),
                ("great dodecahedron", 12, 30, 12),
                ("great stellated dodecahedron", 20, 30, 12),
                ("great icosahedron", 12, 30, 20),
            ]
            for name, v, e, f in polyhedra:
                print(f"{name}: V {v} - E {e} + F {f} = {v - e + f}")
        """,
    ),

    entry(
        databaseId = 23,
        kind = Kind.EXAMPLE,
        section = "Probability",
        title = "Gambler's ruin with fair coins",
        originalTitle = "Theorie analytique des probabilites (1812)",
        author = "Pierre-Simon Laplace",
        year = 1812,
        summary = "A player with a fortune of a units who bets one unit on each toss of a fair coin reaches a target of N before ruin with chance a over N.",
        details = "Huygens had treated a version of the ruin problem in 1657, and Laplace's 1812 treatise gives the same chance and the expected length of play. The example prints the chance and the expected number of tosses for a target of ten units, in exact fractions.",
        instructions = """
            1. Save the example as gambler_ruin.py.
            2. Run it: python3 gambler_ruin.py
        """,
        example = """
            from fractions import Fraction

            N = 10
            for a in range(1, N):
                chance = Fraction(a, N)
                duration = a * (N - a)
                print(f"fortune {a}: chance to reach {N} first {chance}, expected tosses {duration}")
        """,
    ),

    entry(
        databaseId = 23,
        kind = Kind.EXAMPLE,
        section = "Probability",
        title = "Dice sums from a generating function",
        originalTitle = "Theorie analytique des probabilites (1812)",
        author = "Pierre-Simon Laplace",
        year = 1812,
        summary = "Two dice are multiplied as polynomials in x, and the coefficient of x to the power s counts the ways of throwing a total of s.",
        details = "Laplace used generating functions of this kind in his 1812 treatise, and the method turns a counting problem into a product of polynomials. The example builds the generating function of one die, squares it and prints the number of ways to throw each total.",
        instructions = """
            1. Save the example as dice_sums.py.
            2. Run it: python3 dice_sums.py
        """,
        example = """
            faces = [0, 1, 1, 1, 1, 1, 1]   # index s holds 1 when a face shows s, for s = 1 to 6

            def multiply(p, q):
                out = [0] * (len(p) + len(q) - 1)
                for i, a in enumerate(p):
                    for j, b in enumerate(q):
                        out[i + j] += a * b
                return out

            two = multiply(faces, faces)
            for total in range(2, 13):
                print(f"total {total}: {two[total]} ways")
            print("ways out of 36:", sum(two))
        """,
    ),

    entry(
        databaseId = 23,
        kind = Kind.EXAMPLE,
        section = "Factor tables",
        title = "A smallest-factor sieve for numbers to forty",
        originalTitle = "Table des diviseurs pour tous les nombres non premiers (1814)",
        author = "Johann Karl Burckhardt",
        year = 1814,
        summary = "A sieve marks each composite number with its smallest prime factor, the idea behind printed factor tables.",
        details = "Burckhardt's factor tables gave the smallest factor of each composite number, and this example builds the same kind of table up to forty by sieving. It prints the composites with their smallest factors and then the primes.",
        instructions = """
            1. Save the example as factor_sieve.py.
            2. Run it: python3 factor_sieve.py
        """,
        example = """
            LIMIT = 40
            smallest = [0] * (LIMIT + 1)
            for d in range(2, LIMIT + 1):
                if smallest[d] == 0:
                    for m in range(d, LIMIT + 1, d):
                        if smallest[m] == 0:
                            smallest[m] = d
            rows = [f"{n}:{smallest[n]}" for n in range(2, LIMIT + 1) if smallest[n] != n]
            print("composites up to 40 and their smallest factor:")
            print(" ".join(rows))
            print("primes up to 40: " + " ".join(str(n) for n in range(2, LIMIT + 1) if smallest[n] == n))
        """,
    ),

    entry(
        databaseId = 23,
        kind = Kind.EXAMPLE,
        section = "Reciprocals",
        title = "Periods of the decimal expansions of unit fractions",
        originalTitle = "Tables of Squares, Cubes, Square Roots, Cube Roots, and Reciprocals (1814)",
        author = "Peter Barlow",
        year = 1814,
        summary = "The repeating part of 1/n for n coprime to ten has a length found by long division, and the program measures it for a few primes.",
        details = "Barlow's 1814 tables listed reciprocals, and the length of their repeating decimals was a question that arose in practical calculation. The example runs long division on remainders and prints the period for each prime from three to nineteen, except five.",
        instructions = """
            1. Save the example as unit_periods.py.
            2. Run it: python3 unit_periods.py
        """,
        example = """
            def period(n):
                # Length of the repeating part of 1/n, found by long division on remainders (n coprime to 10).
                r = 1
                k = 0
                while True:
                    r = (r * 10) % n
                    k += 1
                    if r == 1:
                        return k

            for n in (3, 7, 11, 13, 17, 19):
                print(f"1/{n} has period {period(n)}")
        """,
    ),

    entry(
        databaseId = 23,
        kind = Kind.EXAMPLE,
        section = "Bibliography",
        title = "Sorting a catalogue of titles by a key",
        originalTitle = "Manuel du libraire et de l'amateur de livres (1810)",
        author = "Jacques-Charles Brunet",
        year = 1810,
        summary = "Brunet's handbook was arranged alphabetically by author, and this example sorts a list of titles by a lowercase key.",
        details = "A bibliographic handbook such as Brunet's depends on an alphabetical order that readers can follow. The example sorts five titles from this database without regard to capital letters, so that the order is the same for every reader.",
        instructions = """
            1. Save the example as sort_titles.py.
            2. Run it: python3 sort_titles.py
        """,
        example = """
            titles = [
                "Gergonne's Annales of mathematics",
                "Bentham's Chrestomathia school plan",
                "Barlow's tables of squares, cubes and reciprocals",
                "Brunet's book-trade manual",
                "Bessel's Fundamenta Astronomiae",
            ]
            for position, title in enumerate(sorted(titles, key=str.lower), start=1):
                print(f"{position}. {title}")
        """,
    ),
)
