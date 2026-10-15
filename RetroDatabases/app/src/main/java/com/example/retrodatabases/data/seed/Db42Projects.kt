package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1620 to 1630: a simulated wheel adder, a request matcher, logarithms by square roots and a doubling of grains. */
val db42Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 42,
        kind = Kind.PROJECT,
        section = "Calculating",
        title = "A six-wheel adder that carries like Schickard's clock",
        originalTitle = "Rechenuhr, letter to Kepler",
        author = "Wilhelm Schickard",
        year = 1623,
        summary = "A program simulates a row of decimal wheels that add a number and pass each carry to the wheel on the left, as Schickard's wheels carried their tens.",
        details = "Schickard's clock of 1623 added with wheels that carried a ten into the next wheel. The project simulates a six-wheel register, adds four numbers to it, prints the wheel readings after each sum and reports the overflow past the last wheel.",
        instructions = """
            1. Save the example as schickard_adder.py.
            2. Run it: python3 schickard_adder.py
        """,
        example = """
            def add_on_wheels(wheels, number):
                # Each wheel holds a digit from 0 to 9; a carry of ten moves one wheel to the left.
                digits = [int(d) for d in f"{number:06d}"]
                carry = 0
                for i in range(5, -1, -1):
                    total = wheels[i] + digits[i] + carry
                    wheels[i] = total % 10
                    carry = total // 10
                return carry

            wheels = [0, 0, 0, 0, 0, 0]
            for number in (9, 991, 12345, 999999):
                overflow = add_on_wheels(wheels, number)
                print("added", number, "wheels", "".join(map(str, wheels)), "overflow", overflow)
        """,
    ),

    entry(
        databaseId = 42,
        kind = Kind.PROJECT,
        section = "Information",
        title = "A request matcher for the Bureau",
        originalTitle = "Bureau d'adresse et de rencontre",
        author = "Theophraste Renaudot",
        year = 1630,
        summary = "A program matches requests with offers by shared keywords and prints each matched pair, in the way the Bureau's register brought buyers and sellers together.",
        details = "The Bureau brought requests and offers together through its register. The project matches invented requests with invented offers by their shared words and prints each pair, and it makes no claim about how the Bureau matched its own entries.",
        instructions = """
            1. Save the example as bureau_matcher.py.
            2. Run it: python3 bureau_matcher.py
        """,
        example = """
            REQUESTS = [
                ("a cook for meals", {"cook", "meals"}),
                ("a tutor in Latin", {"tutor", "latin"}),
                ("a copyist", {"copy", "pen"}),
            ]
            OFFERS = [
                ("cook with a kitchen", {"cook", "kitchen"}),
                ("Latin lessons", {"latin", "lessons"}),
                ("hand-made pens", {"pen", "ink"}),
            ]

            for request, need in REQUESTS:
                best_offer, best_words = max(OFFERS, key=lambda offer: len(need & offer[1]))
                shared = need & best_words
                if shared:
                    print(f"{request} -> {best_offer} (shared: {', '.join(sorted(shared))})")
                else:
                    print(f"{request} -> no match")
        """,
    ),

    entry(
        databaseId = 42,
        kind = Kind.PROJECT,
        section = "Logarithms",
        title = "Logarithms by chained square roots of ten",
        originalTitle = "Arithmetica logarithmica",
        author = "Henry Briggs",
        year = 1624,
        summary = "Briggs's sixth chapter finds logarithms by taking square roots of ten again and again, and a short program computes the logs of 2, 3, 5 and 7 this way.",
        details = "Each square root halves the logarithm still to be found, so the program takes the roots of ten in turn and keeps each one that the value still exceeds. It prints the logarithms of four primes to four decimal places.",
        instructions = """
            1. Save the example as briggs_roots.py.
            2. Run it: python3 briggs_roots.py
        """,
        example = """
            import math

            def log10_by_roots(x, steps=40):
                # Chained square roots of ten: each root halves the exponent still to be found.
                y, value, root, weight = 0.0, float(x), 10.0, 1.0
                for _ in range(steps):
                    root = math.sqrt(root)
                    weight /= 2
                    if value >= root:
                        value /= root
                        y += weight
                return y

            for x in (2, 3, 5, 7):
                print(f"log10 of {x} by square roots: {log10_by_roots(x):.4f}")
        """,
    ),

    entry(
        databaseId = 42,
        kind = Kind.PROJECT,
        section = "Arithmetic",
        title = "Doubling grains on a chessboard",
        originalTitle = "Jinkoki",
        author = "Yoshida Mitsuyoshi",
        year = 1627,
        summary = "A doubling problem, of the kind set in arithmetic books of the period, counts the grains on a board that doubles from square to square.",
        details = "The grains double from one square of the board to the next, so the square with number n holds two to the power of n minus one grains. The project prints three squares and the total over all sixty-four, which is one less than two to the power of sixty-four.",
        instructions = """
            1. Save the example as doubling.py.
            2. Run it: python3 doubling.py
        """,
        example = """
            def grains(square):
                # Each square holds twice the grains of the square before it.
                return 2 ** (square - 1)

            for square in (1, 10, 64):
                print(f"grains on square {square}: {grains(square)}")
            print(f"total on all 64 squares: {sum(grains(s) for s in range(1, 65))}")
        """,
    ),

)
