package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1490 to 1500: a treaty line in degrees, a Greek article quiz, Slavonic numerals, a queen's squares in Lucena's chess, Hebrew final letters and Greek letters in Latin. */
val db55Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 55,
        kind = Kind.EXAMPLE,
        section = "Treaties",
        title = "Turning the Tordesillas line from leagues into degrees",
        originalTitle = "Tratado de Tordesillas",
        author = "Castile and Portugal",
        year = 1494,
        summary = "The line of 370 leagues in the Treaty of Tordesillas comes to about 21 degrees at a common reckoning of seventeen and a half leagues to a degree.",
        details = "The reckoning is an illustration, and accounts differ on the number of leagues to a degree, so the example shows the arithmetic and not the position of the line. The output is an exact fraction, 148/7 of a degree, which the program also writes as 21 and 1/7 degrees.",
        instructions = """
            1. Save the example as leagues_to_degrees.py.
            2. Run it: python3 leagues_to_degrees.py
        """,
        example = """
            from fractions import Fraction

            LEAGUES_PER_DEGREE = Fraction(35, 2)   # seventeen and a half leagues to a degree, an illustrative reckoning
            line = 370                             # leagues west of the Cape Verde islands, as the treaty gives it
            degrees = Fraction(line) / LEAGUES_PER_DEGREE
            print(f"{line} leagues at {LEAGUES_PER_DEGREE} leagues to a degree is {degrees} degrees")
            whole, part = divmod(degrees.numerator, degrees.denominator)
            print(f"that is {whole} and {part}/{degrees.denominator} degrees")
        """,
    ),

    entry(
        databaseId = 55,
        kind = Kind.EXAMPLE,
        section = "Greek",
        title = "A Greek article quiz for learners",
        originalTitle = "Erotemata",
        author = "Constantine Lascaris",
        year = 1495,
        summary = "A question-and-answer grammar of Greek, such as Lascaris's, taught the article by drill, and this quiz produces the same kind of question and answer for each gender and number.",
        details = "The forms are the nominative article in transliteration: ho, he and to in the singular, and hoi, hai and ta in the plural. The quiz is a teaching drill, and its output is fixed by the table of forms.",
        instructions = """
            1. Save the example as article_quiz.py.
            2. Run it: python3 article_quiz.py
        """,
        example = """
            SINGULAR = {"masculine": "ho", "feminine": "he", "neuter": "to"}
            PLURAL = {"masculine": "hoi", "feminine": "hai", "neuter": "ta"}
            for number, forms in (("singular", SINGULAR), ("plural", PLURAL)):
                for gender in ("masculine", "feminine", "neuter"):
                    print(f"Q: the {number} article of a {gender} noun? A: {forms[gender]}")
        """,
    ),

    entry(
        databaseId = 55,
        kind = Kind.EXAMPLE,
        section = "Numerals",
        title = "Cyrillic letters as numbers in the Slavonic books",
        originalTitle = "Oktoih",
        author = "Schweipolt Fiol",
        year = 1491,
        summary = "Slavonic books wrote numbers with Cyrillic letters, each letter standing for a value, and this program writes a few numbers in that system.",
        details = "The values run from one to nine hundred in the standard Slavonic order, and the example joins the letters for hundreds, tens and units. The titlo mark that the books put over numerals is left out, so the output is the plain letters.",
        instructions = """
            1. Save the example as slavonic_numerals.py.
            2. Run it: python3 slavonic_numerals.py
        """,
        example = """
            # Slavonic letter numerals: the letters of the Cyrillic alphabet in their standard order, by code point.
            LETTERS = {
                1: chr(0x430), 2: chr(0x432), 3: chr(0x433), 4: chr(0x434), 5: chr(0x435),
                6: chr(0x455), 7: chr(0x437), 8: chr(0x438), 9: chr(0x473), 10: chr(0x456),
                20: chr(0x43A), 30: chr(0x43B), 40: chr(0x43C), 50: chr(0x43D),
                60: chr(0x46F), 70: chr(0x43E), 80: chr(0x43F), 90: chr(0x447),
                100: chr(0x440), 200: chr(0x441), 300: chr(0x442), 400: chr(0x443),
                500: chr(0x444), 600: chr(0x445), 700: chr(0x471), 800: chr(0x461), 900: chr(0x446),
            }

            def slavonic(number):
                # Each decimal place contributes one letter, from the hundreds down to the units.
                letters = []
                for place in (100, 10, 1):
                    digit = (number // place) % 10
                    if digit:
                        letters.append(LETTERS[digit * place])
                return "".join(letters)

            for number in (2, 9, 10, 14, 20, 40, 100, 214, 386):
                print(number, slavonic(number))
        """,
    ),

    entry(
        databaseId = 55,
        kind = Kind.EXAMPLE,
        section = "Chess",
        title = "Squares a queen covers on an empty board in Lucena's chess of 1497",
        originalTitle = "Repeticion de amores y arte de ajedrez",
        author = "Luis Ramirez de Lucena",
        year = 1497,
        summary = "Lucena's chess treatise of 1497 presents the queen's far-reaching move of the modern game, and this example counts the squares a queen reaches from a given square on an empty board.",
        details = "The treatise was printed at Salamanca in 1497, and the record uses that printed book. Accounts differ on whether the queen's new move first appears there or in the poem Scachs d'amor of about 1475.",
        instructions = """
            1. Save the example as queen_squares.py.
            2. Run it: python3 queen_squares.py
        """,
        example = """
            DIRECTIONS = [(1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (1, -1), (-1, 1), (-1, -1)]

            def queen_squares(file, rank):
                # Slide in each of the eight directions to the edge of the board, counting the squares passed.
                count = 0
                for df, dr in DIRECTIONS:
                    f, r = file + df, rank + dr
                    while 0 <= f < 8 and 0 <= r < 8:
                        count += 1
                        f += df
                        r += dr
                return count

            for name, file, rank in [("d4", 3, 3), ("a1", 0, 0)]:
                print(f"a queen on {name} attacks {queen_squares(file, rank)} squares")
        """,
    ),

    entry(
        databaseId = 55,
        kind = Kind.EXAMPLE,
        section = "Hebrew letters",
        title = "Five Hebrew letters that take a final form",
        originalTitle = "De verbo mirifico",
        author = "Johannes Reuchlin",
        year = 1494,
        summary = "Reuchlin's dialogue on the Hebrew names of God set out the letters of the Hebrew alphabet, and five of them take a different form at the end of a word, which this program lists.",
        details = "The letters are kaf, mem, nun, pe and tsadi, and the example names them in transliteration. The list is fixed, so the output can be checked against any Hebrew alphabet.",
        instructions = """
            1. Save the example as hebrew_final.py.
            2. Run it: python3 hebrew_final.py
        """,
        example = """
            # The five Hebrew letters that take a different form at the end of a word, named in transliteration.
            FINAL_LETTERS = ["kaf", "mem", "nun", "pe", "tsadi"]
            for position, name in enumerate(FINAL_LETTERS, start=1):
                print(position, name, "has a final form")
            print("letters with final forms:", len(FINAL_LETTERS))
        """,
    ),

    entry(
        databaseId = 55,
        kind = Kind.EXAMPLE,
        section = "Transliteration",
        title = "Writing three Greek words in Latin letters, letter by letter",
        originalTitle = "Lexicon graecum",
        author = "Suidas, edited by Demetrius Chalcondyles",
        year = 1499,
        summary = "Greek words were sometimes written in Latin letters for readers who did not know the Greek script, and this program transliterates three words letter by letter from a table of letter names.",
        details = "The three words and the table are chosen for the example and are not taken from the Suda, and the output is fixed by the table.",
        instructions = """
            1. Save the example as greek_latin.py.
            2. Run it: python3 greek_latin.py
        """,
        example = """
            LATIN = {"alpha": "a", "epsilon": "e", "theta": "th", "iota": "i", "kappa": "k", "lambda": "l",
                     "mu": "m", "omicron": "o", "phi": "ph", "sigma": "s"}
            WORDS = {
                "theos": ["theta", "epsilon", "omicron", "sigma"],
                "kosmos": ["kappa", "omicron", "sigma", "mu", "omicron", "sigma"],
                "philosophia": ["phi", "iota", "lambda", "omicron", "sigma", "omicron", "phi", "iota", "alpha"],
            }
            for word, letters in WORDS.items():
                spelled = "".join(LATIN[letter] for letter in letters)
                print(f"{word}: {len(letters)} letters, written {spelled}")
        """,
    ),

)
