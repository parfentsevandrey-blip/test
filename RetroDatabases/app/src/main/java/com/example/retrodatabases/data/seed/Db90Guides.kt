package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1140 to 1150: Bhaskara's cyclic method, his zigzag multiplication, and the ratios of a drone string. */
val db90Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 90,
        kind = Kind.GUIDE,
        section = "Algebra",
        title = "How Bhaskara's cyclic method finds a solution of x squared minus 2 y squared equals 1",
        originalTitle = "Chakravala",
        author = "Bhaskara II",
        year = 1150,
        summary = "The chakravala, or cyclic method, climbs from a starting pair to a whole-number solution of x squared minus 2 y squared equals 1, and a short program runs one step of it.",
        details = "Bhaskara II described the cyclic method in his algebra of 1150, and his famous challenge with 61 needs a longer cycle than this short program follows. The example uses 2, where the cycle closes at once with x equal to 3 and y equal to 2, and the check by squaring can be done by hand.",
        instructions = """
            1. Save the example as chakravala.py.
            2. Run it: python3 chakravala.py
        """,
        example = """
            from math import isqrt

            def chakravala(n):
                # cyclic method for x*x - n*y*y equal to 1, for n that is not a square
                a = isqrt(n) + 1 if isqrt(n) ** 2 != n else isqrt(n)
                b, k = 1, a * a - n
                while k != 1:
                    best = None
                    for m in range(-abs(k), abs(k) + 1):
                        if (a + b * m) % k == 0 and (m * m - n) % k == 0:
                            if best is None or (abs(m * m - n), -m) < (abs(best * best - n), -best):
                                best = m
                    m = best
                    a, b, k = (a * m + n * b) // abs(k), (a + b * m) // abs(k), (m * m - n) // k
                return a, b

            x, y = chakravala(2)
            print("x =", x, "y =", y)
            print("check x*x - 2*y*y =", x * x - 2 * y * y)
        """,
    ),

    entry(
        databaseId = 90,
        kind = Kind.GUIDE,
        section = "Arithmetic",
        title = "How the zigzag gomutrika rule multiplies two numbers by their digits",
        originalTitle = "Gomutrika",
        author = "Bhaskara II",
        year = 1150,
        summary = "The gomutrika, a zigzag rule of Indian arithmetic, multiplies two two-digit numbers by summing the cross products of their digits, and a short program applies it.",
        details = "Bhaskara II describes the zigzag method in his arithmetic of 1150, where the cross products of the digits are added along a diagonal and then carried. The example checks two products by the rule and prints each one, so the carries can be followed by hand.",
        instructions = """
            1. Save the example as gomutrika.py.
            2. Run it: python3 gomutrika.py
        """,
        example = """
            def gomutrika(a, b):
                # zigzag rule for two two-digit numbers: units, then the cross sum, then the leading products
                a1, a0 = divmod(a, 10)
                b1, b0 = divmod(b, 10)
                carry, low = divmod(a0 * b0, 10)
                carry, mid = divmod(a1 * b0 + a0 * b1 + carry, 10)
                top = a1 * b1 + carry
                return top * 100 + mid * 10 + low

            for a, b in [(12, 34), (23, 45)]:
                print(a, "x", b, "=", gomutrika(a, b))
        """,
    ),

    entry(
        databaseId = 90,
        kind = Kind.GUIDE,
        section = "Music",
        title = "How the stopped points of a drone string give an octave, a fifth and a fourth",
        originalTitle = "Organistrum keys",
        author = "Makers and players of the organistrum",
        year = 1140,
        summary = "A stopped drone string sounds an octave, a fifth or a fourth above its open note when it is cut at half, two thirds or three quarters of its length, and a short program finds those points.",
        details = "The organistrum of the Codex Calixtinus era used keys that pressed the strings at fixed points, and this example shows only the arithmetic of such points on an illustrative 60 centimetre string. Surviving sources of the 1140s give no tuning table for the instrument, so the lengths are chosen for the demonstration.",
        instructions = """
            1. Save the example as drone.py.
            2. Run it: python3 drone.py
        """,
        example = """
            STRING = 60   # illustrative length of the drone string, in centimetres
            for name, (top, bottom) in [("octave", (1, 2)), ("fifth", (2, 3)), ("fourth", (3, 4))]:
                print(name, "at", STRING * top // bottom, "cm")
        """,
    ),

)
