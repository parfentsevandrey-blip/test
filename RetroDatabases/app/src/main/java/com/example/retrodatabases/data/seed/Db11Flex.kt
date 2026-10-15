package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1930 to 1940: short programs for a theorem, a shuffle and a diagonal argument. */
val db11Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 11,
        kind = Kind.FLEX,
        section = "Logic",
        title = "Ramsey's theorem for six points",
        originalTitle = "On a problem of formal logic (Ramsey, 1930)",
        author = "Frank Ramsey",
        year = 1930,
        summary = "Colour every pair of six points red or blue and some three points are joined by one colour, and the program checks all 32768 colourings.",
        details = "Ramsey proved in 1930 that large enough sets always contain a group of the given size with one colour. The number six for triangles was settled later, by Greenwood and Gleason in 1955. The example shows that five points can avoid a one-colour triangle, using the pentagon pattern, and that every colouring of six points fails.",
        rare = true,
        instructions = """
            1. Save the example as ramsey.py.
            2. Run it: python3 ramsey.py
        """,
        example = """
            from itertools import combinations, product

            def has_mono_triangle(n, colours):
                # colours maps each pair (i, j) with i < j to 0 or 1.
                for a, b, c in combinations(range(n), 3):
                    if colours[(a, b)] == colours[(a, c)] == colours[(b, c)]:
                        return True
                return False

            pentagon = {}
            for i, j in combinations(range(5), 2):
                # Pairs one step apart on the pentagon get colour 0, pairs two steps apart get colour 1.
                pentagon[(i, j)] = 0 if (j - i) % 5 in (1, 4) else 1
            print("K5 pentagon colouring has no one-colour triangle:", not has_mono_triangle(5, pentagon))

            edges = list(combinations(range(6), 2))
            count = 0
            for bits in product((0, 1), repeat=len(edges)):
                if not has_mono_triangle(6, dict(zip(edges, bits))):
                    count += 1
            print(f"K6 colourings: {2 ** len(edges)}, without a one-colour triangle: {count}")
        """,
    ),

    entry(
        databaseId = 11,
        kind = Kind.FLEX,
        section = "Permutations",
        title = "A Fisher and Yates shuffle with fixed picks",
        originalTitle = "Statistical tables for biological, agricultural and medical research (Fisher and Yates, 1938)",
        author = "Ronald Fisher and Frank Yates",
        year = 1938,
        summary = "Shuffling swaps each position with a chosen position at or before it, which gives a permutation, and here the picks are fixed so the output can be checked by hand.",
        details = "Fisher and Yates gave a hand-operated routine for random order in their 1938 tables. Durstenfeld published the swap form used by computers in 1964. The example runs the swap form with fixed picks, so the result is known in advance, and checks that the output is still a permutation of the input.",
        instructions = """
            1. Save the example as shuffle.py.
            2. Run it: python3 shuffle.py
        """,
        example = """
            def shuffle(items, picks):
                # picks[k] is the index chosen for position i = n - 1 - k. The picks are fixed, not random.
                a = list(items)
                n = len(a)
                for k, j in enumerate(picks):
                    i = n - 1 - k
                    if not 0 <= j <= i:
                        raise ValueError("pick out of range")
                    a[i], a[j] = a[j], a[i]
                return a

            result = shuffle([1, 2, 3, 4, 5], [0, 1, 2, 0])
            print("shuffled:", result)
            print("still a permutation:", sorted(result) == [1, 2, 3, 4, 5])
        """,
    ),

    entry(
        databaseId = 11,
        kind = Kind.FLEX,
        section = "Computability",
        title = "Turing's diagonal table, a function no listed machine computes",
        originalTitle = "The diagonal construction in On computable numbers (Turing, 1936)",
        author = "Alan Turing",
        year = 1936,
        summary = "Given a list of machines, flipping the diagonal of their outputs gives a function that every machine in the list gets wrong on at least one input.",
        details = "The argument follows the diagonal method of Cantor. Turing used it in 1936 to show that some questions about machines have no general answer. The example writes four machines as rows of outputs, flips each diagonal bit, and checks that the new row differs from every listed row.",
        instructions = """
            1. Save the example as diagonal.py.
            2. Run it: python3 diagonal.py
        """,
        example = """
            # Row i holds the output of machine i on the inputs 0 to 3, one bit per input.
            MACHINES = [
                [0, 1, 1, 0],
                [1, 1, 0, 0],
                [0, 0, 0, 1],
                [1, 0, 1, 1],
            ]
            for i, row in enumerate(MACHINES):
                print(f"machine {i}: " + " ".join(str(b) for b in row))

            # The diagonal function flips the bit that each machine gives on its own index.
            diagonal = [1 - MACHINES[j][j] for j in range(len(MACHINES))]
            print("diagonal:", " ".join(str(b) for b in diagonal))
            print("differs from every listed machine:", all(diagonal[i] != MACHINES[i][i] for i in range(len(MACHINES))))
        """,
    ),
)
