package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1820 to 1830: the electrical, analytic and algebraic methods of the decade, each with a program that runs today. */
val db22Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 22,
        kind = Kind.GUIDE,
        section = "Electric circuits",
        title = "How Ohm's law splits a current between two branches",
        originalTitle = "Die galvanische Kette, mathematisch bearbeitet (1827)",
        author = "Georg Simon Ohm",
        year = 1827,
        summary = "Ohm's law says current equals voltage over resistance, and two branches share a current in inverse proportion to their resistances.",
        details = "Ohm's 1827 book treated the galvanic circuit mathematically, with a law that makes the current proportional to the voltage and inversely proportional to the total resistance. The example uses exact fractions to split a current between two resistors and checks that the voltage across each branch agrees.",
        instructions = """
            1. Save the example as ohm.py.
            2. Run it: python3 ohm.py
        """,
        example = """
            from fractions import Fraction

            # Ohm's law for a whole circuit: current = EMF / (external resistance + internal resistance).
            emf = Fraction(6)
            internal = Fraction(1)
            external = Fraction(2)
            current = emf / (external + internal)
            print("current I =", current)

            # Two branches in parallel share the current in inverse proportion to their resistances.
            r1 = Fraction(2)
            r2 = Fraction(3)
            i1 = current * r2 / (r1 + r2)
            i2 = current * r1 / (r1 + r2)
            print("branch currents:", i1, "and", i2)
            print("sum of branch currents:", i1 + i2)
            print("voltage across each branch:", i1 * r1, "and", i2 * r2)
            print("doubling the EMF doubles the current:", 2 * emf / (external + internal) == 2 * current)
        """,
    ),

    entry(
        databaseId = 22,
        kind = Kind.GUIDE,
        section = "Real roots",
        title = "How Sturm's sequence counts the real roots of a polynomial",
        originalTitle = "Memoire sur la resolution des equations numeriques (memoir of 1835)",
        author = "Jacques Charles Francois Sturm",
        year = 1829,
        summary = "Sturm's sequence of polynomials, built by repeated division, gives the number of real roots in an interval by counting sign changes at its ends.",
        details = "Sturm presented the theorem to the Paris Academy in May 1829, and accounts give 13 May or 25 May for the day, so the year is safe while the day is in dispute. The full memoir followed in 1835, and the example uses exact fractions to count the roots of a cubic in three intervals.",
        instructions = """
            1. Save the example as sturm.py.
            2. Run it: python3 sturm.py
        """,
        example = """
            from fractions import Fraction as F

            def remainder(a, b):
                # Remainder of polynomial a divided by polynomial b; coefficient lists run from the constant term upward.
                a = [F(c) for c in a]
                b = [F(c) for c in b]
                while len(b) > 1 and b[-1] == 0:
                    b.pop()
                while len(a) >= len(b) and any(c != 0 for c in a):
                    factor = a[-1] / b[-1]
                    shift = len(a) - len(b)
                    for i, c in enumerate(b):
                        a[i + shift] -= factor * c
                    a.pop()
                while len(a) > 1 and a[-1] == 0:
                    a.pop()
                return a if a else [F(0)]

            def derivative(p):
                return [i * c for i, c in enumerate(p)][1:]

            def sturm_chain(p):
                # Each new member is the negative of the remainder of the two before it.
                chain = [[F(c) for c in p], [F(c) for c in derivative(p)]]
                while True:
                    r = remainder(chain[-2], chain[-1])
                    if all(c == 0 for c in r):
                        break
                    chain.append([-c for c in r])
                return chain

            def value(p, x):
                return sum(c * x ** i for i, c in enumerate(p))

            def sign(v):
                return (v > 0) - (v < 0)

            def sign_changes(signs):
                signs = [s for s in signs if s != 0]
                return sum(1 for a, b in zip(signs, signs[1:]) if a != b)

            def changes_at(chain, x):
                return sign_changes([sign(value(p, x)) for p in chain])

            def changes_at_infinity(chain, positive):
                signs = []
                for p in chain:
                    s = sign(p[-1])
                    if not positive and (len(p) - 1) % 2 == 1:
                        s = -s
                    signs.append(s)
                return sign_changes(signs)

            # The cubic (x - 1)(x - 2)(x - 3), written from the constant term upward.
            chain = sturm_chain([-6, 11, -6, 1])
            print("chain length:", len(chain))
            print("real roots in total:", changes_at_infinity(chain, False) - changes_at_infinity(chain, True))
            bounds = [F(0), F(3, 2), F(5, 2), F(10)]
            for a, b in zip(bounds, bounds[1:]):
                count = changes_at(chain, a) - changes_at(chain, b)
                print(f"roots in ({a}, {b}]: {count}")
        """,
    ),

    entry(
        databaseId = 22,
        kind = Kind.GUIDE,
        section = "Heat",
        title = "How Fourier's sine series splits heat into modes",
        originalTitle = "Theorie analytique de la chaleur (1822)",
        author = "Jean-Baptiste Joseph Fourier",
        year = 1822,
        summary = "Fourier wrote a temperature on a rod as a sum of sine waves, each decaying at its own rate, so the share of each wave can be read off as an exact fraction.",
        details = "The Theorie analytique de la chaleur of 1822 solved the heat equation with sine series, and each term decays with the square of its wave number. The example takes three starting amplitudes, shows each mode's decay rate, and gives each mode's share of the total energy as an exact fraction, using the orthogonality of the sine waves.",
        instructions = """
            1. Save the example as fourier.py.
            2. Run it: python3 fourier.py
        """,
        example = """
            from fractions import Fraction as F

            # Starting temperature on a rod from 0 to pi, written as a sine series with amplitudes b_n for sin(n x).
            amplitudes = {1: F(1), 3: F(1, 3), 5: F(1, 5)}

            # The heat equation makes mode n decay as exp(-n^2 t), so the decay rate of mode n is n squared.
            for n, b in amplitudes.items():
                print(f"mode {n}: amplitude {b}, decay rate {n * n}")

            # Sine waves on [0, pi] are orthogonal with equal squared norms, so the energy in mode n is proportional to b_n squared.
            total = sum(b * b for b in amplitudes.values())
            for n, b in amplitudes.items():
                print(f"mode {n}: energy share {b * b / total}")
            print("shares sum to 1:", sum(b * b / total for b in amplitudes.values()) == 1)
        """,
    ),

    entry(
        databaseId = 22,
        kind = Kind.GUIDE,
        section = "Group theory",
        title = "How Abel's commutator test shows A5 is not solvable",
        originalTitle = "Memoire sur les equations algebriques ou on demontre l'impossibilite de la resolution de l'equation generale du cinquieme degre (1824)",
        author = "Niels Henrik Abel",
        year = 1824,
        summary = "Abel's proof that the general quintic cannot be solved by radicals rests on a group of permutations that is its own commutator subgroup, and the example checks this by brute force.",
        details = "Abel printed his proof in 1824 in a short pamphlet, and it showed that the general fifth-degree equation has no solution by radicals. The example builds the 60 even permutations of five objects, collects their commutators and checks that the commutators generate the whole group, so the group is not solvable.",
        instructions = """
            1. Save the example as abel.py.
            2. Run it: python3 abel.py
        """,
        example = """
            from itertools import permutations

            def compose(p, q):
                # The permutation that applies q first and then p.
                return tuple(p[q[i]] for i in range(len(q)))

            def inverse(p):
                inv = [0] * len(p)
                for i, x in enumerate(p):
                    inv[x] = i
                return tuple(inv)

            def is_even(p):
                # A permutation is even when its number of inversions is even.
                inversions = sum(1 for i in range(len(p)) for j in range(i + 1, len(p)) if p[i] > p[j])
                return inversions % 2 == 0

            def generate(gens, n):
                # Close the identity under multiplication by the generators, which gives the whole generated group.
                identity = tuple(range(n))
                group = {identity}
                frontier = [identity]
                while frontier:
                    new = []
                    for g in frontier:
                        for s in gens:
                            h = compose(s, g)
                            if h not in group:
                                group.add(h)
                                new.append(h)
                    frontier = new
                return group

            n = 5
            A5 = [p for p in permutations(range(n)) if is_even(p)]
            print("elements of A5:", len(A5))
            commutators = set()
            for a in A5:
                for b in A5:
                    commutators.add(compose(compose(a, b), compose(inverse(a), inverse(b))))
            derived = generate(list(commutators), n)
            print("commutator subgroup has", len(derived), "elements")
            print("A5 is its own commutator subgroup:", derived == set(A5))
        """,
    ),

    entry(
        databaseId = 22,
        kind = Kind.GUIDE,
        section = "Electrodynamics",
        title = "How Ampere's force between parallel currents scales",
        originalTitle = "Theorie mathematique des phenomenes electrodynamiques uniquement deduite de l'experience (1826)",
        author = "Andre-Marie Ampere",
        year = 1826,
        summary = "Ampere's electrodynamic law says the force between two parallel currents is proportional to the product of the currents and inversely proportional to their distance, attractive when the currents run the same way.",
        details = "Ampere reported the attraction and repulsion of currents in 1820 and stated the mathematical law in his 1826 treatise. The example tabulates the force per unit length between long straight wires for a few currents and distances, as exact fractions, with the constant set to two and the sign showing attraction or repulsion.",
        instructions = """
            1. Save the example as ampere.py.
            2. Run it: python3 ampere.py
        """,
        example = """
            from fractions import Fraction as F

            def force(i1, i2, d):
                # Force per unit length between two long parallel wires, with the constant set to two.
                return 2 * i1 * i2 / d

            # Each case: a label, the two currents, the distance and the sense (+1 same way, -1 opposite ways).
            cases = [
                ("I1=1 I2=1 d=1 same way", 1, 1, 1, 1),
                ("I1=2 I2=1 d=1", 2, 1, 1, 1),
                ("I1=1 I2=1 d=2", 1, 1, 2, 1),
                ("I1=3 I2=2 d=4", 3, 2, 4, 1),
                ("I1=1 I2=1 d=1 opposite ways", 1, 1, 1, -1),
            ]
            for label, i1, i2, d, sense in cases:
                print(f"{label}: {sense * force(F(i1), F(i2), F(d))}")
        """,
    ),
)
