package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1920 to 1930: short programs for a recursive function, a punched card, a colouring count, a walk, a primitive recursion and a rotor. */
val db12Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 12,
        kind = Kind.EXAMPLE,
        section = "Recursion",
        title = "Ackermann's function for small inputs",
        originalTitle = "Ackermann's function (Ackermann, 1928; two-argument form by Peter and Robinson)",
        author = "Written as a teaching example",
        year = 1928,
        summary = "Evaluates the two-argument Ackermann function, a computable function that grows faster than any primitive recursive one, for small inputs.",
        details = "Ackermann's 1928 function took three arguments, and it showed that a computable function can lie outside the primitive recursive class. Rozsa Peter cut it to two arguments in 1935, and Robinson streamlined the base cases in 1948. The example prints seven values small enough to check by hand, and the closed forms A(1,n) = n + 2, A(2,n) = 2n + 3 and A(3,n) = 2^(n+3) - 3 give the same numbers.",
        instructions = """
            1. Save the example as ackermann.py.
            2. Run it: python3 ackermann.py
        """,
        example = """
            import sys

            # Two-argument Ackermann function, the form given by Peter (1935) and Robinson (1948).
            def A(m, n):
                if m == 0:
                    return n + 1
                if n == 0:
                    return A(m - 1, 1)
                return A(m - 1, A(m, n - 1))

            sys.setrecursionlimit(10000)
            for m, n in [(0, 5), (1, 4), (2, 2), (2, 3), (3, 0), (3, 1), (3, 3)]:
                print(f"A({m},{n}) = {A(m, n)}")
        """,
    ),

    entry(
        databaseId = 12,
        kind = Kind.EXAMPLE,
        section = "Punched cards",
        title = "A punched-card encoder for the 80-column card",
        originalTitle = "Hollerith code on the 80-column card (IBM, 1928)",
        author = "Written as a teaching example",
        year = 1928,
        summary = "Encodes characters as punch rows on the 80-column card, with zone rows 12, 11 and 0 and digit rows 1 to 9.",
        details = "The 80-column card with rectangular holes is credited to IBM in 1928, but a 2012 account places similar keypunches with British Tabulating Machine Company as early as 1919, so the priority is disputed. Letters A to I take a 12 punch with a digit, J to R an 11 punch with a digit, and S to Z a 0 punch with a digit from 2 to 9. Digits take one punch each, and a space takes none. The example encodes a short string and prints the punches of each character.",
        instructions = """
            1. Save the example as card.py.
            2. Run it: python3 card.py
        """,
        example = """
            # Hollerith code: rows 12, 11 and 0 are zone punches, rows 1 to 9 are digit punches.
            def punches(ch):
                if ch == " ":
                    return []
                if ch.isdigit():
                    return [ch]
                if "A" <= ch <= "I":
                    return ["12", str(ord(ch) - ord("A") + 1)]
                if "J" <= ch <= "R":
                    return ["11", str(ord(ch) - ord("J") + 1)]
                if "S" <= ch <= "Z":
                    return ["0", str(ord(ch) - ord("S") + 2)]
                raise ValueError("no punch for " + ch)

            for ch in "A1 S0J":
                rows = punches(ch)
                shown = "-".join(rows) if rows else "none"
                print(repr(ch), "->", shown)
        """,
    ),

    entry(
        databaseId = 12,
        kind = Kind.EXAMPLE,
        section = "Topology",
        title = "Sperner's lemma on a subdivided triangle",
        originalTitle = "Sperner's lemma (Sperner, 1928)",
        author = "Written as a teaching example",
        year = 1928,
        summary = "A triangle cut into four pieces, with corners labelled 1, 2 and 3, always has an odd number of pieces whose corners carry all three labels.",
        details = "The example checks all eight admissible labellings of the three midpoints and prints the number of fully labelled pieces for each. Every count is 1, which is odd, as the lemma requires. The lemma is one of the standard tools of topological combinatorics.",
        instructions = """
            1. Save the example as sperner.py.
            2. Run it: python3 sperner.py
        """,
        example = """
            from itertools import product

            # Triangle ABC is cut into four small triangles; D, E and F are the midpoints of AB, BC and CA.
            # Corners get the labels 1, 2 and 3. A midpoint takes a label of the edge it lies on: D gets 1 or 2, E gets 2 or 3, F gets 3 or 1.
            TRIANGLES = [("A", "D", "F"), ("D", "B", "E"), ("F", "E", "C"), ("D", "E", "F")]
            CORNERS = {"A": 1, "B": 2, "C": 3}

            counts = []
            for d, e, f in product([1, 2], [2, 3], [3, 1]):
                labels = dict(CORNERS, D=d, E=e, F=f)
                full = sum(1 for tri in TRIANGLES if {labels[v] for v in tri} == {1, 2, 3})
                counts.append(full)
                print(f"D={d} E={e} F={f}: {full} fully labelled")
            print("all counts odd:", all(c % 2 == 1 for c in counts))
        """,
    ),

    entry(
        databaseId = 12,
        kind = Kind.EXAMPLE,
        section = "Random walks",
        title = "Random walk return probabilities",
        originalTitle = "Ueber eine Aufgabe der Wahrscheinlichkeitsrechnung betreffend die Irrfahrt im Strassennetz (Polya, 1921)",
        author = "Written as a teaching example",
        year = 1921,
        summary = "Counts the walks on the integers that return to their start after an even number of steps, and compares the count with the binomial formula.",
        details = "Polya's 1921 paper sorted walks on the lattice by dimension: in one and two dimensions the walk returns to its start with probability one, and in three or more it does not. The example checks the one-dimensional return probabilities for 2, 4, 6 and 8 steps by enumerating every path. They equal the central binomial coefficient divided by 4 to the power n, which is 1/2, 3/8, 5/16 and 35/128.",
        instructions = """
            1. Save the example as walk.py.
            2. Run it: python3 walk.py
        """,
        example = """
            from fractions import Fraction
            from itertools import product

            # Simple random walk on the integers: each step is +1 or -1, with equal chance.
            def return_probability(steps):
                back = 0
                for path in product((1, -1), repeat=steps):
                    if sum(path) == 0:
                        back += 1
                return Fraction(back, 2 ** steps)

            for steps in (2, 4, 6, 8):
                print(f"return to 0 after {steps} steps: {return_probability(steps)}")
        """,
    ),

    entry(
        databaseId = 12,
        kind = Kind.EXAMPLE,
        section = "Arithmetic",
        title = "Primitive recursion: addition and multiplication from the successor",
        originalTitle = "Begruendung der elementaren Arithmetik (Skolem, 1923)",
        author = "Written as a teaching example",
        year = 1923,
        summary = "Defines addition, multiplication and powers by primitive recursion, using only a base case and one recursive step for each.",
        details = "Skolem's 1923 paper built elementary arithmetic from recursive definitions, without quantifiers over infinite domains, in the system now called primitive recursive arithmetic. Ackermann's function of 1928 grows faster than any function in this class. The example defines three operations, each with a base case and one recursive step, and prints four results that can be checked by hand.",
        instructions = """
            1. Save the example as pra.py.
            2. Run it: python3 pra.py
        """,
        example = """
            # Primitive recursion: each function has a base case and one recursive step.
            def add(x, y):
                # add(x, 0) = x and add(x, succ(y)) = succ(add(x, y))
                if y == 0:
                    return x
                return add(x, y - 1) + 1

            def mul(x, y):
                # mul(x, 0) = 0 and mul(x, succ(y)) = add(mul(x, y), x)
                if y == 0:
                    return 0
                return add(mul(x, y - 1), x)

            def power(x, y):
                # power(x, 0) = 1 and power(x, succ(y)) = mul(power(x, y), x)
                if y == 0:
                    return 1
                return mul(power(x, y - 1), x)

            print("add(3, 4) =", add(3, 4))
            print("mul(3, 4) =", mul(3, 4))
            print("power(2, 5) =", power(2, 5))
            print("mul(6, 7) =", mul(6, 7))
        """,
    ),

    entry(
        databaseId = 12,
        kind = Kind.EXAMPLE,
        section = "Rotor machines",
        title = "A rotor cipher with a stepping rotor",
        originalTitle = "Rotor cipher machine of Hebern (1921), a five-letter teaching model",
        author = "Written as a teaching example",
        year = 1921,
        summary = "A five-letter rotor turns one step after each letter, so the same plain letter gives a different cipher letter each time.",
        details = "Edward Hebern filed for a patent on an electric rotor cipher machine in 1921, granted in 1924 as US 1,510,441, and his first rotor drawings date from 1917. The example uses a made-up wiring of five letters, not Hebern's, to show how stepping changes the output: four As encrypt to CDDD, and the decryption run recovers them.",
        instructions = """
            1. Save the example as hebern.py.
            2. Run it: python3 hebern.py
        """,
        example = """
            # A made-up rotor on the letters A to E. The rotor turns one step after each letter.
            WIRING = [2, 4, 0, 1, 3]   # A to C, B to E, C to A, D to B, E to D
            N = 5

            def encrypt_letter(i, k):
                # Input letter i at rotor position k: pass through the wiring, then shift back by k.
                return (WIRING[(i + k) % N] - k) % N

            def decrypt_letter(c, k):
                # Find the input letter that encrypts to c at position k.
                for i in range(N):
                    if encrypt_letter(i, k) == c:
                        return i
                raise ValueError("no letter")

            def run(text, decrypt=False):
                out = []
                for k, ch in enumerate(text):
                    if decrypt:
                        out.append(decrypt_letter(ord(ch) - ord("A"), k))
                    else:
                        out.append(encrypt_letter(ord(ch) - ord("A"), k))
                return "".join(chr(v + ord("A")) for v in out)

            cipher = run("AAAA")
            print("encrypt AAAA:", cipher)
            plain = run(cipher, decrypt=True)
            print("decrypt", cipher + ":", plain)
            print("round trip ok:", plain == "AAAA")
        """,
    ),
)
