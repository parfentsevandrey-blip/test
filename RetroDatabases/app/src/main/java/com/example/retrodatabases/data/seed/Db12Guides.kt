package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1920 to 1930: the techniques of the decade, each with a small program that runs today. */
val db12Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 12,
        kind = Kind.GUIDE,
        section = "Notation",
        title = "How Polish notation evaluates an expression",
        originalTitle = "Polish notation (Jan Lukasiewicz, 1924)",
        author = "Jan Lukasiewicz",
        year = 1924,
        summary = "Prefix notation puts each operator before its operands, so no brackets are needed and one pass with a stack evaluates the expression.",
        details = "Lukasiewicz introduced the notation in 1924, and his own 1931 account dates the idea to 1924 and says it first appeared in an earlier footnote. The example reads the tokens from right to left, so each operator finds its two operands on the stack. It prints four expressions with their values, and each value can be checked by hand.",
        instructions = """
            1. Save the example as polish.py.
            2. Run it: python3 polish.py
        """,
        example = """
            def evaluate(tokens):
                # Read the tokens from right to left: each operator takes the two values that come after it.
                stack = []
                for tok in reversed(tokens):
                    if tok in ("+", "-", "*"):
                        left = stack.pop()
                        right = stack.pop()
                        if tok == "+":
                            stack.append(left + right)
                        elif tok == "-":
                            stack.append(left - right)
                        else:
                            stack.append(left * right)
                    else:
                        stack.append(int(tok))
                return stack.pop()

            for text in ["+ 2 3", "* + 2 3 4", "- 10 * 2 3", "+ * 2 3 * 4 5"]:
                print(text, "=", evaluate(text.split()))
        """,
    ),

    entry(
        databaseId = 12,
        kind = Kind.GUIDE,
        section = "Rewriting",
        title = "How a tag system runs step by step",
        originalTitle = "Tag systems (Post, 1921; printed 1943)",
        author = "Emil Post",
        year = 1921,
        summary = "A word is rewritten by deleting a fixed number of symbols from its front and appending a production for the first symbol, until a halt symbol appears.",
        details = "Post worked out the idea in 1920 and 1921, but the systems were first printed in 1943, so the year is a choice. The example runs a 2-tag system: each step deletes two symbols, appends the production of the first symbol, and stops when the first symbol is the halt symbol h. It prints the word at every step.",
        instructions = """
            1. Save the example as tag.py.
            2. Run it: python3 tag.py
        """,
        example = """
            # A 2-tag system: each step deletes the first two symbols, then appends the production of the first symbol.
            PRODUCTIONS = {"a": "bb", "b": "h"}
            HALT = "h"

            def run(word, max_steps=10):
                print(word)
                for _ in range(max_steps):
                    if word[0] == HALT:
                        print("halt: the first symbol is h")
                        return
                    word = word[2:] + PRODUCTIONS[word[0]]
                    print(word)
                print("stopped after", max_steps, "steps")

            run("ab")
        """,
    ),

    entry(
        databaseId = 12,
        kind = Kind.GUIDE,
        section = "Eigenvalues",
        title = "How the power method finds the largest eigenvalue",
        originalTitle = "The power method for a dominant eigenvalue (von Mises, 1929)",
        author = "Richard von Mises and Hilda Pollaczek-Geiringer",
        year = 1929,
        summary = "Repeated multiplication by a matrix turns a starting vector toward the eigenvector of the largest eigenvalue, and a quotient of two dot products reads that eigenvalue off.",
        details = "Each step multiplies the vector by the matrix, and the Rayleigh quotient of the vector estimates the dominant eigenvalue. The example uses the symmetric matrix with rows [2, 1] and [1, 2], whose eigenvalues are 3 and 1, starts from the vector [1, 0], and prints four estimates. The error of the quotient shrinks as the square of the eigenvalue ratio, one third here, so it falls by about a factor of 9 per step.",
        instructions = """
            1. Save the example as power_method.py.
            2. Run it: python3 power_method.py
        """,
        example = """
            from fractions import Fraction

            # Power method for the symmetric matrix A = [[2, 1], [1, 2]], whose eigenvalues are 3 and 1.
            A = [[2, 1], [1, 2]]

            def apply(M, v):
                return [M[0][0] * v[0] + M[0][1] * v[1], M[1][0] * v[0] + M[1][1] * v[1]]

            def dot(a, b):
                return a[0] * b[0] + a[1] * b[1]

            v = [1, 0]
            for step in range(1, 5):
                v = apply(A, v)
                # Rayleigh quotient of the current vector: an estimate of the dominant eigenvalue.
                estimate = Fraction(dot(v, apply(A, v)), dot(v, v))
                print(f"step {step}: estimate {float(estimate):.4f}")
            print("exact dominant eigenvalue: 3")
        """,
    ),

    entry(
        databaseId = 12,
        kind = Kind.GUIDE,
        section = "Classical ciphers",
        title = "How a Hill cipher encrypts a pair of letters",
        originalTitle = "Cryptography in an Algebraic Alphabet (Hill, 1929)",
        author = "Lester S. Hill",
        year = 1929,
        summary = "Hill's cipher turns each pair of letters into a pair of letters by multiplying a vector of numbers by a key matrix, modulo 26.",
        details = "Hill published the idea in the American Mathematical Monthly in 1929. The key matrix has an inverse modulo 26 when its determinant shares no factor with 26, and then the inverse matrix decrypts. The example encrypts HELP with the matrix [[3, 3], [2, 5]], decrypts the result with its inverse [[15, 17], [20, 9]], and checks the round trip.",
        instructions = """
            1. Save the example as hill.py.
            2. Run it: python3 hill.py
        """,
        example = """
            # Hill cipher with the key matrix [[3, 3], [2, 5]] over the letters A = 0 to Z = 25.
            KEY = [[3, 3], [2, 5]]
            # Inverse of KEY modulo 26: the determinant is 9, and 9 * 3 = 27 = 1 modulo 26.
            INVERSE = [[15, 17], [20, 9]]

            def to_numbers(text):
                return [ord(c) - ord("A") for c in text]

            def to_text(numbers):
                return "".join(chr(n % 26 + ord("A")) for n in numbers)

            def apply(matrix, text):
                # Each pair of letters is multiplied by the matrix, modulo 26.
                nums = to_numbers(text)
                out = []
                for i in range(0, len(nums), 2):
                    a, b = nums[i], nums[i + 1]
                    out.append((matrix[0][0] * a + matrix[0][1] * b) % 26)
                    out.append((matrix[1][0] * a + matrix[1][1] * b) % 26)
                return to_text(out)

            cipher = apply(KEY, "HELP")
            print("cipher:", cipher)
            plain = apply(INVERSE, cipher)
            print("decrypted:", plain)
            print("round trip ok:", plain == "HELP")
        """,
    ),

    entry(
        databaseId = 12,
        kind = Kind.GUIDE,
        section = "Urns",
        title = "How a Polya urn fills",
        originalTitle = "Ueber die Statistik verketteter Vorgaenge (Eggenberger and Polya, 1923)",
        author = "F. Eggenberger and G. Polya",
        year = 1923,
        summary = "A ball is drawn, returned and joined by one more of its colour, and every count of red balls is equally likely when the urn starts with one of each.",
        details = "The model appeared in the Zeitschrift fur Angewandte Mathematik und Mechanik in 1923, volume 3, on pages 279 to 289 by most references. The example computes exact probabilities with fractions and no random numbers: the chance of a sequence is a product of ratios, and the chance of k reds after three draws is one quarter for every k.",
        instructions = """
            1. Save the example as polya_urn.py.
            2. Run it: python3 polya_urn.py
        """,
        example = """
            from fractions import Fraction
            from itertools import product

            # One red and one black ball; each draw returns the ball and adds one more of its colour.
            def probability(sequence):
                red, black = 1, 1
                p = Fraction(1)
                for colour in sequence:
                    total = red + black
                    if colour == "R":
                        p *= Fraction(red, total)
                        red += 1
                    else:
                        p *= Fraction(black, total)
                        black += 1
                return p

            print("P(RRB) =", probability("RRB"))

            n = 3
            dist = {}
            for seq in product("RB", repeat=n):
                k = seq.count("R")
                dist[k] = dist.get(k, Fraction(0)) + probability(seq)
            print("P(k reds), k = 0..3:", " ".join(str(dist[k]) for k in range(n + 1)))
            print("uniform:", all(dist[k] == Fraction(1, n + 1) for k in range(n + 1)))
        """,
    ),
)
