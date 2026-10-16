package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1460 to 1470: a relay rota, a common prefix of two Latin words, Heron's area of a 13-14-15 triangle, a day of the year, a pairing sum and the divisors of 1460. */
val db58Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 58,
        kind = Kind.EXAMPLE,
        section = "Post stations",
        title = "A round-robin rota for relay horses at a post station",
        originalTitle = "Postes royales",
        author = "Louis XI of France",
        year = 1464,
        summary = "A post station with three horses can rotate its rides so that each horse takes one stage in turn, and a short program prints the rota for seven rides.",
        details = "Louis XI's relay horses of 1464 are the setting, but the rota is an illustrative schedule of three named horses, not a record of any real station. The example uses the remainder operator to give each horse its turn.",
        instructions = """
            1. Save the example as relay_rota.py.
            2. Run it: python3 relay_rota.py
        """,
        example = """
            HORSES = ["bay", "grey", "roan"]
            RIDES = 7

            for ride in range(1, RIDES + 1):
                horse = HORSES[(ride - 1) % len(HORSES)]
                print(f"ride {ride}: {horse}")
        """,
    ),

    entry(
        databaseId = 58,
        kind = Kind.EXAMPLE,
        section = "Latin words",
        title = "Shared leading letters of two Latin headwords",
        originalTitle = "Catholicon",
        author = "Johannes Balbus of Genoa, printed at Mainz",
        year = 1460,
        summary = "The leading letters that two Latin headwords share form a common prefix, and a short program finds it for four pairs of words.",
        details = "The Catholicon of 1460 is the setting, and its headwords are the kind of words the program compares. The four pairs are illustrative, and the program counts shared letters from the start of each word.",
        instructions = """
            1. Save the example as common_prefix.py.
            2. Run it: python3 common_prefix.py
        """,
        example = """
            def common_prefix(first, second):
                # Walk the two words together and stop at the first letter that differs.
                count = 0
                for a, b in zip(first, second):
                    if a != b:
                        break
                    count += 1
                return first[:count]

            pairs = [("amicus", "amica"), ("amor", "amoris"), ("ars", "artis"), ("liber", "libri")]
            for first, second in pairs:
                prefix = common_prefix(first, second)
                print(f"{first} and {second} share {prefix!r} ({len(prefix)} letters)")
        """,
    ),

    entry(
        databaseId = 58,
        kind = Kind.EXAMPLE,
        section = "Triangles",
        title = "Heron's area of a 13-14-15 triangle",
        originalTitle = "De triangulis omnimodis",
        author = "Johannes Regiomontanus",
        year = 1464,
        summary = "Heron's formula gives the area of a triangle from its three sides, and for sides 13, 14 and 15 the area is the whole number 84.",
        details = "The 13-14-15 triangle is a whole-number example, and the example uses exact fractions for the half perimeter and the three differences. The subject is the same as Regiomontanus's book on triangles, but the example itself is illustrative.",
        instructions = """
            1. Save the example as heron_area.py.
            2. Run it: python3 heron_area.py
        """,
        example = """
            from fractions import Fraction as F

            def heron_area_squared(a, b, c):
                # Heron's formula: the squared area is s(s-a)(s-b)(s-c), where s is half the perimeter.
                s = F(a + b + c, 2)
                return s * (s - a) * (s - b) * (s - c)

            a, b, c = 13, 14, 15
            area_sq = heron_area_squared(a, b, c)
            print(f"sides {a}, {b}, {c}: squared area {area_sq}")
            print(f"area {int(area_sq ** 0.5)}")
        """,
    ),

    entry(
        databaseId = 58,
        kind = Kind.EXAMPLE,
        section = "Calendar",
        title = "The day of the year for the Eltville printing",
        originalTitle = "Vocabularius ex quo",
        author = "Heinrich and Nikolaus Bechtermunze of Eltville",
        year = 1467,
        summary = "The Eltville first edition of 4 November 1467 falls on day 308 of a 365-day year, and a short program computes the day number with the date library.",
        details = "The Eltville word list is dated 4 November 1467. The year 1467 has 365 days, so the program prints the day number of that date next to the length of the year.",
        instructions = """
            1. Save the example as day_of_year.py.
            2. Run it: python3 day_of_year.py
        """,
        example = """
            from datetime import date

            printing = date(1467, 11, 4)   # the Eltville first edition, as commonly given
            year_start = date(1467, 1, 1)
            year_end = date(1468, 1, 1)
            day = printing.timetuple().tm_yday
            length = (year_end - year_start).days
            print(f"4 November 1467 is day {day} of {length}")
        """,
    ),

    entry(
        databaseId = 58,
        kind = Kind.EXAMPLE,
        section = "Sums",
        title = "Gauss's pairing sum of the years 1460 to 1470",
        originalTitle = "Gasparinus's Epistolae",
        author = "Gasparinus of Barzizza, printed at Paris",
        year = 1470,
        summary = "Adding the years 1460 to 1470 by pairing their ends gives the same total as adding them one by one, and a short program checks both ways.",
        details = "The Paris letters of Gasparinus appeared in 1470, the last year of the decade, and the example sums the years of the decade. The pairing method is a schoolroom trick, and the program prints both totals so that they can be compared.",
        instructions = """
            1. Save the example as pairing_sum.py.
            2. Run it: python3 pairing_sum.py
        """,
        example = """
            first, last = 1460, 1470
            count = last - first + 1
            by_pairing = count * (first + last) // 2
            by_adding = sum(range(first, last + 1))
            print(f"years {first} to {last}: {count} years")
            print(f"sum by pairing: {by_pairing}")
            print(f"sum by adding: {by_adding}")
            print("the two sums agree" if by_pairing == by_adding else "the two sums differ")
        """,
    ),

    entry(
        databaseId = 58,
        kind = Kind.EXAMPLE,
        section = "Number theory",
        title = "Divisors of 1460 by trial division",
        originalTitle = "Universitas studii Basiliensis",
        author = "Pope Pius II, by the founding bull of 1459",
        year = 1460,
        summary = "The number 1460 has twelve divisors whose sum is 3108, and a short program finds them by trial division over the numbers up to 1460.",
        details = "The University of Basel opened in 1460, and the example uses that year as its number. The program tests every candidate from one to the year itself, which is slow for large numbers but clear for a year.",
        instructions = """
            1. Save the example as divisors.py.
            2. Run it: python3 divisors.py
        """,
        example = """
            n = 1460
            divisors = [d for d in range(1, n + 1) if n % d == 0]
            print(f"{n} has {len(divisors)} divisors")
            print("divisors:", " ".join(str(d) for d in divisors))
            print("their sum:", sum(divisors))
        """,
    ),

)
