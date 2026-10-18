package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1150 to 1160: the rule of three, the rules of signs, pulley blocks in a water clock, the climates of a world map and Bhaskara's lotus problem. */
val db89Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 89,
        kind = Kind.GUIDE,
        section = "Roots",
        title = "How the Indian digit method finds the whole square root of a number",
        originalTitle = "Square root by digit pairs",
        author = "Aryabhata and Indian arithmetic of the 1150s",
        year = 1150,
        summary = "The Indian digit method takes a square root two digits at a time, and a short program applies it to three numbers and checks each against an exact integer root.",
        details = "Aryabhata gave the rule in 499, and it was still taught in Indian arithmetic in the 1150s. The example groups the digits in pairs, finds each root digit by trying the largest digit that fits, and reports the whole root and the remainder for each number.",
        instructions = """
            1. Save the example as digit_root.py.
            2. Run it: python3 digit_root.py
        """,
        example = """
            from math import isqrt

            def digit_root(n):
                # digit pairs from the right; each step takes the largest digit that keeps the square inside the remainder
                pairs = []
                while n > 0:
                    pairs.append(n % 100)
                    n //= 100
                pairs.reverse()
                root, rest = 0, 0
                for pair in pairs:
                    rest = rest * 100 + pair
                    d = 0
                    while (20 * root + (d + 1)) * (d + 1) <= rest:
                        d += 1
                    rest -= (20 * root + d) * d
                    root = root * 10 + d
                return root, rest

            for number in (1154, 2025, 1150):
                root, remainder = digit_root(number)
                print(number, "root", root, "remainder", remainder, "matches isqrt:", root == isqrt(number) and remainder == number - root * root)
        """,
    ),

    entry(
        databaseId = 89,
        kind = Kind.GUIDE,
        section = "Signs",
        title = "How the rules of signs give the product of two negative numbers",
        originalTitle = "Rules of signs",
        author = "Brahmagupta and Bhaskara II",
        year = 1150,
        summary = "The rules of signs say that a negative times a negative is positive, and a short program applies them to four products and checks each against Python's own multiplication.",
        details = "Brahmagupta stated the rules for adding and multiplying positive and negative quantities in 628, and Bhaskara II's Bijaganita of 1150 repeats them for algebra. The example applies the rules to four products and prints whether each result agrees with ordinary multiplication, so the sign rule is tested rather than assumed.",
        instructions = """
            1. Save the example as signs.py.
            2. Run it: python3 signs.py
        """,
        example = """
            def sign_product(p, q):
                # rule of signs: like signs give a positive product, unlike signs a negative one
                magnitude = abs(p) * abs(q)
                negative = (p < 0) != (q < 0)
                return -magnitude if negative else magnitude

            for p, q in [(-3, -4), (-3, 4), (3, -4), (3, 4)]:
                value = sign_product(p, q)
                print(f"({p}) x ({q}) = {value}, matches: {value == p * q}")
        """,
    ),

    entry(
        databaseId = 89,
        kind = Kind.GUIDE,
        section = "Clocks",
        title = "How the ropes of a pulley block share the load of a water clock",
        originalTitle = "Pulley block",
        author = "Makers of the Damascus water clock",
        year = 1154,
        summary = "A pulley block with several rope strands carries its load with each strand taking an equal share, and a short program prints the pull and the rope drawn for three blocks.",
        details = "The Damascus water clock was driven through pulleys and ropes, according to its 1203 description, so a block of this kind was part of its working. The example uses ideal blocks with no friction, and its loads, strand counts and lifts are illustrative.",
        instructions = """
            1. Save the example as pulleys.py.
            2. Run it: python3 pulleys.py
        """,
        example = """
            # (load, strands, lift): the strands share the load equally, and each strand is drawn for the lift
            cases = [(120, 4, 5), (90, 3, 2), (64, 8, 1)]
            for load, strands, lift in cases:
                pull = load // strands
                rope = strands * lift
                print(f"load {load} on {strands} strands: pull {pull} per strand, rope drawn {rope} for a lift of {lift}")
        """,
    ),

    entry(
        databaseId = 89,
        kind = Kind.GUIDE,
        section = "Maps",
        title = "How the seven climates and ten sections number the squares of a world map",
        originalTitle = "Tabula Rogeriana, climates and sections",
        author = "Muhammad al-Idrisi",
        year = 1154,
        summary = "Al-Idrisi divided the inhabited world into seven climates, each cut into ten sections, and a short program numbers the squares of that grid.",
        details = "The climates are the traditional bands of latitude, and seven bands of ten sections make seventy squares in all. The example prints the first square, the last square and the total, so the count can be checked by hand, and the numbering is the program's own.",
        instructions = """
            1. Save the example as climates.py.
            2. Run it: python3 climates.py
        """,
        example = """
            CLIMATES = 7     # the bands of latitude
            SECTIONS = 10    # sections cut from each band
            squares = [(c, s) for c in range(1, CLIMATES + 1) for s in range(1, SECTIONS + 1)]
            print("first square: climate", squares[0][0], "section", squares[0][1])
            print("last square: climate", squares[-1][0], "section", squares[-1][1])
            print("squares in the grid:", len(squares))
        """,
    ),

    entry(
        databaseId = 89,
        kind = Kind.GUIDE,
        section = "Geometry",
        title = "How Bhaskara's lotus problem gives the depth of a lake",
        originalTitle = "Lotus problem",
        author = "Bhaskara II",
        year = 1150,
        summary = "A lotus stalk stands half a cubit above the water and is pushed two cubits sideways, so a right triangle gives the depth of the lake, and a short program computes it exactly.",
        details = "The problem is one of the riddles in Bhaskara II's Lilavati, and its answer is a depth of three and three quarter cubits. The example uses exact fractions and checks that the depth fits the right triangle, so the result does not depend on rounding.",
        instructions = """
            1. Save the example as lotus.py.
            2. Run it: python3 lotus.py
        """,
        example = """
            from fractions import Fraction

            distance = Fraction(2)          # cubits the flower is pushed sideways
            stalk_above = Fraction(1, 2)    # cubits of stalk above the water
            depth = (distance ** 2 - stalk_above ** 2) / (2 * stalk_above)
            print("depth in cubits:", depth, "=", float(depth))
            check = depth ** 2 + distance ** 2 == (depth + stalk_above) ** 2
            print("the right triangle fits:", check)
        """,
    ),

)
