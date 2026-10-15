package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1570 to 1580: Euclid's square of a sum, Palladio's column heights, Nahuatl numbers, Greek letter numbers, the fineness of silver and a grid of map sheets. */
val db47Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 47,
        kind = Kind.EXAMPLE,
        section = "Geometry",
        title = "Euclid's square of a sum, checked with numbers",
        originalTitle = "Euclidis Elementorum libri XV",
        author = "Federico Commandino",
        year = 1572,
        summary = "Euclid's second book shows that the square on a sum of two lines equals the squares on the lines plus twice the rectangle between them, and the example checks this for three pairs of whole numbers.",
        details = "Proposition II.4 is the geometric form of the identity (a + b) squared equals a squared plus b squared plus 2ab, and Commandino's Latin edition of 1572 gives it in the same form. Each check in the example prints the whole square and the three parts, so the equality can be read off directly.",
        instructions = """
            1. Save the example as euclid_square.py.
            2. Run it: python3 euclid_square.py
        """,
        example = """
            for a, b in ((3, 5), (4, 4), (2, 7)):
                whole = (a + b) ** 2
                parts = a * a + b * b + 2 * a * b
                print(f"segments {a} and {b}: whole square {whole}, parts {a * a} + {b * b} + {2 * a * b}, equal: {whole == parts}")
        """,
    ),

    entry(
        databaseId = 47,
        kind = Kind.EXAMPLE,
        section = "Architecture",
        title = "Column heights of four orders in Palladio's proportions",
        originalTitle = "I quattro libri dell'architettura",
        author = "Andrea Palladio",
        year = 1570,
        summary = "Palladio gave each classical order a column height as a fixed number of lower diameters, and the example turns those multiples into centimetres for a column 60 centimetres across.",
        details = "The Tuscan column is taken as 7 diameters high, the Doric 8, the Ionic 9 and the Corinthian 10, the proportions that Palladio's book of 1570 sets out for the orders. The size of 60 centimetres for the lower diameter is illustrative, so the heights show the rule and not a measured building.",
        instructions = """
            1. Save the example as column_heights.py.
            2. Run it: python3 column_heights.py
        """,
        example = """
            DIAMETER_CM = 60   # lower diameter of the column, an illustrative size
            ORDERS = [("Tuscan", 7), ("Doric", 8), ("Ionic", 9), ("Corinthian", 10)]
            for name, diameters in ORDERS:
                print(f"{name}: {diameters} diameters, {diameters * DIAMETER_CM} cm")
            print(f"Corinthian minus Tuscan: {(10 - 7) * DIAMETER_CM} cm")
        """,
    ),

    entry(
        databaseId = 47,
        kind = Kind.EXAMPLE,
        section = "Nahuatl",
        title = "Nahuatl number words for 1 to 10, 20 and 400",
        originalTitle = "Vocabulario en lengua mexicana y castellana",
        author = "Alonso de Molina",
        year = 1571,
        summary = "The Nahuatl words for the numbers one to ten, for twenty and for four hundred show a count built on twenties, and the example prints them with their numerals.",
        details = "Molina's dictionary of 1571 records the Nahuatl language of Mexico, and the words below are standard Nahuatl number words. The example prints them in order, so the twenty-based count shows in the step from ten to twenty and then to four hundred.",
        instructions = """
            1. Save the example as nahuatl_numbers.py.
            2. Run it: python3 nahuatl_numbers.py
        """,
        example = """
            NUMBERS = {1: "ce", 2: "ome", 3: "yei", 4: "nahui", 5: "macuilli", 6: "chicuace",
                       7: "chicome", 8: "chicuei", 9: "chicnahui", 10: "mahtlactli",
                       20: "cempohualli", 400: "centzontli"}
            for number in (1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 20, 400):
                print(number, NUMBERS[number])
        """,
    ),

    entry(
        databaseId = 47,
        kind = Kind.EXAMPLE,
        section = "Numerals",
        title = "Greek letter numbers: 572, 27 and 999 spelt out",
        originalTitle = "Thesaurus Graecae linguae",
        author = "Henri Estienne",
        year = 1572,
        summary = "The Greek alphabet doubled as a set of numerals, so a number could be spelt out as a short run of letter names, and the example does this for three numbers.",
        details = "Estienne's Thesaurus of 1572 was printed for readers of Greek texts, where letter numbers appear in lists and in ancient writings. The example takes the largest letter value first at each step, so 572 becomes phi, omicron and beta.",
        instructions = """
            1. Save the example as greek_numbers.py.
            2. Run it: python3 greek_numbers.py
        """,
        example = """
            GREEK = [(900, "sampi"), (800, "omega"), (700, "psi"), (600, "chi"), (500, "phi"),
                     (400, "upsilon"), (300, "tau"), (200, "sigma"), (100, "rho"), (90, "koppa"),
                     (80, "pi"), (70, "omicron"), (60, "xi"), (50, "nu"), (40, "mu"),
                     (30, "lambda"), (20, "kappa"), (10, "iota"), (9, "theta"), (8, "eta"),
                     (7, "zeta"), (5, "epsilon"), (4, "delta"), (3, "gamma"), (2, "beta"), (1, "alpha")]

            def spell(number):
                names = []
                for value, name in GREEK:
                    while number >= value:
                        names.append(name)
                        number -= value
                return " ".join(names)

            for number in (572, 27, 999):
                print(f"{number}: {spell(number)}")
        """,
    ),

    entry(
        databaseId = 47,
        kind = Kind.EXAMPLE,
        section = "Assaying",
        title = "Fineness of silver from an assay, in parts per thousand",
        originalTitle = "Beschreibung allerfurnemisten mineralischen Ertz und Bergkwercksarten",
        author = "Lazarus Ercker",
        year = 1574,
        summary = "The fineness of a silver alloy is the weight of fine silver in a thousand parts of the sample, and the example works it out for three assays.",
        details = "Ercker's book of 1574 set out the tests by which miners and mint men found how much silver an ore or a coin held, and fineness is the usual measure in such tests. The weights in the example are illustrative and are not Ercker's own figures.",
        rare = true,
        instructions = """
            1. Save the example as silver_fineness.py.
            2. Run it: python3 silver_fineness.py
        """,
        example = """
            from fractions import Fraction as F

            def fineness(fine_grams, sample_grams):
                # Parts of fine silver in a thousand parts of the sample.
                return F(fine_grams, sample_grams) * 1000

            for fine, sample in ((18, 24), (9, 10), (7, 20)):
                print(f"{fine} g of fine silver in {sample} g: fineness {fineness(fine, sample)} parts per thousand")
        """,
    ),

    entry(
        databaseId = 47,
        kind = Kind.EXAMPLE,
        section = "Maps",
        title = "Ortelius's grid index for map sheets of ten degrees",
        originalTitle = "Theatrum orbis terrarum",
        author = "Abraham Ortelius",
        year = 1570,
        summary = "A grid index of ten-degree cells gives each place a sheet label built from its latitude band and its longitude band, and the example labels four places.",
        details = "Ortelius's atlas of 1570 was a set of separate sheets, and a numbered grid is a common way to find a sheet by place. The grid in the example is illustrative and is not Ortelius's own numbering, and it uses whole degrees, so the labels come out exactly.",
        instructions = """
            1. Save the example as sheet_grid.py.
            2. Run it: python3 sheet_grid.py
        """,
        example = """
            PLACES = [("Antwerp", 51, 4), ("Rome", 41, 12), ("Lisbon", 38, -9), ("Cape of Good Hope", -34, 18)]
            for name, lat, lon in PLACES:
                band = (lat + 90) // 10      # 0 at the south pole, 18 at the north pole
                column = (lon + 180) // 10   # 0 at 180 degrees west, 36 at 180 degrees east
                print(f"{name} ({lat}, {lon}) falls in sheet R{band:02d}C{column:02d}")
        """,
    ),

)
