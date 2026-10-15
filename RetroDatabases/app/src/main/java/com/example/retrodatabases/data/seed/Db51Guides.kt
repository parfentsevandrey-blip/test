package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1530 to 1540: the golden number of a year, a sundial's shadow, carats in an alloy, a rhumb's northing and French -er verbs. */
val db51Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 51,
        kind = Kind.GUIDE,
        section = "Calendars",
        title = "How the golden number of a year is found",
        originalTitle = "Calendar tables with the golden number",
        author = "Printers of the period",
        year = 1530,
        summary = "The golden number of a year is its place in the nineteen-year cycle, found by taking the year modulo 19 and adding one, and a short program prints those numbers for 1530 to 1540.",
        details = "Printed calendars of the period gave the golden number so that readers could find the date of Easter, and the cycle of nineteen years goes back to Meton of Athens. The example prints the golden numbers of 1530 to 1540, which climb from 11 in 1530 to 19 in 1538 and then start again at 1 in 1539.",
        instructions = """
            1. Save the example as golden_number.py.
            2. Run it: python3 golden_number.py
        """,
        example = """
            for year in range(1530, 1541):
                golden = year % 19 + 1
                print(f"{year}: golden number {golden}")
        """,
    ),

    entry(
        databaseId = 51,
        kind = Kind.GUIDE,
        section = "Sundials",
        title = "How a sundial's shadow follows the sun's height",
        originalTitle = "Horologiographia",
        author = "Sebastian Munster",
        year = 1531,
        summary = "On level ground a vertical stick casts a shadow equal to its height divided by the tangent of the sun's altitude, and a short program gives three shadows as exact fractions.",
        details = "Munster's book of 1531 taught the drawing of sundials, and every hour line on a dial comes from this geometry of shadows. The example uses altitudes whose tangents are exact fractions, so the shadow lengths print without rounding.",
        instructions = """
            1. Save the example as sundial_shadow.py.
            2. Run it: python3 sundial_shadow.py
        """,
        example = """
            from fractions import Fraction as F

            HEIGHT = 3   # the height of the stick, in feet, as an illustrative figure

            def shadow(height, tangent_of_altitude):
                # On level ground the shadow is the height divided by the tangent of the sun's altitude.
                return F(height) / tangent_of_altitude

            for tangent in (F(1), F(1, 2), F(2)):
                print(f"tangent {tangent}: shadow {shadow(HEIGHT, tangent)} feet")
        """,
    ),

    entry(
        databaseId = 51,
        kind = Kind.GUIDE,
        section = "Metals",
        title = "How carats give the gold in an alloy",
        originalTitle = "Pirotechnia",
        author = "Vannoccio Biringuccio",
        year = 1540,
        summary = "A carat counts gold in parts of twenty-four, so an alloy of 18 carats holds three parts of gold in four, and a short program turns carat counts into exact fractions.",
        details = "Biringuccio's Pirotechnia of 1540 describes the assay of metals, and the scale of twenty-four parts for gold was in use among goldsmiths of the period. The example reads each carat count as a fraction of gold and gives the rest as the share of other metals.",
        instructions = """
            1. Save the example as carat_share.py.
            2. Run it: python3 carat_share.py
        """,
        example = """
            from fractions import Fraction as F

            def gold_share(carats):
                # Gold is counted in parts of 24, so the share of gold is the carat count over 24.
                return F(carats, 24)

            for carats in (18, 14, 9, 24):
                share = gold_share(carats)
                print(f"{carats} carats: gold {share}, other metals {1 - share}")
        """,
    ),

    entry(
        databaseId = 51,
        kind = Kind.GUIDE,
        section = "Navigation",
        title = "How a rhumb's northing follows from its course",
        originalTitle = "Tratado em defensam da carta de marear",
        author = "Pedro Nunes",
        year = 1537,
        summary = "On a rhumb the gain to the north is the distance times the cosine of the course, and a short program gives the northing of a 120-mile run on three courses.",
        details = "Nunes's treatise of 1537 studied the rhumb, a course kept at a fixed angle to the meridians. The example uses the flat rule for a short run, which is a simplified rule for the navigator and not a full treatment of the curve on the globe.",
        instructions = """
            1. Save the example as rhumb_northing.py.
            2. Run it: python3 rhumb_northing.py
        """,
        example = """
            from fractions import Fraction as F

            COSINE = {0: F(1), 60: F(1, 2), 90: F(0)}   # exact cosines of three courses, measured from north

            def northing(distance, course):
                # The gain to the north is the distance times the cosine of the course.
                return distance * COSINE[course]

            for course in (0, 60, 90):
                print(f"course {course} degrees, run 120 miles: northing {northing(120, course)} miles")
        """,
    ),

    entry(
        databaseId = 51,
        kind = Kind.GUIDE,
        section = "Grammar",
        title = "How a French -er verb takes its present endings",
        originalTitle = "Lesclarcissement de la langue francoyse",
        author = "John Palsgrave",
        year = 1530,
        summary = "Regular French verbs ending in -er share one set of present-tense endings, and a short program conjugates the verb parler for the six persons.",
        details = "Palsgrave's grammar of 1530 set out the French verbs for English readers, and the regular -er verbs form the largest class of French verbs. The example applies the present-tense endings to one verb, and the table it prints is the modern conjugation rather than a quotation from the book.",
        instructions = """
            1. Save the example as er_verbs.py.
            2. Run it: python3 er_verbs.py
        """,
        example = """
            STEM = "parl"
            ENDINGS = ["e", "es", "e", "ons", "ez", "ent"]
            PRONOUNS = ["je", "tu", "il", "nous", "vous", "ils"]

            for pronoun, ending in zip(PRONOUNS, ENDINGS):
                print(pronoun, STEM + ending)
        """,
    ),

)
