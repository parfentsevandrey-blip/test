package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1470 to 1480: the Mexica 260-day count, a windlass drum, Greek articles, braccia into ells and a check digit. */
val db57Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 57,
        kind = Kind.GUIDE,
        section = "Calendars",
        title = "How the Mexica 260-day count pairs numbers with day signs",
        originalTitle = "Tonalpohualli",
        author = "Mexica calendar makers",
        year = 1479,
        summary = "The 260-day count pairs a number from one to thirteen with one of twenty day signs, and the pair repeats only after 260 days.",
        details = "The twenty day signs are carved in the outer ring of the calendar stone, which one account dates to 1479. The program prints the pair for six chosen days, and day 260 returns to the first pair.",
        instructions = """
            1. Save the example as tonalpohualli.py.
            2. Run it: python3 tonalpohualli.py
        """,
        example = """
            DAY_SIGNS = ["Cipactli", "Ehecatl", "Calli", "Cuetzpalin", "Coatl", "Miquiztli", "Mazatl", "Tochtli", "Atl", "Itzcuintli", "Ozomatli", "Malinalli", "Acatl", "Ocelotl", "Cuauhtli", "Cozcacuauhtli", "Ollin", "Tecpatl", "Quiahuitl", "Xochitl"]

            def count_name(n):
                number = n % 13 + 1
                sign = DAY_SIGNS[n % 20]
                return f"{number} {sign}"

            for n in [0, 1, 12, 13, 259, 260]:
                print(f"day {n}: {count_name(n)}")
        """,
    ),

    entry(
        databaseId = 57,
        kind = Kind.GUIDE,
        section = "Machines",
        title = "How a windlass drum turns a load into whole turns",
        originalTitle = "De re militari",
        author = "Roberto Valturio",
        year = 1472,
        summary = "A windlass drum lifts a load by the rope wound on each turn, so the full turns needed are the load divided by the drum's circumference, rounded up.",
        details = "Valturio's book of 1472 illustrates engines of war and lifting gear, and drums of this kind were used to raise weights. The example uses whole numbers, so every answer is exact and can be checked by hand.",
        instructions = """
            1. Save the example as windlass.py.
            2. Run it: python3 windlass.py
        """,
        example = """
            DRUM = 6
            for load in [40, 36, 7, 0]:
                turns = (load + DRUM - 1) // DRUM
                print(f"load {load}: {turns} turns")
        """,
    ),

    entry(
        databaseId = 57,
        kind = Kind.GUIDE,
        section = "Greek grammar",
        title = "How Greek articles agree with their nouns",
        originalTitle = "Erotemata",
        author = "Constantine Lascaris",
        year = 1476,
        summary = "A Greek article takes its gender and number from the noun it stands before, so a table of articles gives the form for each gender.",
        details = "Lascaris's grammar of 1476 sets out the article forms, and the example prints the singular and plural article for three sample nouns of different genders. Accents and breathings are left out, so the example uses plain letters only.",
        instructions = """
            1. Save the example as greek_articles.py.
            2. Run it: python3 greek_articles.py
        """,
        example = """
            ARTICLES = {"masculine": ("ho", "hoi"), "feminine": ("he", "hai"), "neuter": ("to", "ta")}
            NOUNS = [("logos", "masculine"), ("polis", "feminine"), ("ergon", "neuter")]
            for noun, gender in NOUNS:
                singular, plural = ARTICLES[gender]
                print(f"{singular} {noun} (plural article {plural})")
        """,
    ),

    entry(
        databaseId = 57,
        kind = Kind.GUIDE,
        section = "Cloth measures",
        title = "How a length in braccia converts to ells",
        originalTitle = "Braccio",
        author = "Florentine cloth merchants",
        year = 1480,
        summary = "A length measured in braccia converts to ells by a fixed ratio, and exact fractions keep every converted length precise.",
        details = "The braccio was the cloth measure used at Florence, and the ratio in the example is illustrative and is not a historical rate. The example converts three lengths and prints each result as an exact fraction or a whole number.",
        instructions = """
            1. Save the example as braccia.py.
            2. Run it: python3 braccia.py
        """,
        example = """
            from fractions import Fraction

            ELLS_PER_BRACCIO = Fraction(4, 5)   # an illustrative ratio, not a historical one

            for braccia in [8, 12, 15]:
                ells = braccia * ELLS_PER_BRACCIO
                print(f"{braccia} braccia = {ells} ells")
        """,
    ),

    entry(
        databaseId = 57,
        kind = Kind.GUIDE,
        section = "Library",
        title = "How a library accession number gets a check digit",
        originalTitle = "Bibliotheca Apostolica Vaticana",
        author = "Vatican Library",
        year = 1475,
        summary = "A check digit is computed from the other digits, so a copied number with a slip shows up as a mismatch.",
        details = "The weights in the example are invented for illustration, and they are not a historical catalogue rule. The program prints three numbers with their check digits, computed by a weighted sum of the digits.",
        instructions = """
            1. Save the example as check_digit.py.
            2. Run it: python3 check_digit.py
        """,
        example = """
            def with_check_digit(number):
                digits = [int(d) for d in str(number)]
                weighted = sum(weight * digit for weight, digit in enumerate(digits, start=1))
                return f"{number}-{weighted % 10}"

            for number in [1475, 1479, 1476]:
                print(with_check_digit(number))
        """,
    ),

)
