package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1800 to 1810: polygons, cards, casting out nines, a barrel of pins, a derivative and gas volumes. */
val db24Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 24,
        kind = Kind.EXAMPLE,
        section = "Triangle geometry",
        title = "Carnot's sum of distances from the circumcentre",
        originalTitle = "Geometrie de position (1803)",
        author = "Lazare Carnot",
        year = 1803,
        summary = "Carnot's theorem says the signed distances from the circumcentre to the three sides of a triangle add up to the circumradius plus the inradius, and the program checks it in exact fractions.",
        details = "Carnot's theorem is stated in his Geometrie de position of 1803. The example takes the right triangle with corners (0, 0), (4, 0) and (0, 3), where the circumcentre lies on the hypotenuse, finds the circumcentre, and checks the sum of signed distances against R plus r.",
        instructions = """
            1. Save the example as carnot.py.
            2. Run it: python3 carnot.py
        """,
        example = """
            from fractions import Fraction as F
            from math import isqrt

            # Corners of a right triangle with legs 4 and 3; the hypotenuse runs from (4, 0) to (0, 3).
            A, B, C = (F(0), F(0)), (F(4), F(0)), (F(0), F(3))

            def sq(p):
                return p[0] * p[0] + p[1] * p[1]

            def dist2(p, q):
                return (p[0] - q[0]) ** 2 + (p[1] - q[1]) ** 2

            # The circumcentre O satisfies 2(B - A).O = |B|^2 - |A|^2 and 2(C - A).O = |C|^2 - |A|^2.
            a1, b1, c1 = 2 * (B[0] - A[0]), 2 * (B[1] - A[1]), sq(B) - sq(A)
            a2, b2, c2 = 2 * (C[0] - A[0]), 2 * (C[1] - A[1]), sq(C) - sq(A)
            det = a1 * b2 - a2 * b1
            O = ((c1 * b2 - c2 * b1) / det, (a1 * c2 - a2 * c1) / det)
            print(f"circumcentre: ({O[0]}, {O[1]})")

            # Each side as a x + b y + c = 0; the centroid lies inside, so it fixes the positive side.
            G = ((A[0] + B[0] + C[0]) / 3, (A[1] + B[1] + C[1]) / 3)
            sides = [(3, 4, -12), (1, 0, 0), (0, 1, 0)]
            signed = []
            for a, b, c in sides:
                vO = a * O[0] + b * O[1] + c
                vG = a * G[0] + b * G[1] + c
                d = F(abs(vO)) / isqrt(a * a + b * b)
                signed.append(d if vO * vG >= 0 else -d)
            print("signed distances: " + ", ".join(str(d) for d in signed))

            total = sum(signed)
            area2 = abs((B[0] - A[0]) * (C[1] - A[1]) - (B[1] - A[1]) * (C[0] - A[0]))
            a_len = isqrt(int(dist2(B, C)))
            b_len = isqrt(int(dist2(C, A)))
            c_len = isqrt(int(dist2(A, B)))
            K = F(area2, 2)
            R = F(a_len * b_len * c_len) / (4 * K)
            r = 2 * K / (a_len + b_len + c_len)
            print("sum of signed distances:", total)
            print("R + r:", R + r)
            print("Carnot's sum equals R + r:", total == R + r)
        """,
    ),

    entry(
        databaseId = 24,
        kind = Kind.EXAMPLE,
        section = "Punched cards",
        title = "A Jacquard card read as a row of holes",
        originalTitle = "Patterning by punched cards on Jacquard's improved loom (1801)",
        author = "Joseph Marie Jacquard",
        year = 1801,
        summary = "Each Jacquard card holds one row of the weave as holes, so the program reads each card as a binary number and counts the hooks it raises.",
        details = "Jacquard's loom took one card per row of the pattern, with a hole where a hook was to be lifted, so a card is a row of binary digits. The example reads four cards of eight positions each, prints each card's value and hook count, and sums the hooks for the whole pattern.",
        instructions = """
            1. Save the example as jacquard_card.py.
            2. Run it: python3 jacquard_card.py
        """,
        example = """
            # Each card is one row of the weave: a 1 is a hole that lifts a hook, a 0 is a blank.
            PATTERN = [
                "11110000",
                "01100110",
                "00011000",
                "10101010",
            ]
            for number, row in enumerate(PATTERN, start=1):
                holes = int(row, 2)
                print(f"card {number}: {row} value {holes}, raised hooks {row.count('1')}")
            print("total raised hooks:", sum(row.count("1") for row in PATTERN))
        """,
    ),

    entry(
        databaseId = 24,
        kind = Kind.EXAMPLE,
        section = "Congruence checks",
        title = "Casting out nines checks products by congruence mod 9",
        originalTitle = "Disquisitiones Arithmeticae, congruences with a modulus (1801)",
        author = "Traditional arithmetic rule, in congruence form by Carl Friedrich Gauss",
        year = 1801,
        summary = "A number and its digit sum leave the same remainder mod 9, so a product can be checked by multiplying the remainders of its factors and comparing.",
        details = "The rule of casting out nines is much older than Gauss, but the Disquisitiones of 1801 writes such checks as congruences with a modulus. The example checks four correct products and one typing error, which the check catches.",
        instructions = """
            1. Save the example as casting_nines.py.
            2. Run it: python3 casting_nines.py
        """,
        example = """
            def digit_sum(n):
                return sum(int(d) for d in str(n))

            def nine_residue(n):
                return digit_sum(n) % 9

            checks = [(123, 456, 56088), (9876, 543, 5362668), (47, 58, 2726), (1234, 56, 69104), (47, 58, 2716)]
            for a, b, claimed in checks:
                lhs = nine_residue(claimed)
                rhs = (nine_residue(a) * nine_residue(b)) % 9
                verdict = "passes" if lhs == rhs else "fails"
                print(f"{a} x {b} = {claimed}: residue {lhs}, from factors {rhs}, check {verdict}")
        """,
    ),

    entry(
        databaseId = 24,
        kind = Kind.EXAMPLE,
        section = "Mechanical music",
        title = "A melody stored as pins on a barrel",
        originalTitle = "Pinned barrel of Maelzel's Panharmonicon (1805)",
        author = "Johann Nepomuk Maelzel",
        year = 1805,
        summary = "A barrel instrument stores a tune as pins, one row for each step of the barrel, and the program turns a short melody into such a pin pattern.",
        details = "Barrel instruments of this kind, such as Maelzel's Panharmonicon of 1805, stored a tune as pins on a rotating cylinder, so the same pins played the same notes every time. The example turns a seven-note tune into one row of pins per step and counts the pins.",
        instructions = """
            1. Save the example as barrel_pins.py.
            2. Run it: python3 barrel_pins.py
        """,
        example = """
            # A barrel has one pin position for each tone of the scale; each step is one turn of the barrel.
            TONES = ["C", "D", "E", "F", "G", "A", "B", "C2"]
            MELODY = [0, 2, 4, 2, 0, 4, 7]   # indices into TONES

            barrel = []
            for step, tone in enumerate(MELODY, start=1):
                pins = ["." for _ in TONES]
                pins[tone] = "X"
                barrel.append("".join(pins))
                print(f"step {step} plays {TONES[tone]}: {barrel[-1]}")
            print("pins on the barrel:", sum(row.count("X") for row in barrel))
            print("tones used:", " ".join(sorted({TONES[t] for t in MELODY}, key=TONES.index)))
        """,
    ),

    entry(
        databaseId = 24,
        kind = Kind.EXAMPLE,
        section = "Calculus notation",
        title = "Differentiating a polynomial in Leibniz's d/dx form",
        originalTitle = "Principles of Analytical Calculation, in the Leibniz notation (1803)",
        author = "Robert Woodhouse",
        year = 1803,
        summary = "Leibniz's notation writes the derivative as d over dx, and the program applies the power rule term by term and prints each derivative.",
        details = "Woodhouse's 1803 case for Leibniz's symbols is the background, since the d and dx notation makes repeated differentiation easy to write. The example differentiates 2x cubed minus 5x plus 7 twice, applying the power rule to each term.",
        instructions = """
            1. Save the example as leibniz_derivative.py.
            2. Run it: python3 leibniz_derivative.py
        """,
        example = """
            # A polynomial is a list of (power, coefficient) pairs; d/dx of c x^n is n c x^(n-1).
            def derivative(terms):
                return [(n - 1, c * n) for n, c in terms if n > 0]

            def show(terms):
                parts = []
                for n, c in sorted(terms, reverse=True):
                    if c == 0:
                        continue
                    if n == 0:
                        parts.append(str(c))
                    elif n == 1:
                        parts.append(f"{c}x")
                    else:
                        parts.append(f"{c}x^{n}")
                return " + ".join(parts).replace("+ -", "- ")

            f = [(3, 2), (1, -5), (0, 7)]    # 2x^3 - 5x + 7
            print("f(x) =", show(f))
            print("d/dx f =", show(derivative(f)))
            print("d2/dx2 f =", show(derivative(derivative(f))))
        """,
    ),

    entry(
        databaseId = 24,
        kind = Kind.EXAMPLE,
        section = "Triangulation practice",
        title = "Distance across a river from a base line and two angles",
        originalTitle = "Triangulation by a base line and two angles, survey practice (1802)",
        author = "Survey practice of the period",
        year = 1802,
        summary = "A base line and two measured angles fix the distances to a point across a river, and the law of sines gives both in exact fractions.",
        details = "Surveyors of the Great Trigonometrical Survey measured base lines and angles in just this way, and the law of sines carried their triangles forward. The example uses a base line of 10 units, with angles whose sines are 3/5 and 4/5, so the two distances come out in whole units.",
        instructions = """
            1. Save the example as river_distance.py.
            2. Run it: python3 river_distance.py
        """,
        example = """
            from fractions import Fraction as F

            # Base line AB of 10 units; the angles at A and B have sines 3/5 and 4/5, so the angle at C is a right angle.
            base = F(10)
            sin_A, sin_B = F(3, 5), F(4, 5)
            sin_C = 1
            # Law of sines: a / sin A = b / sin B = c / sin C, with a opposite A, b opposite B and c = AB opposite C.
            a = base * sin_A / sin_C   # distance from B to the point C across the river
            b = base * sin_B / sin_C   # distance from A to C
            print("distance from A to C:", b)
            print("distance from B to C:", a)
            print("check, a squared + b squared equals base squared:", a * a + b * b == base * base)
        """,
    ),
)
