package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1580 to 1590: knockout matches, a lever for an obelisk, Porta's image size, the Hijri cycle, substitution keys and Lee's stitches. */
val db46Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 46,
        kind = Kind.EXAMPLE,
        section = "Tournaments",
        title = "Knockout matches in a tournament of 16 players",
        originalTitle = "Knockout matches in a tournament of 16 players",
        author = "Tournament organisers of the period",
        year = 1585,
        summary = "A knockout tournament needs one match fewer than it has players, and a short program counts the matches and the rounds for three tournament sizes.",
        details = "Each match removes one player, so a tournament of n players needs n minus 1 matches. The number of rounds is the power of two that gives the bracket size, and the example uses illustrative sizes, not the figures of any particular event.",
        instructions = """
            1. Save the example as knockout.py.
            2. Run it: python3 knockout.py
        """,
        example = """
            for players in (16, 8, 4):
                rounds = players.bit_length() - 1
                print(f"{players} players: {players - 1} matches in {rounds} rounds")
        """,
    ),

    entry(
        databaseId = 46,
        kind = Kind.EXAMPLE,
        section = "Engineering",
        title = "Lifting the Vatican obelisk with a lever, 1586",
        originalTitle = "Lifting the Vatican obelisk with a lever, 1586",
        author = "Domenico Fontana",
        year = 1586,
        summary = "Domenico Fontana raised the Vatican obelisk for Pope Sixtus V in 1586, and a short program works out the lifting force for three levers with illustrative arms.",
        details = "Fontana moved and set up the obelisk in 1586 with ropes, winches and levers, and the lever rule balances load times short arm against force times long arm. The example uses illustrative loads and arm lengths rather than the engineers' figures, and it gives each force as an exact fraction.",
        instructions = """
            1. Save the example as obelisk_lever.py.
            2. Run it: python3 obelisk_lever.py
        """,
        example = """
            from fractions import Fraction as F

            def lifting_force(weight, short_arm, long_arm):
                # A lever balances when the load times its short arm equals the force times its long arm.
                return F(weight * short_arm, long_arm)

            for weight, short_arm, long_arm in ((1000, 4, 40), (1200, 3, 36), (900, 5, 20)):
                print(f"load {weight} with arms {short_arm} and {long_arm}: force {lifting_force(weight, short_arm, long_arm)}")
        """,
    ),

    entry(
        databaseId = 46,
        kind = Kind.EXAMPLE,
        section = "Calendars",
        title = "The 30-year cycle of the Hijri calendar",
        originalTitle = "The Hijri calendar",
        author = "Islamic astronomers",
        year = 1580,
        summary = "In the 30-year Hijri cycle eleven years have 355 days and nineteen have 354 days, and a short program counts the leap years and the days in one cycle.",
        details = "The Islamic calendar counts lunar months, so a common year has 354 days and a leap year 355, and eleven leap years in each thirty-year cycle keep the calendar in step with the moon. The example applies the tabular rule for the leap years and prints the days of one cycle.",
        instructions = """
            1. Save the example as hijri_cycle.py.
            2. Run it: python3 hijri_cycle.py
        """,
        example = """
            def is_leap(year):
                # In the 30-year cycle of the Hijri calendar, eleven years are leap years, and this rule picks them out.
                return (11 * year + 14) % 30 < 11

            leap_years = [year for year in range(1, 31) if is_leap(year)]
            print("leap years in the cycle:", len(leap_years))
            print("leap years by number:", " ".join(str(year) for year in leap_years))
            print("days in the cycle:", 30 * 354 + len(leap_years))
        """,
    ),

    entry(
        databaseId = 46,
        kind = Kind.EXAMPLE,
        section = "Optics",
        title = "Porta's camera obscura and the size of an image",
        originalTitle = "Magia naturalis",
        author = "Giambattista della Porta",
        year = 1589,
        summary = "A pinhole image has a height set by the ratio of the image distance to the object distance, and a short program works out the image height for three cases.",
        details = "Porta described the camera obscura in his Magia naturalis, and the rule used here follows from similar triangles through a small hole. The example uses illustrative distances, not Porta's own measurements, and it gives the image heights as exact fractions.",
        instructions = """
            1. Save the example as image_size.py.
            2. Run it: python3 image_size.py
        """,
        example = """
            from fractions import Fraction as F

            def image_height(object_height, object_distance, image_distance):
                # The image is to the object as the image distance is to the object distance, the ratio of similar triangles.
                return F(object_height * image_distance, object_distance)

            for height, distance, image in ((6, 30, 2), (4, 10, 3), (9, 45, 5)):
                print(f"object {height} at {distance}, image distance {image}: image height {image_height(height, distance, image)}")
        """,
    ),

    entry(
        databaseId = 46,
        kind = Kind.EXAMPLE,
        section = "Ciphers",
        title = "Keys of a 26-letter substitution: 26 factorial",
        originalTitle = "Keys of a 26-letter substitution: 26 factorial",
        author = "Cipher writers of the period",
        year = 1586,
        summary = "A simple substitution on the 26 letters has 26 factorial keys, a number of 27 digits that a short program prints exactly.",
        details = "Each key is an arrangement of the 26 cipher letters, so the count is 26 times 25 times 24 and so on down to one. The example prints the exact value and its number of digits.",
        instructions = """
            1. Save the example as substitution_keys.py.
            2. Run it: python3 substitution_keys.py
        """,
        example = """
            import math

            keys = math.factorial(26)
            print("keys for 26 letters:", keys)
            print("digits in the count:", len(str(keys)))
        """,
    ),

    entry(
        databaseId = 46,
        kind = Kind.EXAMPLE,
        section = "Knitting",
        title = "Lee's stitch count for a stocking leg",
        originalTitle = "The stocking frame",
        author = "William Lee",
        year = 1589,
        summary = "A stocking leg of 96 needles and 40 courses holds 3840 stitches, and a short program counts the stitches for three illustrative legs.",
        details = "Lee's frame of 1589 knitted loops in courses across a row of needles, so the stitch count of a leg is needles times courses. The needle and course counts are illustrative and are not figures from Lee's own machine.",
        instructions = """
            1. Save the example as stitch_count.py.
            2. Run it: python3 stitch_count.py
        """,
        example = """
            LEGS = ((96, 40), (80, 50), (64, 30))   # needles and courses, illustrative

            total = 0
            for needles, courses in LEGS:
                stitches = needles * courses
                total += stitches
                print(f"{needles} needles, {courses} courses: {stitches} stitches")
            print("total stitches:", total)
        """,
    ),

)
