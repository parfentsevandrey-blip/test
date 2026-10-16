package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1450 to 1460: the pages of a folio sheet, a south-up map turned half a turn, a scribal bar, a screw press's multiplying lever and the justified line of the 42-line Bible. */
val db59Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 59,
        kind = Kind.GUIDE,
        section = "Imposition",
        title = "How a folio sheet is imposed, with which page goes where",
        originalTitle = "Folio",
        author = "The printers of the 42-line Bible",
        year = 1455,
        summary = "A folio sheet printed on both sides carries four pages, with page 4 and page 1 on the outer side and pages 2 and 3 on the inner side, so that the folded sheet reads in order.",
        details = "The 42-line Bible was printed in folio, and each sheet was folded once to make two leaves. The example prints the page pairs for each side of one sheet, so the order of the pages can be checked by hand.",
        instructions = """
            1. Save the example as folio_imposition.py.
            2. Run it: python3 folio_imposition.py
        """,
        example = """
            # One folio sheet: four pages, two on each side of the sheet.
            OUTER = (4, 1)
            INNER = (2, 3)

            print("outer side:", OUTER[0], "|", OUTER[1])
            print("inner side:", INNER[0], "|", INNER[1])
        """,
    ),

    entry(
        databaseId = 59,
        kind = Kind.GUIDE,
        section = "Maps",
        title = "How a south-up map is turned north up",
        originalTitle = "Mappamondo",
        author = "Fra Mauro, a monk of San Michele",
        year = 1459,
        summary = "A map with south at the top becomes north-up after a half turn, which moves each point to the opposite side of the map's centre, so a column becomes the width minus one minus the column and a row becomes the height minus one minus the row.",
        details = "Fra Mauro's world map has south at the top, and a reader who turns it to put north at the top makes a half turn, which flips both its columns and its rows. The example takes an illustrative map of 100 by 80 cells, counted from the top left, and prints where two points lie after the turn.",
        instructions = """
            1. Save the example as south_up.py.
            2. Run it: python3 south_up.py
        """,
        example = """
            WIDTH = 100
            HEIGHT = 80

            def north_up(x, y):
                # A half turn flips the columns and the rows, so both counts run back from their last index.
                return WIDTH - 1 - x, HEIGHT - 1 - y

            for x, y in ((10, 20), (99, 0)):
                nx, ny = north_up(x, y)
                print(f"south-up ({x}, {y}) -> north-up ({nx}, {ny})")
        """,
    ),

    entry(
        databaseId = 59,
        kind = Kind.GUIDE,
        section = "Abbreviations",
        title = "How a scribal bar expands a short word",
        originalTitle = "Nota abbreviationis",
        author = "The scribes of Latin books",
        year = 1450,
        summary = "A bar written over a vowel in a scribe's abbreviation stood for a dropped m, so a short form such as ta with its bar expands to tam, and a short program spells out a few such forms.",
        details = "Scribes used a stroke over a vowel for a missing m or n, and early printers had to supply sorts for such signs. The example uses a tilde in place of the stroke and expands each form with the rule that a vowel before the tilde takes an m.",
        rare = true,
        instructions = """
            1. Save the example as scribal_bar.py.
            2. Run it: python3 scribal_bar.py
        """,
        example = """
            VOWELS = "aeiou"

            def expand(short):
                # A tilde after a vowel stands for a dropped m, so the tilde is replaced by m.
                if short.endswith("~") and short[-2] in VOWELS:
                    return short[:-1] + "m"
                return short

            for short in ("ta~", "do~", "ho~", "cu~"):
                print(short, "->", expand(short))
        """,
    ),

    entry(
        databaseId = 59,
        kind = Kind.GUIDE,
        section = "Presses",
        title = "How a screw press multiplies force through its lever",
        originalTitle = "Torcular",
        author = "Johann Gutenberg, after the wine press",
        year = 1450,
        summary = "In a screw press the force from the hand is multiplied by the length of the lever's circle divided by the pitch of the screw, which is two pi times the lever length over the pitch.",
        details = "The screw of Gutenberg's press was turned by a lever, and the ratio of the circle the handle travels to the distance the screw advances in one turn gives the gain of force. The example applies the formula to three illustrative lever and pitch pairs, which are not measurements of any surviving press.",
        instructions = """
            1. Save the example as screw_press.py.
            2. Run it: python3 screw_press.py
        """,
        example = """
            import math

            def multiplication(lever_cm, pitch_cm):
                # The handle travels a circle of two pi times the lever length for each turn of the screw.
                return 2 * math.pi * lever_cm / pitch_cm

            for lever, pitch in ((60, 2), (30, 3), (50, 4)):
                print(f"lever {lever} cm, pitch {pitch} cm: {multiplication(lever, pitch):.1f} times")
        """,
    ),

    entry(
        databaseId = 59,
        kind = Kind.GUIDE,
        section = "Justification",
        title = "How a printed line is justified by spreading its spaces",
        originalTitle = "How a printed line is justified by spreading its spaces",
        author = "The compositors of Gutenberg's shop",
        year = 1455,
        summary = "A compositor fills a line to its full measure by sharing the spare width among the word spaces, and a short program works out the spaces for one line.",
        details = "The 42-line Bible is set with both margins straight, which its compositors achieved by spreading the spare width among the spaces and by choosing abbreviations to fill a line. The example takes four words and a measure of 30 characters, and it prints the spaces that each gap receives in an illustrative line rather than in a page of the book.",
        instructions = """
            1. Save the example as justify_line.py.
            2. Run it: python3 justify_line.py
        """,
        example = """
            WORDS = ["In", "principio", "creavit", "Deus"]
            MEASURE = 30

            def justify(words, measure):
                # The spare width is shared among the gaps between words, the extra spaces going to the first gaps.
                letters = sum(len(word) for word in words)
                gaps = len(words) - 1
                spare = measure - letters - gaps
                base, extra = divmod(spare, gaps)
                spaces = [1 + base + (1 if i < extra else 0) for i in range(gaps)]
                line = words[0]
                for space, word in zip(spaces, words[1:]):
                    line += " " * space + word
                return line, spaces

            line, spaces = justify(WORDS, MEASURE)
            print(line)
            print("length:", len(line))
            print("gap spaces:", spaces)
        """,
    ),

)
