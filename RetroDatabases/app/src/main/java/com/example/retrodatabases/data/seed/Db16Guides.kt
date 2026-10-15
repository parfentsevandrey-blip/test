package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1880 to 1890: logic diagrams, an interferometer prediction, unit fractions, co-relation and a map-colouring count, each with a program that runs. */
val db16Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 16,
        kind = Kind.GUIDE,
        section = "Logic diagrams",
        title = "How Venn diagrams check a syllogism",
        originalTitle = "On the Diagrammatic and Mechanical Representation of Propositions and Reasonings",
        author = "John Venn",
        year = 1880,
        summary = "Each premise shades part of a three-circle diagram, and the conclusion holds when every region it would shade is already empty.",
        details = "Venn's 1880 paper gave a diagram of three overlapping circles and a rule for shading the regions that a universal premise rules out. The example runs three syllogisms through that rule, using the eight regions as triples of inside and outside. The program prints how many regions survive the premises and whether the conclusion holds.",
        instructions = """
            1. Save the example as venn.py.
            2. Run it: python3 venn.py
        """,
        example = """
            # Three circles S, M and P. A region is a triple (s, m, p): 1 inside the circle, 0 outside.
            regions = [(s, m, p) for s in (0, 1) for m in (0, 1) for p in (0, 1)]
            POS = {"S": 0, "M": 1, "P": 2}

            def shaded(kind, a, b):
                # Venn shading: "All A are B" empties the part of A outside B; "No A are B" empties the part of A inside B.
                if kind == "A":
                    return {r for r in regions if r[POS[a]] == 1 and r[POS[b]] == 0}
                return {r for r in regions if r[POS[a]] == 1 and r[POS[b]] == 1}

            def test(name, premises, conclusion):
                left = set(regions)
                for kind, a, b in premises:
                    left -= shaded(kind, a, b)
                kind, a, b = conclusion
                # The conclusion holds when every region it would shade is already empty.
                ok = all(r not in shaded(kind, a, b) for r in left)
                print(f"{name}: {len(left)} regions left, {'valid' if ok else 'invalid'}")

            test("Barbara", [("A", "M", "P"), ("A", "S", "M")], ("A", "S", "P"))
            test("Celarent", [("E", "M", "P"), ("A", "S", "M")], ("E", "S", "P"))
            test("AAA-2", [("A", "P", "M"), ("A", "S", "M")], ("A", "S", "P"))
        """,
    ),

    entry(
        databaseId = 16,
        kind = Kind.GUIDE,
        section = "Interferometry",
        title = "How the Michelson-Morley fringe shift is predicted",
        originalTitle = "On the Relative Motion of the Earth and the Luminiferous Ether",
        author = "Albert Abraham Michelson and Edward Williams Morley",
        year = 1887,
        summary = "Turning the apparatus should shift the fringes by a predicted fraction, and the program computes that fraction from the Earth's speed.",
        details = "Michelson and Morley's 1887 experiment expected a fringe shift from the Earth's motion through the ether, and their paper reported a shift far smaller than the expected one. The example computes the expected shift with exact fractions for an arm of 11 metres, the Earth's speed of 30 km/s and light of 550 nanometres. The result is two fifths of a fringe.",
        instructions = """
            1. Save the example as fringe.py.
            2. Run it: python3 fringe.py
        """,
        example = """
            from fractions import Fraction

            # Speeds and lengths in SI units, kept exact as fractions.
            c = Fraction(300000000)             # speed of light, metres per second
            v = Fraction(30000)                 # the Earth's orbital speed, about 30 km/s
            arm = Fraction(11)                  # effective length of one arm, metres
            wavelength = Fraction(550, 10**9)   # 550 nanometres

            # Expected fringe shift when the apparatus is turned: 2 L (v/c)^2 / wavelength.
            shift = 2 * arm * (v / c) ** 2 / wavelength
            print(f"expected shift at 30 km/s: {shift} fringe")
            half = shift / 4
            print(f"expected shift at 15 km/s: {half} fringe")
            print(f"as a decimal: {float(shift):.1f}")
        """,
    ),

    entry(
        databaseId = 16,
        kind = Kind.GUIDE,
        section = "Map colouring",
        title = "How Tait's edge colouring of a dual graph is counted",
        originalTitle = "Remarks on the colouring of maps",
        author = "Peter Guthrie Tait",
        year = 1880,
        summary = "Tait's remarks turned four-colouring a map into three-colouring the edges of its dual cubic graph, and the program counts those colourings for the tetrahedral map.",
        details = "Tait's 1880 remarks reduced the four colour question to proper three-edge-colourings of bridgeless cubic planar graphs. The dual of the tetrahedral map is the complete graph on four vertices, which is cubic. The example counts its edges, its perfect matchings and its proper three-edge-colourings, and prints 6, 3 and 6.",
        instructions = """
            1. Save the example as tait.py.
            2. Run it: python3 tait.py
        """,
        example = """
            from itertools import combinations, product

            # K4, the complete graph on four vertices, is the dual of the tetrahedral map and is cubic.
            vertices = range(4)
            edges = list(combinations(vertices, 2))
            print(f"edges of K4: {len(edges)}")

            # A perfect matching is a set of two edges that together touch all four vertices.
            matchings = [m for m in combinations(edges, 2) if len({v for e in m for v in e}) == 4]
            print(f"perfect matchings of K4: {len(matchings)}")

            # Tait's 1880 remark: a proper three-edge-colouring gives different colours to the three edges at every vertex.
            count = 0
            for colours in product(range(3), repeat=len(edges)):
                ok = True
                for v in vertices:
                    seen = [c for e, c in zip(edges, colours) if v in e]
                    if len(set(seen)) != 3:
                        ok = False
                        break
                if ok:
                    count += 1
            print(f"proper 3-edge-colourings of K4: {count}")
        """,
    ),

    entry(
        databaseId = 16,
        kind = Kind.GUIDE,
        section = "Unit fractions",
        title = "How the greedy rule writes a fraction in unit fractions",
        originalTitle = "On a point in the theory of vulgar fractions",
        author = "James Joseph Sylvester",
        year = 1880,
        summary = "Each step takes the unit fraction with the smallest denominator that does not overshoot, so any positive fraction splits into distinct unit fractions.",
        details = "Sylvester's note of 1880 in the American Journal of Mathematics described the greedy method, which Fibonacci had already used in his Liber abaci of 1202. The example writes three fractions as sums of unit fractions with exact arithmetic. Each step is a ceiling division, and the process always ends; the 1880 date rests on a single secondary bibliography.",
        instructions = """
            1. Save the example as greedy.py.
            2. Run it: python3 greedy.py
        """,
        example = """
            from fractions import Fraction

            def greedy_unit_fractions(x):
                # Each step takes the unit fraction 1/d with the smallest d that does not overshoot x.
                parts = []
                while x > 0:
                    d = -(-x.denominator // x.numerator)   # ceiling of denominator / numerator
                    parts.append(Fraction(1, d))
                    x -= Fraction(1, d)
                return parts

            for x in (Fraction(2, 3), Fraction(5, 6), Fraction(7, 15)):
                parts = greedy_unit_fractions(x)
                print(f"{x} = " + " + ".join(str(p) for p in parts))
        """,
    ),

    entry(
        databaseId = 16,
        kind = Kind.GUIDE,
        section = "Correlation",
        title = "How co-relation is computed from paired deviations",
        originalTitle = "Co-relations and their measurement, chiefly from anthropometric data",
        author = "Francis Galton",
        year = 1888,
        summary = "Two measurements are co-related when the deviations of one follow the deviations of the other, and the coefficient gives the strength of that link as a number from 0 to 1.",
        details = "Galton's 1888 paper in the Proceedings of the Royal Society introduced co-relation and an index for it, drawing on anthropometric measurements. The paper was read in December 1888, and sources differ on the exact day. The example uses five invented pairs, not Galton's data, and computes the deviation sums and the coefficient with exact fractions.",
        instructions = """
            1. Save the example as corelation.py.
            2. Run it: python3 corelation.py
        """,
        example = """
            from fractions import Fraction

            # Five invented pairs, not Galton's data: x is one measurement and y is the other.
            x = [1, 2, 3, 4, 5]
            y = [2, 4, 5, 4, 5]
            n = len(x)
            mean_x = Fraction(sum(x), n)
            mean_y = Fraction(sum(y), n)
            dx = [Fraction(a) - mean_x for a in x]
            dy = [Fraction(b) - mean_y for b in y]
            sxy = sum(a * b for a, b in zip(dx, dy))
            sxx = sum(a * a for a in dx)
            syy = sum(b * b for b in dy)
            print("sum of products of deviations:", sxy)
            print("sum of squares for x:", sxx)
            print("sum of squares for y:", syy)
            r2 = sxy * sxy / (sxx * syy)
            print("co-relation squared:", r2)
            print("co-relation:", round(float(r2) ** 0.5, 4))
        """,
    ),
)
