package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1780 to 1790: the probability of majorities, quadratic residues, earthworks, mechanics and crystal faces. */
val db26Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 26,
        kind = Kind.GUIDE,
        section = "Probability",
        title = "How a majority vote gains reliability",
        originalTitle = "Essai sur l'application de l'analyse a la probabilite des decisions rendues a la pluralite des voix",
        author = "Marie Jean Antoine Nicolas de Caritat, marquis de Condorcet",
        year = 1785,
        summary = "If each voter is right with the same chance above one half, a majority of more voters is more often right.",
        details = "Condorcet's essay of 1785 argued that a majority of independent voters, each right with a chance above one half, is right more often as the number of voters grows. The example computes the exact chance that a majority of 1, 3, 5 and 7 voters is right when each is right with chance 2/3, and checks that at chance 1/2 a majority of five is right half the time. Critics have noted that the assumption of independent voters is a strong one.",
        instructions = """
            1. Save the example as jury.py.
            2. Run it: python3 jury.py
        """,
        example = """
            from fractions import Fraction
            from math import comb

            def majority_right(n, p):
                # Chance that more than half of n independent voters, each right with chance p, are right.
                q = 1 - p
                return sum(comb(n, k) * p**k * q**(n - k) for k in range(n // 2 + 1, n + 1))

            p = Fraction(2, 3)
            for n in (1, 3, 5, 7):
                print(f"{n} voters: {majority_right(n, p)}")
            print("at p = 1/2 with 5 voters:", majority_right(5, Fraction(1, 2)))
        """,
    ),

    entry(
        databaseId = 26,
        kind = Kind.GUIDE,
        section = "Number theory",
        title = "Legendre's 1785 memoir on squares modulo a prime",
        originalTitle = "Recherches d'analyse indeterminee",
        author = "Adrien-Marie Legendre",
        year = 1785,
        summary = "A number is a square modulo an odd prime p exactly when raising it to the power (p-1)/2 gives 1, and the program checks this for every residue.",
        details = "Legendre stated the law of quadratic reciprocity in his memoir of 1785, and Gauss gave the first complete proof in 1796; accounts differ on how much of the proof Legendre had. The example lists the nonzero squares modulo 7, 11 and 13 by squaring, checks with Euler's power test that the same residues come out, and then checks reciprocity for the pair 7 and 11.",
        instructions = """
            1. Save the example as legendre.py.
            2. Run it: python3 legendre.py
        """,
        example = """
            def squares(p):
                # The nonzero squares modulo p, found by squaring every nonzero residue.
                return sorted({(x * x) % p for x in range(1, p)})

            def legendre(a, p):
                # Euler's criterion: a to the power (p-1)/2, mod p, is 1 for a square and p-1 otherwise.
                return 1 if pow(a, (p - 1) // 2, p) == 1 else -1

            for p in (7, 11, 13):
                s = squares(p)
                print(f"squares mod {p}: " + " ".join(str(x) for x in s))
                ok = all(legendre(a, p) == (1 if a in s else -1) for a in range(1, p))
                print(f"criterion agrees mod {p}: {ok}")

            def reciprocity_holds(p, q):
                # Quadratic reciprocity for distinct odd primes: (p/q)(q/p) = (-1) to the power ((p-1)/2)((q-1)/2).
                lhs = legendre(p % q, q) * legendre(q % p, p)
                rhs = (-1) ** (((p - 1) // 2) * ((q - 1) // 2))
                return lhs == rhs

            print("reciprocity for 7 and 11:", reciprocity_holds(7, 11))
        """,
    ),

    entry(
        databaseId = 26,
        kind = Kind.GUIDE,
        section = "Earthworks",
        title = "How Monge's earthwork cost is minimised by matching in order",
        originalTitle = "Memoire sur la theorie des deblais et des remblais",
        author = "Gaspard Monge",
        year = 1781,
        summary = "Earth carried along paths that cross costs more, so the cheapest plan matches piles and holes in order along the line.",
        details = "Monge's memoir argued that earth should be carried along paths that do not cross, and is dated here to the Academy's volume for 1781; drafts were read in 1776 and 1781, and one source dates the printed text to 1784. The example takes three piles and three holes of unit volume on a line, tries all six ways of matching them, and finds that the in-order matching is the cheapest.",
        instructions = """
            1. Save the example as monge.py.
            2. Run it: python3 monge.py
        """,
        example = """
            from itertools import permutations

            piles = [0, 2, 5]   # positions of three piles, one unit of earth each
            holes = [1, 3, 4]   # positions of three holes, one unit each

            def cost(order):
                # The pile at piles[i] goes to the hole at holes[order[i]]; the cost is distance times volume.
                return sum(abs(piles[i] - holes[order[i]]) for i in range(len(piles)))

            best = min(permutations(range(3)), key=cost)
            print("best assignment:", best, "cost", cost(best))
            print("in-order matching cost:", cost((0, 1, 2)))
            print("all costs:", sorted(cost(p) for p in permutations(range(3))))
            print("in order is optimal:", cost((0, 1, 2)) == cost(best))
        """,
    ),

    entry(
        databaseId = 26,
        kind = Kind.GUIDE,
        section = "Mechanics",
        title = "How Atwood's machine slows a falling weight",
        originalTitle = "A Treatise on the Rectilinear Motion and Rotation of Bodies",
        author = "George Atwood",
        year = 1784,
        summary = "Two weights over a pulley accelerate at the fraction (m1 - m2)/(m1 + m2) of gravity, slow enough to time, and the program prints that fraction for three pairs of masses.",
        details = "Atwood's treatise of 1784 described the machine as a way to slow falling bodies so that the laws of motion could be measured. For masses m1 and m2 on a light string over a frictionless pulley, the acceleration is (m1 - m2)/(m1 + m2) times g and the tension is 2 m1 m2/(m1 + m2) times g. The example prints both for three pairs of masses and then finds the time squared to fall a quarter of a unit with the acceleration for 3 and 1.",
        instructions = """
            1. Save the example as atwood.py.
            2. Run it: python3 atwood.py
        """,
        example = """
            from fractions import Fraction as F

            def accel_over_g(m1, m2):
                # Two masses on a light frictionless pulley: a = (m1 - m2) g / (m1 + m2).
                return F(m1 - m2, m1 + m2)

            def tension_over_g(m1, m2):
                # Tension in the string: T = 2 m1 m2 g / (m1 + m2).
                return F(2 * m1 * m2, m1 + m2)

            for m1, m2 in [(3, 1), (5, 3), (2, 1)]:
                print(f"masses {m1} and {m2}: a = {accel_over_g(m1, m2)} g, T = {tension_over_g(m1, m2)} g")

            # Time squared to fall height h from rest is 2h/a; here g = 1, h = 1/4 and a = 1/2.
            h = F(1, 4)
            a = accel_over_g(3, 1)
            print("time squared to fall 1/4 with a = g/2 and g = 1:", 2 * h / a)
        """,
    ),

    entry(
        databaseId = 26,
        kind = Kind.GUIDE,
        section = "Crystallography",
        title = "How rational indices fix the faces of a crystal",
        originalTitle = "Essai d'une theorie sur la structure des crystaux",
        author = "Rene-Just Hauy",
        year = 1784,
        summary = "Each face of a crystal has whole-number indices, and the program counts the planes allowed by the smallest indices, grouped by the symmetry of the cube.",
        details = "Hauy's essay of 1784 proposed that the faces of a crystal correspond to simple whole-number indices, the law of rational indices. The example lists the planes whose indices are -1, 0 or 1, groups them into families that the symmetry of the cube turns into one another, and counts 26 planes in 3 families of sizes 6, 8 and 12.",
        instructions = """
            1. Save the example as hauy.py.
            2. Run it: python3 hauy.py
        """,
        example = """
            from itertools import permutations, product

            def family(index):
                # Permutations and sign changes of the three indices are the symmetries of the cube.
                return frozenset(
                    tuple(sign * v for sign, v in zip(signs, perm))
                    for perm in permutations(index)
                    for signs in product((1, -1), repeat=3)
                )

            indices = [h for h in product((-1, 0, 1), repeat=3) if h != (0, 0, 0)]
            families = []
            seen = set()
            for h in indices:
                if h in seen:
                    continue
                fam = family(h)
                seen |= fam
                families.append(fam)

            print("planes with indices from -1 to 1:", len(indices))
            print("families:", len(families))
            print("family sizes:", sorted(len(f) for f in families))
            print("total:", sum(len(f) for f in families))
        """,
    ),
)
