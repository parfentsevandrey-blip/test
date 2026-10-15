package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1930 to 1940: the techniques of the decade, each with a small program that runs today. */
val db11Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 11,
        kind = Kind.GUIDE,
        section = "Functions",
        title = "How Church numerals count with functions",
        originalTitle = "The lambda calculus of Alonzo Church (1932, with the 1936 results)",
        author = "Alonzo Church",
        year = 1936,
        summary = "A number is a function that applies another function n times, so adding and multiplying numbers becomes composing functions.",
        details = "Church's calculus of functions needs no numbers at all. Its 1936 paper used it for the undecidability result. The example defines zero, successor, addition and multiplication as nested functions in Python, turns the results back into ordinary integers by counting applications, and prints small sums and products.",
        instructions = """
            1. Save the example as church_numerals.py.
            2. Run it: python3 church_numerals.py
        """,
        example = """
            def zero(f):
                return lambda x: x

            def succ(n):
                return lambda f: lambda x: f(n(f)(x))

            def add(m):
                return lambda n: lambda f: lambda x: m(f)(n(f)(x))

            def mul(m):
                return lambda n: lambda f: m(n(f))

            def to_int(n):
                # Count how many times the number applies its function to zero.
                return n(lambda k: k + 1)(0)

            one = succ(zero)
            two = succ(one)
            three = succ(two)
            print("succ(two) =", to_int(succ(two)))
            print("add(two)(three) =", to_int(add(two)(three)))
            print("mul(two)(three) =", to_int(mul(two)(three)))
            print("mul(three)(three) =", to_int(mul(three)(three)))
            print("add(zero)(one) =", to_int(add(zero)(one)))
        """,
    ),

    entry(
        databaseId = 11,
        kind = Kind.GUIDE,
        section = "Logic",
        title = "How Goedel numbering turns a formula into a number",
        originalTitle = "Ueber formal unentscheidbare Saetze der Principia Mathematica und verwandter Systeme I (1931)",
        author = "Kurt Goedel",
        year = 1931,
        summary = "Each symbol of a formula gets a code, and the whole formula gets one number built from primes, so a statement about formulas becomes a statement about numbers.",
        details = "The k-th symbol of the formula contributes the k-th prime, raised to the code of that symbol. Unique factorisation lets the number be decoded again, which is what makes the coding useful in proofs. The example encodes two short formulas, prints their numbers and decodes them back.",
        instructions = """
            1. Save the example as goedel_numbering.py.
            2. Run it: python3 goedel_numbering.py
        """,
        example = """
            SYMBOLS = {"0": 1, "S": 2, "=": 3, "+": 4, "(": 5, ")": 6}
            SYMBOL_OF = {code: symbol for symbol, code in SYMBOLS.items()}
            PRIMES = [2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37]

            def encode(formula):
                # The k-th symbol contributes the k-th prime, raised to the code of the symbol.
                number = 1
                for k, symbol in enumerate(formula):
                    number *= PRIMES[k] ** SYMBOLS[symbol]
                return number

            def decode(number):
                # Unique factorisation: the exponent of each prime gives back the symbol in that place.
                symbols = []
                k = 0
                while number > 1:
                    p = PRIMES[k]
                    exponent = 0
                    while number % p == 0:
                        number //= p
                        exponent += 1
                    symbols.append(SYMBOL_OF[exponent])
                    k += 1
                return "".join(symbols)

            for formula in ["0=0", "S0=S0"]:
                n = encode(formula)
                print(formula, "->", n, "->", decode(n), decode(n) == formula)
        """,
    ),

    entry(
        databaseId = 11,
        kind = Kind.GUIDE,
        section = "Logic design",
        title = "How a half adder is built from NAND gates",
        originalTitle = "A symbolic analysis of relay and switching circuits (Shannon, 1937)",
        author = "Claude Shannon",
        year = 1937,
        summary = "Shannon showed that switching circuits can be designed with Boolean algebra, and a half adder can be built from NAND gates alone.",
        details = "Shannon's 1937 thesis designed relay networks from series and parallel contacts. This example uses NAND instead, a single gate that Sheffer showed in 1913 can build every Boolean function. It builds NOT, AND and exclusive-or from NAND gates, combines them into a half adder and prints the truth table.",
        instructions = """
            1. Save the example as half_adder.py.
            2. Run it: python3 half_adder.py
        """,
        example = """
            def nand(a, b):
                return 0 if (a and b) else 1

            def not_(a):
                return nand(a, a)

            def and_(a, b):
                return not_(nand(a, b))

            def xor(a, b):
                # Four NAND gates make an exclusive-or.
                n = nand(a, b)
                return nand(nand(a, n), nand(b, n))

            def half_adder(a, b):
                # The sum is the exclusive-or of the inputs and the carry is their AND.
                return xor(a, b), and_(a, b)

            print("a b | sum carry")
            for a in (0, 1):
                for b in (0, 1):
                    s, c = half_adder(a, b)
                    print(a, b, "|", s, c)
        """,
    ),

    entry(
        databaseId = 11,
        kind = Kind.GUIDE,
        section = "Games",
        title = "How the Grundy value of a game position is found",
        originalTitle = "Mathematische Kampfspiele (Sprague, 1935) and Mathematics and games (Grundy, 1939)",
        author = "Roland Sprague and Patrick Grundy",
        year = 1935,
        summary = "A position gets the smallest number that its moves do not reach, and two games are added by exclusive-or of their values.",
        details = "Sprague found the rule in 1935 and Grundy published the same idea in 1939. The example computes the values for a subtraction game in which a move takes away 1, 3 or 4 counters, then shows that two heaps combine by exclusive-or.",
        instructions = """
            1. Save the example as sprague_grundy.py.
            2. Run it: python3 sprague_grundy.py
        """,
        example = """
            MOVES = (1, 3, 4)

            def mex(values):
                # The smallest non-negative number that is not in values.
                n = 0
                while n in values:
                    n += 1
                return n

            grundy = []
            for n in range(11):
                options = {grundy[n - m] for m in MOVES if m <= n}
                grundy.append(mex(options))

            print(" ".join(f"{n}:{g}" for n, g in enumerate(grundy)))
            print("first player wins from 10:", grundy[10] != 0)
            print("two heaps of 4 and 5 have value", grundy[4] ^ grundy[5])
        """,
    ),

    entry(
        databaseId = 11,
        kind = Kind.GUIDE,
        section = "Optimisation",
        title = "How a linear program is solved by checking corners",
        originalTitle = "Mathematical methods of organizing and planning production (Kantorovich, 1939)",
        author = "Leonid Kantorovich",
        year = 1939,
        summary = "The best value of a linear objective is reached at a corner of the allowed region, so a small program can test every corner and keep the best.",
        details = "Kantorovich described linear programming in 1939 for planning production. The example uses exact fractions, finds each point where two limits meet, keeps the points that satisfy all limits and reports the best one. It is brute force, so it suits only small problems.",
        instructions = """
            1. Save the example as linear_program.py.
            2. Run it: python3 linear_program.py
        """,
        example = """
            from fractions import Fraction as F
            from itertools import combinations

            # Each limit is written a*x + b*y <= c. The axes are the limits x >= 0 and y >= 0.
            CONS = [(1, 1, 4), (1, 3, 6), (-1, 0, 0), (0, -1, 0)]
            OBJ = (3, 2)

            def intersect(p, q):
                (a1, b1, c1), (a2, b2, c2) = p, q
                det = a1 * b2 - a2 * b1
                if det == 0:
                    return None
                return (F(c1 * b2 - c2 * b1, det), F(a1 * c2 - a2 * c1, det))

            def feasible(pt):
                return all(a * pt[0] + b * pt[1] <= c for a, b, c in CONS)

            corners = set()
            for p, q in combinations(CONS, 2):
                pt = intersect(p, q)
                if pt is not None and feasible(pt):
                    corners.add(pt)

            values = sorted((OBJ[0] * x + OBJ[1] * y, (x, y)) for x, y in corners)
            for value, (x, y) in values:
                print(f"corner ({x}, {y}) value {value}")
            best = max(values)
            print(f"best: ({best[1][0]}, {best[1][1]}) value {best[0]}")
        """,
    ),
)
