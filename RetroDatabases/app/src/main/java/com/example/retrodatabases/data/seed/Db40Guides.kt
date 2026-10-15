package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1640 to 1650: Fermat's test, Torricelli's efflux, Fermat's factoring by squares, Guldin's ring volume and Pascal's complement subtraction. */
val db40Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 40,
        kind = Kind.GUIDE,
        section = "Number theory",
        title = "How Fermat's little theorem tests a number for primality",
        originalTitle = "Lettre a Frenicle de Bessy",
        author = "Pierre de Fermat",
        year = 1640,
        summary = "If a number n is prime, then a to the power n minus 1 leaves remainder 1 on division by n, so a failure of this test proves that n is composite.",
        details = "Fermat stated the rule in a letter to Frenicle de Bessy dated 18 October 1640, without a proof, and Euler later gave the first published proof. Some composite numbers pass the test for some bases, and the example shows 341 and 561 passing for base 2 while 91 fails.",
        instructions = """
            1. Save the example as fermat_test.py.
            2. Run it: python3 fermat_test.py
        """,
        example = """
            def passes(n, base):
                # Fermat's test: the base raised to n - 1 must leave remainder 1 when n is prime.
                return pow(base, n - 1, n) == 1

            for n in (97, 91, 341, 561):
                verdict = "passes" if passes(n, 2) else "fails"
                print(f"n = {n}: base 2 test {verdict}")
        """,
    ),

    entry(
        databaseId = 40,
        kind = Kind.GUIDE,
        section = "Hydraulics",
        title = "How Torricelli's theorem gives the speed of water from a hole",
        originalTitle = "Opera geometrica",
        author = "Evangelista Torricelli",
        year = 1643,
        summary = "Water leaves a small hole in a tank at the speed a body gains by falling through the height of the water above the hole, which is the square root of 2gh.",
        details = "Torricelli worked out the result in the early 1640s and printed it in his Opera geometrica of 1644. The example uses g equal to 9.81 metres per second squared and prints the speed for three heights, rounded to two decimal places.",
        instructions = """
            1. Save the example as efflux.py.
            2. Run it: python3 efflux.py
        """,
        example = """
            from math import sqrt

            G = 9.81  # metres per second squared

            def efflux_speed(height):
                # The jet leaves at the speed gained by falling through the height of water above the hole.
                return sqrt(2 * G * height)

            for h in (1.0, 4.0, 0.25):
                print(f"height {h:.2f} m: speed {efflux_speed(h):.2f} m/s")
        """,
    ),

    entry(
        databaseId = 40,
        kind = Kind.GUIDE,
        section = "Number theory",
        title = "How Fermat's factoring method splits a number as a difference of squares",
        originalTitle = "Fermat's method in his correspondence",
        author = "Pierre de Fermat",
        year = 1643,
        summary = "A number that is a square minus a square factors as the sum times the difference of the two roots, so searching for a square just above the number finds its factors.",
        details = "Fermat set out the method in letters to Mersenne in 1643, and it works best when the two factors lie close to the square root of the number. The letters circulated in manuscript and were printed only much later, so the year names the letters, and the example counts the tries for two numbers.",
        instructions = """
            1. Save the example as fermat_factor.py.
            2. Run it: python3 fermat_factor.py
        """,
        example = """
            from math import isqrt

            def fermat_factor(n):
                # Find a with a * a - n = b * b; then n = (a - b) * (a + b).
                a = isqrt(n)
                if a * a < n:
                    a += 1
                tries = 1
                while True:
                    b2 = a * a - n
                    b = isqrt(b2)
                    if b * b == b2:
                        return a - b, a + b, a, b, tries
                    a += 1
                    tries += 1

            for n in (5959, 10403):
                f, g, a, b, tries = fermat_factor(n)
                print(f"{n} = {f} * {g} (a = {a}, b = {b}, tries {tries})")
        """,
    ),

    entry(
        databaseId = 40,
        kind = Kind.GUIDE,
        section = "Solids",
        title = "How Guldin's rule gives the volume of a ring-shaped solid",
        originalTitle = "Centrobaryca, seu de centro gravitatis",
        author = "Paul Guldin",
        year = 1640,
        summary = "The volume of a solid made by turning a plane figure about an axis equals the figure's area times the distance its centroid travels.",
        details = "Guldin published the rule in his Centrobaryca, printed at Vienna in the 1640s, although the same result goes back to Pappus in antiquity, so accounts differ on the credit. The example applies the rule to a ring with major radius 3 and minor radius 1, and the answer, 6 times pi squared, is printed to four decimal places.",
        instructions = """
            1. Save the example as guldin_ring.py.
            2. Run it: python3 guldin_ring.py
        """,
        example = """
            from math import pi

            def ring_volume(major, minor):
                # Guldin's rule: the circle's centroid travels 2 pi times the major radius, and the circle's area is pi times minor squared.
                return (2 * pi * major) * (pi * minor ** 2)

            print(f"ring with major radius 3 and minor radius 1: volume {ring_volume(3, 1):.4f}")
            print(f"check, 6 times pi squared: {6 * pi ** 2:.4f}")
        """,
    ),

    entry(
        databaseId = 40,
        kind = Kind.GUIDE,
        section = "Calculating machines",
        title = "How the nines complement lets a wheel machine subtract",
        originalTitle = "Complement method of the Pascaline",
        author = "Blaise Pascal",
        year = 1645,
        summary = "A wheel machine that can only add subtracts by adding the nines complement of the number, and the carry out of the top wheel shows whether the difference is negative.",
        details = "Pascal's machine handled subtraction in this way, so that one addition mechanism served both operations. The example subtracts three pairs of six-digit numbers, and it prints the wheel reading and the carry for each pair.",
        instructions = """
            1. Save the example as nines_complement.py.
            2. Run it: python3 nines_complement.py
        """,
        example = """
            def subtract(a, b, digits=6):
                # The machine adds the nines complement of b and one; the carry out of the top wheel marks a result that is not negative.
                m = 10 ** digits
                nines = (m - 1) - b
                total = a + nines + 1
                return total % m, total // m

            for a, b in ((123456, 98765), (500000, 1), (98765, 123456)):
                result, carry = subtract(a, b)
                sign = "not negative" if carry else "negative"
                print(f"{a} - {b}: wheels {result:06d}, carry {carry}, difference {sign}")
        """,
    ),

)
