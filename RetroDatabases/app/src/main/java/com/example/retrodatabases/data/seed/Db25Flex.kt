package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1790 to 1800: repeated derivatives, a fold to true length and the resonances of a speaking tube. */
val db25Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 25,
        kind = Kind.FLEX,
        section = "Derivation calculus",
        title = "Repeated derivatives of polynomials",
        originalTitle = "Du calcul des derivations (1800), applied to polynomials",
        author = "Louis Francois Antoine Arbogast",
        year = 1800,
        summary = "Applying the derivation D k times to x to the n gives n times (n minus 1) and so on down to (n minus k plus 1), times x to the power n minus k.",
        details = "Arbogast's rules make repeated derivation a product of falling factors. The example applies D several times to small polynomials and prints each result in the usual form.",
        instructions = """
            1. Save the example as derivations.py.
            2. Run it: python3 derivations.py
        """,
        example = """
            def derive(poly):
                # A polynomial is a dict from exponent to coefficient; D sends x to the n to n times x to the n minus 1.
                out = {}
                for e, c in poly.items():
                    if e > 0:
                        out[e - 1] = out.get(e - 1, 0) + c * e
                return out

            def derive_times(poly, k):
                for _ in range(k):
                    poly = derive(poly)
                return poly

            def text(poly):
                parts = []
                for e in sorted(poly, reverse=True):
                    c = poly[e]
                    if c == 0:
                        continue
                    if e == 0:
                        parts.append(str(c))
                    elif e == 1:
                        parts.append(f"{c}x")
                    else:
                        parts.append(f"{c}x^{e}")
                return " + ".join(parts) if parts else "0"

            print("D^2 x^4 = " + text(derive_times({4: 1}, 2)))
            print("D^4 x^4 = " + text(derive_times({4: 1}, 4)))
            print("D^5 x^4 = " + text(derive_times({4: 1}, 5)))
            print("D^2 (x^3 + x^2) = " + text(derive_times({3: 1, 2: 1}, 2)))
        """,
    ),

    entry(
        databaseId = 25,
        kind = Kind.FLEX,
        section = "Descriptive geometry",
        title = "Monge's fold to a true length",
        originalTitle = "Geometrie descriptive (1799), folding the vertical plane",
        author = "Gaspard Monge",
        year = 1799,
        summary = "Folding the vertical plane of a segment onto the horizontal one turns the segment into a right triangle whose legs give its true length.",
        details = "The two legs are the length of the horizontal projection and the difference of heights, so the hypotenuse is the true length of the segment. The example computes both legs from two points with whole coordinates and checks that the folded length is exact.",
        instructions = """
            1. Save the example as fold.py.
            2. Run it: python3 fold.py
        """,
        example = """
            from math import isqrt

            A = (1, 2, 3)
            B = (4, 6, 15)
            dx, dy, dz = B[0] - A[0], B[1] - A[1], B[2] - A[2]
            horizontal_sq = dx * dx + dy * dy
            print("horizontal projection squared: " + str(horizontal_sq))
            # After the fold, the horizontal projection and the height difference are the legs of a right triangle.
            true_sq = horizontal_sq + dz * dz
            length = isqrt(true_sq)
            print(f"true length: {length} exact: {length * length == true_sq}")
        """,
    ),

    entry(
        databaseId = 25,
        kind = Kind.FLEX,
        section = "Speech",
        title = "Resonances of a speaking tube",
        originalTitle = "Mechanismus der menschlichen Sprache (1791), the vocal tube",
        author = "Wolfgang von Kempelen",
        year = 1791,
        summary = "A tube closed at one end has resonances at odd multiples of the speed of sound divided by four times its length, which a vocal tract model can use.",
        details = "Kempelen built his machine around a tube that stood for the vocal tract, and the lowest resonance of a tube closed at one end is a quarter of a wavelength. The example computes the first three resonances of a 0.17 metre tube with exact fractions, taking 340 metres per second for the speed of sound.",
        instructions = """
            1. Save the example as tube.py.
            2. Run it: python3 tube.py
        """,
        example = """
            from fractions import Fraction

            SPEED = Fraction(340)                 # metres per second, rounded
            LENGTH = Fraction(17, 100)            # tube length in metres
            base = SPEED / (4 * LENGTH)           # lowest resonance of a tube closed at one end
            for n in range(1, 4):
                print(f"f{n} = {(2 * n - 1) * base} Hz")
        """,
    ),
)
