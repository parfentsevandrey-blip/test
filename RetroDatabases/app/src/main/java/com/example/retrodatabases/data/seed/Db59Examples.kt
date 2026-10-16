package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1450 to 1460: lines on the openings of the 42-line Bible, the range of a great gun, the area of a lateen sail, Donatus's parts of speech as codes, the address bits of a type case and the psalms of a week. */
val db59Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 59,
        kind = Kind.EXAMPLE,
        section = "Layout",
        title = "Lines on the openings of the 42-line Bible",
        originalTitle = "Biblia Latina",
        author = "Johann Gutenberg",
        year = 1455,
        summary = "The 42-line Bible sets 42 lines in each of its two columns, so each page carries 84 lines and an opening of two facing pages carries 168, and a short program multiplies that for a run of openings.",
        details = "The book's name comes from its 42 lines to a column, and the example uses that layout for two columns on each page. The figures are the layout's own arithmetic, and the program prints the lines per page, the lines per opening and the lines on one hundred openings.",
        instructions = """
            1. Save the example as bible_lines.py.
            2. Run it: python3 bible_lines.py
        """,
        example = """
            LINES_PER_COLUMN = 42
            COLUMNS = 2
            PAGES_PER_OPENING = 2

            per_page = LINES_PER_COLUMN * COLUMNS
            per_opening = per_page * PAGES_PER_OPENING
            print("lines per page:", per_page)
            print("lines per opening:", per_opening)
            print("lines on 100 openings:", per_opening * 100)
        """,
    ),

    entry(
        databaseId = 59,
        kind = Kind.EXAMPLE,
        section = "Ordnance",
        title = "The range of a stone shot from a great gun at 45 degrees",
        originalTitle = "Basilica bombard",
        author = "The founder Orban, for Sultan Mehmed II",
        year = 1453,
        summary = "Without air resistance a shot leaving at 45 degrees travels a distance equal to the square of its speed divided by the pull of gravity, and a short program works this out for an illustrative speed.",
        details = "The range formula for a shot leaving at 45 degrees is the square of the speed over the gravitational acceleration. The speed of 100 metres a second is illustrative and is not a figure recorded for Orban's gun, and the program prints the range rounded to two decimal places.",
        instructions = """
            1. Save the example as bombard_range.py.
            2. Run it: python3 bombard_range.py
        """,
        example = """
            G = 9.81        # gravitational acceleration in metres per second squared
            SPEED = 100.0   # an illustrative muzzle speed in metres per second

            # At 45 degrees, sin(2 x 45) is 1, so the range is speed squared over g.
            distance = SPEED * SPEED / G
            print("speed 100 m/s, angle 45 degrees: range", round(distance, 2), "m")
        """,
    ),

    entry(
        databaseId = 59,
        kind = Kind.EXAMPLE,
        section = "Rigging",
        title = "The area of a lateen sail cut as a triangle",
        originalTitle = "Vela latina",
        author = "Portuguese sailors of the caravel",
        year = 1450,
        summary = "A lateen sail is a triangle, so its area is half the base times the height, and a short program works this out for two sails of the kind used on the African coast.",
        details = "The lateen sail of the caravel was a triangle hung from a long yard, and the example takes the base and height as illustrative lengths rather than measurements of any recorded ship. The program prints the area of each sail in square metres.",
        instructions = """
            1. Save the example as lateen_sail.py.
            2. Run it: python3 lateen_sail.py
        """,
        example = """
            def triangle_area(base_m, height_m):
                # The area of a triangle is half of the base times the height.
                return base_m * height_m / 2

            for base, height in ((12, 20), (10, 15)):
                print(f"lateen sail: base {base} m, height {height} m, area {triangle_area(base, height)} square metres")
        """,
    ),

    entry(
        databaseId = 59,
        kind = Kind.EXAMPLE,
        section = "Grammar",
        title = "Donatus's eight parts of speech as three-bit codes",
        originalTitle = "Ars minor",
        author = "Aelius Donatus",
        year = 1450,
        summary = "Donatus's Ars minor names eight parts of speech, and eight codes fit exactly in three binary digits, so a short program gives each part its code.",
        details = "Donatus named eight parts of speech in his grammar: noun, verb, participle, pronoun, adverb, preposition, conjunction and interjection. The example assigns the codes 000 to 111 in that order, which is an illustrative encoding and not a scheme that any school of the 1450s used.",
        instructions = """
            1. Save the example as parts_codes.py.
            2. Run it: python3 parts_codes.py
        """,
        example = """
            PARTS = ["noun", "verb", "participle", "pronoun", "adverb", "preposition", "conjunction", "interjection"]

            for index, part in enumerate(PARTS):
                # Eight parts of speech need three binary digits, so each index is written with three digits.
                code = format(index, "03b")
                print(code, part)
            print("bits needed for eight parts:", (len(PARTS) - 1).bit_length())
        """,
    ),

    entry(
        databaseId = 59,
        kind = Kind.EXAMPLE,
        section = "Type case",
        title = "Bits needed to address each compartment of a type case",
        originalTitle = "Kasten",
        author = "The compositors of the early press",
        year = 1450,
        summary = "A type case of 300 compartments needs nine binary digits to address each one, since 512 is the first power of two that covers its 300 codes.",
        details = "The count of 300 compartments is illustrative and is not the size of any case of the 1450s. The example finds the smallest number of binary digits that gives every compartment its own code.",
        instructions = """
            1. Save the example as compartment_bits.py.
            2. Run it: python3 compartment_bits.py
        """,
        example = """
            COMPARTMENTS = 300

            highest_index = COMPARTMENTS - 1
            bits = highest_index.bit_length()
            print("compartments:", COMPARTMENTS)
            print("bits to address one compartment:", bits)
            print("codes available with that many bits:", 2 ** bits)
        """,
    ),

    entry(
        databaseId = 59,
        kind = Kind.EXAMPLE,
        section = "Psalter",
        title = "Dividing the 150 psalms of the Psalter over a week",
        originalTitle = "Psalterium",
        author = "Saint Benedict, in his Rule",
        year = 1457,
        summary = "The 150 psalms split over a week give three days of 22 psalms and four of 21, and a short program prints each day's range.",
        details = "The Rule of Saint Benedict spreads the psalter over the week, and the example uses a plain even split instead of the Rule's own allocation. The Mainz Psalter of 1457 keeps the 150 psalms in the same order, so the day ranges can be followed in that book.",
        instructions = """
            1. Save the example as psalm_week.py.
            2. Run it: python3 psalm_week.py
        """,
        example = """
            PSALMS = 150
            DAYS = 7

            base, extra = divmod(PSALMS, DAYS)
            start = 1
            for day in range(1, DAYS + 1):
                # The first 'extra' days each take one more psalm than the rest.
                count = base + (1 if day <= extra else 0)
                end = start + count - 1
                print(f"day {day}: psalms {start} to {end}, {count} psalms")
                start = end + 1
            print("total:", PSALMS)
        """,
    ),

)
