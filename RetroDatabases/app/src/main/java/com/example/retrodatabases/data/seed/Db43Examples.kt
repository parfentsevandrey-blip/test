package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1610 to 1620: Kepler's packing density, van Ceulen's polygons, the parallax of a comet, the moons of Jupiter, guilders at the Amsterdam exchange and a word count on the 1611 translation. */
val db43Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 43,
        kind = Kind.EXAMPLE,
        section = "Geometry",
        title = "Kepler's packing density for cubic and close stacks",
        originalTitle = "Strena seu de nive sexangula",
        author = "Johannes Kepler",
        year = 1611,
        summary = "Spheres stacked in the close arrangement fill a larger share of space than spheres in the cubic arrangement, and the example computes both shares.",
        details = "Kepler printed the remark in his Strena of 1611 at Frankfurt, where he wrote about the six-cornered snowflake and proposed the close stacking of spheres as the densest. The example computes the two shares of space, and the close arrangement's share is found from the square root of 18.",
        instructions = """
            1. Save the example as sphere_density.py.
            2. Run it: python3 sphere_density.py
        """,
        example = """
            from math import pi, sqrt

            cubic = pi / 6                 # share of space filled by spheres in the cubic arrangement
            close = pi / sqrt(18)          # share of space filled by spheres in the close arrangement

            print(f"cubic arrangement: {cubic:.4f}")
            print(f"close arrangement: {close:.4f}")
            print("the close arrangement is denser:", close > cubic)
        """,
    ),

    entry(
        databaseId = 43,
        kind = Kind.EXAMPLE,
        section = "Geometry",
        title = "Van Ceulen's polygon doubling toward pi",
        originalTitle = "De arithmetische en geometrische fondamenten",
        author = "Ludolph van Ceulen",
        year = 1615,
        summary = "Van Ceulen found the digits of pi by doubling the sides of polygons inside a circle, and the example repeats the doubling ten times from a hexagon.",
        details = "Van Ceulen's method of 1615 doubled the sides of an inscribed polygon with the half-angle rule, and he carried it to 35 places, figures that are also said to be cut on his tombstone at Leiden. The example starts from a hexagon with sides equal to the radius and doubles ten times, reaching a polygon of 6144 sides, and it prints the estimate of pi to five places.",
        instructions = """
            1. Save the example as polygon_pi.py.
            2. Run it: python3 polygon_pi.py
        """,
        example = """
            from decimal import Decimal, getcontext

            getcontext().prec = 50

            def half_perimeter(doublings):
                # Start with the hexagon, whose side equals the radius of the unit circle.
                sides = 6
                s = Decimal(1)
                for _ in range(doublings):
                    # Half-angle rule: the side of the doubled polygon is the square root of 2 minus the root of 4 minus the square of the side.
                    s = (2 - (4 - s * s).sqrt()).sqrt()
                    sides *= 2
                return sides, sides * s / 2

            sides, approx = half_perimeter(10)
            print(f"sides {sides}: pi is about {approx:.5f}")
            print("agrees with pi to five places:", f"{approx:.5f}" == "3.14159")
        """,
    ),

    entry(
        databaseId = 43,
        kind = Kind.EXAMPLE,
        section = "Astronomy",
        title = "The parallax of the comet of 1618 from two stations",
        originalTitle = "De tribus cometis anni MDCXVIII",
        author = "Orazio Grassi",
        year = 1618,
        summary = "The shift of a comet against the stars between two stations, its parallax, sets its distance, which is inversely proportional to the parallax, so the example computes a distance for each of two illustrative parallaxes.",
        details = "Grassi's lecture of 1619, De tribus cometis anni MDCXVIII, argued from the lack of parallax that the comets of 1618 lay beyond the Moon, while the English astronomer Bainbridge put one of them at roughly ten times the Moon's distance, so the sources disagree on how far the comets lay. The example takes an illustrative baseline of 800 kilometres and prints the distance for two parallaxes.",
        instructions = """
            1. Save the example as comet_parallax.py.
            2. Run it: python3 comet_parallax.py
        """,
        example = """
            from math import pi

            BASELINE_KM = 800.0   # distance between the two stations, an illustrative figure

            def distance_km(parallax_deg):
                # The distance is the baseline divided by the parallax in radians, for a small angle.
                return BASELINE_KM / (parallax_deg * pi / 180)

            for parallax in (0.2, 0.5):
                print(f"parallax {parallax:.1f} degrees over {BASELINE_KM:.0f} km: distance {distance_km(parallax):.0f} km")
        """,
    ),

    entry(
        databaseId = 43,
        kind = Kind.EXAMPLE,
        section = "Astronomy",
        title = "Full orbits of the four Medicean moons in 100 days",
        originalTitle = "Medicea sidera",
        author = "Galileo Galilei",
        year = 1610,
        summary = "The four moons Galileo saw in 1610 make different numbers of full orbits in 100 days, and a short program counts them from their periods.",
        details = "Galileo announced the four moons in 1610 and gave them family names, which were later replaced by the names now in use. The example uses modern values of the periods, since Galileo's own tables gave the moons' motions only roughly, and counts the full orbits in 100 days by integer division.",
        instructions = """
            1. Save the example as jupiter_orbits.py.
            2. Run it: python3 jupiter_orbits.py
        """,
        example = """
            # Modern orbital periods in days.
            PERIODS = {"Io": 1.769137786, "Europa": 3.551181, "Ganymede": 7.154553, "Callisto": 16.689018}
            DAYS = 100

            for name, period in PERIODS.items():
                # Full orbits are counted by integer division, so a partial orbit at the end is not counted.
                print(f"{name}: {int(DAYS // period)} full orbits in {DAYS} days")
        """,
    ),

    entry(
        databaseId = 43,
        kind = Kind.EXAMPLE,
        section = "Money",
        title = "Adding guilders, stuivers and pennings at the Amsterdam exchange",
        originalTitle = "Beurs van Amsterdam",
        author = "The city of Amsterdam",
        year = 1611,
        summary = "Dutch money of account counted one guilder as 20 stuivers and one stuiver as 16 pennings, so sums are added in pennings and then written back in guilders, stuivers and pennings.",
        details = "The exchange building in Amsterdam opened in 1611, and merchants there reckoned in guilders, stuivers and pennings. The example adds two invented sums in these units, converts each to pennings, and writes the total back in the usual units.",
        instructions = """
            1. Save the example as guilders.py.
            2. Run it: python3 guilders.py
        """,
        example = """
            GUILDER = 320   # pennings in one guilder: 20 stuivers of 16 pennings each
            STUIVER = 16    # pennings in one stuiver

            def to_pennings(g, s, p):
                return g * GUILDER + s * STUIVER + p

            def from_pennings(total):
                g, rest = divmod(total, GUILDER)
                s, p = divmod(rest, STUIVER)
                return g, s, p

            a = to_pennings(3, 7, 9)
            b = to_pennings(2, 15, 12)
            g, s, p = from_pennings(a + b)
            print(f"first sum: {a} pennings")
            print(f"second sum: {b} pennings")
            print(f"together: {g} guilders {s} stuivers {p} pennings")
        """,
    ),

    entry(
        databaseId = 43,
        kind = Kind.EXAMPLE,
        section = "Language",
        title = "Counting the words of a sentence from the 1611 translation",
        originalTitle = "The Holy Bible, Authorized Version of 1611",
        author = "The translators of the Authorized Version",
        year = 1611,
        summary = "A program splits a sentence into words and reports how many there are, the length of the longest word and the average word length, the kind of count made on the 1611 translation.",
        details = "The translators finished the Authorized Version in 1611, and its English became the standard for much later reading. The sentence in the example is invented for the purpose, written in the manner of the period, and is not a quotation from the translation.",
        instructions = """
            1. Save the example as word_count.py.
            2. Run it: python3 word_count.py
        """,
        example = """
            SENTENCE = "the translators counted every word twice and found forty-seven of them"
            words = SENTENCE.split()

            print(f"words in the sentence: {len(words)}")
            print(f"longest word length: {max(len(w) for w in words)}")
            print(f"average word length: {sum(len(w) for w in words) / len(words):.2f}")
        """,
    ),

)
