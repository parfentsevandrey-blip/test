package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1720 to 1730: powers of unit complex numbers, the factors of x to the sixth minus one, a card square, a gridiron's rods and a latitude from the noon sun. */
val db32Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 32,
        kind = Kind.GUIDE,
        section = "Complex numbers",
        title = "De Moivre's formula for powers of unit complex numbers",
        originalTitle = "De sectione anguli",
        author = "Abraham de Moivre",
        year = 1722,
        summary = "For a unit complex number written as cos t plus i sin t, its n-th power is cos nt plus i sin nt, and the program checks the powers up to four with exact fractions.",
        details = "De Moivre's rule for dividing an angle appears in his 1722 paper, and the complex form is often attributed to his 1730 book, so accounts differ on which year to give. The example takes the unit number with cosine 3/5 and sine 4/5, raises it to the powers 1 to 4, and checks the real part at power 3 against cos cubed minus three cos sin squared.",
        instructions = """
            1. Save the example as demoivre.py.
            2. Run it: python3 demoivre.py
        """,
        example = """
            from fractions import Fraction as F

            def mul(a, b):
                # Product of two complex numbers written as (real, imaginary) pairs of exact fractions.
                return (a[0] * b[0] - a[1] * b[1], a[0] * b[1] + a[1] * b[0])

            c, s = F(3, 5), F(4, 5)   # cosine and sine of one angle t, with c squared plus s squared equal to one
            z = (c, s)
            powers = [(F(1), F(0))]
            for n in range(1, 5):
                powers.append(mul(powers[-1], z))
            for n in range(1, 5):
                real, imag = powers[n]
                print(f"n = {n}: real {real}, imaginary {imag}")
            print("real part at n = 3 agrees with cos^3 - 3 cos sin^2:", powers[3][0] == c**3 - 3 * c * s**2)
        """,
    ),

    entry(
        databaseId = 32,
        kind = Kind.GUIDE,
        section = "Algebra",
        title = "Cotes's split of x^6 - 1 into real factors",
        originalTitle = "Harmonia Mensurarum",
        author = "Roger Cotes",
        year = 1722,
        summary = "The polynomial x^6 - 1 splits into real linear and quadratic factors whose coefficients come from cosines of multiples of sixty degrees, and the program multiplies them back to check.",
        details = "Cotes's method for splitting x^n minus one was printed in 1722 in the Harmonia Mensurarum, after his death in 1716, and it works from the points of the unit circle where the roots lie. The example writes the factors of x^6 minus one with integer coefficients, multiplies them together, and checks that the product is x^6 minus one.",
        instructions = """
            1. Save the example as cotes.py.
            2. Run it: python3 cotes.py
        """,
        example = """
            # Multiply two polynomials given as lists of integer coefficients, lowest power first.
            def mul(p, q):
                out = [0] * (len(p) + len(q) - 1)
                for i, a in enumerate(p):
                    for j, b in enumerate(q):
                        out[i + j] += a * b
                return out

            # Real factors of x^6 - 1: the cosine of 2 pi k / 6 is 1, 1/2, -1/2 and -1 for k = 0, 1, 2 and 3.
            factors = {
                "x - 1": [-1, 1],
                "x + 1": [1, 1],
                "x^2 - x + 1": [1, -1, 1],
                "x^2 + x + 1": [1, 1, 1],
            }
            product = [1]
            for poly in factors.values():
                product = mul(product, poly)
            print("product of the factors:", product)
            print("equals x^6 - 1:", product == [-1, 0, 0, 0, 0, 0, 1])
            for name, poly in factors.items():
                print(name, "->", poly)
        """,
    ),

    entry(
        databaseId = 32,
        kind = Kind.GUIDE,
        section = "Puzzles",
        title = "Ozanam's card square of four by four",
        originalTitle = "Recreations mathematiques et physiques, volume IV",
        author = "Jacques Ozanam",
        year = 1725,
        summary = "Sixteen playing cards, four suits and four ranks, are laid in a four by four square so that each row and column holds each suit and each rank once.",
        details = "Ozanam's puzzle appeared in the fourth volume of his Recreations in 1725, and it is an early case of what Euler later named a Graeco-Latin square. The example writes out the two Latin squares for the suits and the ranks, checks that every row holds each symbol once, and checks that all sixteen pairs of suit and rank are different.",
        instructions = """
            1. Save the example as ozanam_cards.py.
            2. Run it: python3 ozanam_cards.py
        """,
        example = """
            # Two Latin squares of order four: the suit of each card and the rank of each card.
            suits = [[0, 1, 2, 3], [1, 0, 3, 2], [2, 3, 0, 1], [3, 2, 1, 0]]
            ranks = [[0, 1, 2, 3], [2, 3, 0, 1], [3, 2, 1, 0], [1, 0, 3, 2]]
            names_suit = ["spades", "hearts", "diamonds", "clubs"]
            names_rank = ["ace", "king", "queen", "jack"]

            def is_latin(square):
                n = len(square)
                rows_ok = all(sorted(row) == list(range(n)) for row in square)
                cols_ok = all(sorted(square[i][j] for i in range(n)) == list(range(n)) for j in range(n))
                return rows_ok and cols_ok

            pairs = {(suits[i][j], ranks[i][j]) for i in range(4) for j in range(4)}
            print("both squares are Latin:", is_latin(suits) and is_latin(ranks))
            print("distinct cards placed:", len(pairs))
            print("first row:", [(names_suit[suits[0][j]], names_rank[ranks[0][j]]) for j in range(4)])
        """,
    ),

    entry(
        databaseId = 32,
        kind = Kind.GUIDE,
        section = "Pendulums",
        title = "How the gridiron rods cancel their expansion",
        originalTitle = "Gridiron pendulum",
        author = "John Harrison",
        year = 1726,
        summary = "In a simple model of the gridiron, the brass rods lengthen the pendulum as they grow and the steel rods shorten it, and the program finds the rod totals at which the two cancel.",
        details = "Harrison's gridiron, dated here to 1726, uses steel and brass in one frame so that their different expansions balance. The example uses illustrative expansion figures of 11 for steel and 19 for brass, in parts per million per degree, and works out the net change for three sets of rods and the brass needed to balance 19 units of steel.",
        instructions = """
            1. Save the example as gridiron.py.
            2. Run it: python3 gridiron.py
        """,
        example = """
            # Illustrative expansion figures, in parts per million per degree.
            STEEL = 11
            BRASS = 19

            def net_change(steel_length, brass_length):
                # Brass rods push the bob down as they grow; steel rods lift it. The two must cancel.
                return BRASS * brass_length - STEEL * steel_length

            for steel, brass in ((19, 11), (19, 12), (22, 11)):
                print(f"steel {steel}, brass {brass}: net change {net_change(steel, brass)}")
            brass_needed = STEEL * 19 // BRASS
            print("brass needed to balance 19 units of steel:", brass_needed)
        """,
    ),

    entry(
        databaseId = 32,
        kind = Kind.GUIDE,
        section = "Observation",
        title = "Reading latitude from the sun's noon altitude",
        originalTitle = "Noon altitude of the sun",
        author = "Observers at the Jantar Mantar",
        year = 1724,
        summary = "The sun's altitude at noon, together with its declination, gives the latitude, and the program turns the formula both ways and checks the answers.",
        details = "The rule that noon altitude equals ninety degrees minus the distance between latitude and declination was standard observatory work in the 1720s. Each noon altitude fits two latitudes, one on each side of the declination, and the example finds both and checks one of them.",
        instructions = """
            1. Save the example as latitude.py.
            2. Run it: python3 latitude.py
        """,
        example = """
            # Angles in degrees: the sun's zenith distance at noon is the difference of latitude and declination.
            def noon_altitude(lat, dec):
                return 90 - abs(lat - dec)

            # Two latitudes give the same noon altitude: the declination plus or minus the zenith distance.
            def latitudes_for(altitude, dec):
                z = 90 - altitude
                return dec + z, dec - z

            print("noon altitude at latitude 28 with declination 10:", noon_altitude(28, 10))
            print("noon altitude at latitude 28 with declination -10:", noon_altitude(28, -10))
            lat1, lat2 = latitudes_for(72, 10)
            print("latitudes that fit altitude 72 with declination 10:", (lat1, lat2))
            print(f"check at latitude {lat2}: {noon_altitude(lat2, 10)}")
        """,
    ),

)
