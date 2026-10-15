package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1860 to 1870: short programs for the ciphers, heredity, heat, statistics, matrices and gears of the decade. */
val db18Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 18,
        kind = Kind.EXAMPLE,
        section = "Cryptanalysis",
        title = "How Kasiski's test finds the key length of a Vigenere cipher",
        originalTitle = "Die Geheimschriften und die Dechiffrir-Kunst (1863)",
        author = "Friedrich Kasiski",
        year = 1863,
        summary = "Repeated groups of letters in a polyalphabetic ciphertext tend to lie at distances that are multiples of the key length, so the common factors of those distances suggest the key length.",
        details = "Kasiski published the method in 1863, although Babbage had broken similar ciphers earlier without publishing his work, so the priority is disputed. The example uses a 24-letter ciphertext made with a three-letter key, finds every repeated trigram, prints the distances between its occurrences, and counts the factors of those distances.",
        instructions = """
            1. Save the example as kasiski.py.
            2. Run it: python3 kasiski.py
        """,
        example = """
            from collections import Counter

            # A 24-letter Vigenere ciphertext made with a three-letter key.
            cipher = "DLCMERKRBDLCNSEKRBDLCMER"

            # Every trigram that occurs more than once, with the distances between its occurrences.
            positions = {}
            for i in range(len(cipher) - 2):
                positions.setdefault(cipher[i:i + 3], []).append(i)
            repeats = {gram: spots for gram, spots in positions.items() if len(spots) > 1}
            distances = []
            for gram in sorted(repeats):
                spots = repeats[gram]
                for a, b in zip(spots, spots[1:]):
                    distances.append(b - a)
                    print(f"{gram} at {a} and {b}: distance {b - a}")
            print("distances:", distances)

            # Kasiski: the key length is likely to be a factor of most distances, so count the factors of each distance.
            counts = Counter()
            for d in distances:
                for f in range(2, d + 1):
                    if d % f == 0:
                        counts[f] += 1
            for f in sorted(counts):
                print(f"factor {f} appears {counts[f]} times")
            best = max(sorted(counts), key=lambda f: counts[f])
            print(f"suggested key length: {best}")
        """,
    ),

    entry(
        databaseId = 18,
        kind = Kind.EXAMPLE,
        section = "Heredity",
        title = "How Mendel's crosses give the 3 to 1 ratio",
        originalTitle = "Versuche ueber Pflanzen-Hybriden (1866)",
        author = "Gregor Mendel",
        year = 1866,
        summary = "Crossing two plants that differ in one trait gives three dominant offspring to one recessive, and crossing on two traits gives nine, three, three and one.",
        details = "Mendel read his experiments on pea hybrids to the Brno natural history society in 1865, and the paper was printed in 1866. The example lists the four combinations of two heterozygous parents for one trait and the sixteen combinations for two traits, and counts them with exact fractions.",
        instructions = """
            1. Save the example as mendel.py.
            2. Run it: python3 mendel.py
        """,
        example = """
            from fractions import Fraction as F
            from itertools import product

            # One trait: each parent is Aa, and each offspring takes one allele from each parent.
            def genotype(x, y):
                if x == y == "A":
                    return "AA"
                if x == y == "a":
                    return "aa"
                return "Aa"

            parent = ["A", "a"]
            counts = {}
            for x, y in product(parent, parent):
                g = genotype(x, y)
                counts[g] = counts.get(g, 0) + 1
            total = sum(counts.values())
            for g in ["AA", "Aa", "aa"]:
                print(f"genotype {g}: {F(counts.get(g, 0), total)}")
            dominant = F(counts["AA"] + counts["Aa"], total)
            print(f"dominant trait: {dominant}")
            print(f"ratio dominant to recessive: {counts['AA'] + counts['Aa']}:{counts['aa']}")

            # Two traits: each parent makes four gamete types, and the sixteen pairings are counted.
            gametes = ["AB", "Ab", "aB", "ab"]
            phenotypes = {}
            for p, q in product(gametes, gametes):
                shape = "dominant" if "A" in p[0] + q[0] else "recessive"
                colour = "dominant" if "B" in p[1] + q[1] else "recessive"
                phenotypes[(shape, colour)] = phenotypes.get((shape, colour), 0) + 1
            for shape in ["dominant", "recessive"]:
                for colour in ["dominant", "recessive"]:
                    print(f"{shape} shape, {colour} colour: {F(phenotypes.get((shape, colour), 0), 16)}")
        """,
    ),

    entry(
        databaseId = 18,
        kind = Kind.EXAMPLE,
        section = "Thermodynamics",
        title = "How Clausius's entropy balances a reversible cycle",
        originalTitle = "Ueber verschiedene fuer die Anwendung bequeme Formen der Hauptgleichungen der mechanischen Waermetheorie (1865)",
        author = "Rudolf Clausius",
        year = 1865,
        summary = "Heat taken in at one temperature and given out at another gives entropy changes that cancel in a reversible cycle, but add up to a gain when heat flows directly between the same reservoirs.",
        details = "Clausius introduced the word entropy in his 1865 paper, for the quantity whose change is heat divided by temperature for reversible exchanges. The example runs a Carnot cycle between temperatures 500 and 250 with exact fractions, then compares it with heat passed directly between the same two reservoirs.",
        instructions = """
            1. Save the example as entropy.py.
            2. Run it: python3 entropy.py
        """,
        example = """
            from fractions import Fraction as F

            # Clausius's entropy: a reservoir at temperature T that gives or takes heat Q changes its entropy by Q / T.
            def entropy_change(q, temperature):
                return F(q) / F(temperature)

            T_hot = F(500)
            T_cold = F(250)
            Q_in = F(1000)                  # heat taken from the hot reservoir in one cycle
            Q_out = Q_in * T_cold / T_hot   # Carnot cycle: heat rejected to the cold reservoir
            dS_hot = -entropy_change(Q_in, T_hot)
            dS_cold = entropy_change(Q_out, T_cold)
            print(f"heat rejected: {Q_out}")
            print(f"hot reservoir: {dS_hot}")
            print(f"cold reservoir: {dS_cold}")
            print(f"reversible cycle total: {dS_hot + dS_cold}")

            # Heat passed directly from the hot reservoir to the cold one, with no work done.
            direct = -entropy_change(Q_in, T_hot) + entropy_change(Q_in, T_cold)
            print(f"direct conduction total: {direct}")
            print(f"entropy grows: {direct > 0}")
        """,
    ),

    entry(
        databaseId = 18,
        kind = Kind.EXAMPLE,
        section = "Statistical mechanics",
        title = "How Boltzmann's energy shares fall off with each level",
        originalTitle = "Studien ueber das Gleichgewicht der lebendigen Kraft zwischen bewegten materiellen Punkten (1868)",
        author = "Ludwig Boltzmann",
        year = 1868,
        summary = "A system in equilibrium gives each energy level a share that falls off exponentially with the energy, so higher levels are less likely to be occupied.",
        details = "Boltzmann's 1868 paper extended Maxwell's energy law to particles moving in a field of force, and the exponential form was developed in his later papers. The example uses three levels with a factor of one half per level, so the shares come out as exact fractions that add to one.",
        instructions = """
            1. Save the example as boltzmann.py.
            2. Run it: python3 boltzmann.py
        """,
        example = """
            from fractions import Fraction as F

            # Three energy levels 0, 1 and 2, in units of kT. Each step up multiplies the weight by one half,
            # which is the factor exp(-E) with the spacing chosen to give exactly one half.
            levels = [0, 1, 2]
            weights = [F(1, 2) ** E for E in levels]
            total = sum(weights)
            shares = [w / total for w in weights]
            for E, share in zip(levels, shares):
                print(f"level {E}: share {share}")
            print("shares add to one: " + str(sum(shares) == 1))
            average = sum(E * s for E, s in zip(levels, shares))
            print(f"average energy: {average}")
        """,
    ),

    entry(
        databaseId = 18,
        kind = Kind.EXAMPLE,
        section = "Integer matrices",
        title = "How Smith's normal form reduces an integer matrix",
        originalTitle = "On systems of linear indeterminate equations and congruences (1861)",
        author = "Henry John Stephen Smith",
        year = 1861,
        summary = "Row and column steps turn an integer matrix into a diagonal one whose entries divide each other, and that diagonal shows when a system of integer equations can be solved.",
        details = "Smith published the diagonal form in the Philosophical Transactions in 1861, and its entries are now called invariant factors. The example reduces a two by two integer matrix: the first factor is the greatest common divisor of the entries, and the second follows from the determinant.",
        instructions = """
            1. Save the example as smith.py.
            2. Run it: python3 smith.py
        """,
        example = """
            from math import gcd

            # Smith's diagonal form of a two by two integer matrix. The first invariant factor is the gcd of the entries,
            # and the product of the two invariant factors is the absolute value of the determinant.
            A = [[2, 4], [6, 9]]
            print("start: " + str(A))
            (a, b), (c, d) = A
            det = a * d - b * c
            d1 = gcd(gcd(a, b), gcd(c, d))
            d2 = abs(det) // d1
            print(f"first invariant factor: {d1}")
            print(f"second invariant factor: {d2}")
            print(f"first divides second: {d2 % d1 == 0}")
        """,
    ),

    entry(
        databaseId = 18,
        kind = Kind.EXAMPLE,
        section = "Gear design",
        title = "How the Stern-Brocot tree finds a simple gear ratio",
        originalTitle = "Calcul des rouages par approximation, nouvelle methode (1861)",
        author = "Achille Brocot",
        year = 1861,
        summary = "Walking down the Stern-Brocot tree gives the simplest fraction near a target ratio, which a clockmaker can turn into a pair of gear wheels with whole numbers of teeth.",
        details = "Brocot published his gear-train method in the Revue chronometrique in 1861, pages 186 to 194, while a book with the same title is catalogued under 1862, so accounts differ on the date. Stern had described the same tree in 1858, and Brocot arrived at it independently, so the two are credited separately. The example walks the tree toward the golden ratio and stops when the next fraction needs a denominator above ten.",
        instructions = """
            1. Save the example as brocot.py.
            2. Run it: python3 brocot.py
        """,
        example = """
            from fractions import Fraction as F

            # Stern-Brocot walk toward a target ratio. Each step makes the mediant of the two bounds,
            # and the walk stops when the mediant needs a denominator above the limit.
            target = 0.6180339887   # about (sqrt 5 - 1) / 2
            limit = 10
            left = (0, 1)           # 0/1
            right = (1, 0)          # 1/0 stands for infinity
            while True:
                n = left[0] + right[0]
                d = left[1] + right[1]
                if d > limit:
                    print(f"next mediant {n}/{d} has denominator {d} above {limit}")
                    break
                m = F(n, d)
                if m < target:
                    left = (n, d)
                    print(f"{m} is below the target, left bound becomes {m}")
                else:
                    right = (n, d)
                    print(f"{m} is above the target, right bound becomes {m}")
            low = F(*left)
            high = F(*right)
            print(f"distance from {low}: {abs(low - target):.4f}")
            print(f"distance from {high}: {abs(high - target):.4f}")
            best = low if abs(low - target) < abs(high - target) else high
            print(f"best with denominator at most {limit}: {best}")
        """,
    ),

)
