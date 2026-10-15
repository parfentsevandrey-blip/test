package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1510 to 1518: Durer's magic square, Trithemius's progressive shift, the rule of three, the golden number and astrolabe arcs. */
val db53Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 53,
        kind = Kind.GUIDE,
        section = "Magic squares",
        title = "How Durer's magic square of 1514 adds to 34",
        originalTitle = "Melencolia I",
        author = "Albrecht Durer",
        year = 1514,
        summary = "The magic square in Durer's engraving Melencolia I of 1514 has rows, columns and two diagonals that each add to 34, and a short program checks them.",
        details = "Durer engraved the square in the upper right corner of Melencolia I in 1514, and the year is written in the two middle cells of its bottom row. The example uses the numbers as printed and checks the sums of the rows, the columns and the two main diagonals.",
        instructions = """
            1. Save the example as durer_square.py.
            2. Run it: python3 durer_square.py
        """,
        example = """
            square = [
                [16, 3, 2, 13],
                [5, 10, 11, 8],
                [9, 6, 7, 12],
                [4, 15, 14, 1],
            ]

            rows = [sum(row) for row in square]
            columns = [sum(square[r][c] for r in range(4)) for c in range(4)]
            diagonals = [sum(square[i][i] for i in range(4)), sum(square[i][3 - i] for i in range(4))]
            print("rows:", rows)
            print("columns:", columns)
            print("diagonals:", diagonals)
            print("magic:", all(total == 34 for total in rows + columns + diagonals))
        """,
    ),

    entry(
        databaseId = 53,
        kind = Kind.GUIDE,
        section = "Ciphers",
        title = "How Trithemius's progressive shift enciphers a word",
        originalTitle = "Polygraphiae libri sex",
        author = "Johannes Trithemius",
        year = 1518,
        summary = "Trithemius's progressive shift moves each letter of a word further along a 24-sign table, and a short program enciphers a word and decodes it again.",
        details = "The example uses a table of 24 signs, the size of the table in Trithemius's book, and the signs chosen here are illustrative. The first letter moves by nothing, the second by one place, the third by two, and so on, which makes the message hard to read by counting letters.",
        instructions = """
            1. Save the example as shift_word.py.
            2. Run it: python3 shift_word.py
        """,
        example = """
            SIGNS = "ABCDEFGHIKLMNOPQRSTVXYZ&"

            def encipher(text):
                # Each letter moves on by its position in the message, so the shift grows by one for each letter.
                out = []
                for position, letter in enumerate(text):
                    out.append(SIGNS[(SIGNS.index(letter) + position) % len(SIGNS)])
                return "".join(out)

            def decipher(text):
                out = []
                for position, letter in enumerate(text):
                    out.append(SIGNS[(SIGNS.index(letter) - position) % len(SIGNS)])
                return "".join(out)

            plain = "LATIN"
            cipher = encipher(plain)
            print("plain:", plain)
            print("cipher:", cipher)
            print("decoded:", decipher(cipher))
            print("same:", decipher(cipher) == plain)
        """,
    ),

    entry(
        databaseId = 53,
        kind = Kind.GUIDE,
        section = "Letter codes",
        title = "How the Atbash letter code swaps each Hebrew letter",
        originalTitle = "Atbash",
        author = "Hebrew tradition, studied by Christian scholars of the period",
        year = 1516,
        summary = "The Atbash code swaps each letter of the Hebrew alphabet with the letter at the same distance from the other end, and a short program encodes a word and restores it.",
        details = "Atbash is an old Hebrew letter code known from the Jewish tradition, and Christian Hebraists of the period studied it. The alphabet of twenty-two letters pairs first with last and second with second-last, so applying the code twice gives back the word.",
        instructions = """
            1. Save the example as atbash.py.
            2. Run it: python3 atbash.py
        """,
        example = """
            LETTERS = "alef bet gimel dalet he vav zayin het tet yod kaf lamed mem nun samekh ayin pe tsadi qof resh shin tav".split()

            def atbash(name):
                # Each letter is swapped with the letter at the same distance from the end of the alphabet.
                return LETTERS[len(LETTERS) - 1 - LETTERS.index(name)]

            word = ["alef", "bet", "tav", "mem"]
            coded = [atbash(name) for name in word]
            print("word:", " ".join(word))
            print("atbash:", " ".join(coded))
            print("restored:", " ".join(atbash(name) for name in coded))
        """,
    ),

    entry(
        databaseId = 53,
        kind = Kind.GUIDE,
        section = "Time",
        title = "How the astronomical day begins at noon",
        originalTitle = "Astronomical day",
        author = "Astronomers of the period",
        year = 1516,
        summary = "Astronomers reckoned the day from noon, so a civil time between midnight and noon falls in the previous astronomical day, and a short program converts three civil times.",
        details = "Astronomical tables of the 1510s dated their entries from noon, and the custom lasted among astronomers until the change of 1925. The example subtracts twelve hours from each civil time, using fixed dates, so the output never changes.",
        instructions = """
            1. Save the example as astronomical_day.py.
            2. Run it: python3 astronomical_day.py
        """,
        example = """
            from datetime import datetime, timedelta

            civil_times = [
                datetime(1516, 3, 2, 6, 30),
                datetime(1516, 3, 2, 14, 15),
                datetime(1516, 3, 2, 12, 0),
            ]
            for civil in civil_times:
                # The astronomical day starts at noon, so twelve hours are taken off the civil time.
                astronomical = civil - timedelta(hours=12)
                print(f"civil {civil:%Y-%m-%d %H:%M} is astronomical {astronomical:%Y-%m-%d %H:%M}")
        """,
    ),

    entry(
        databaseId = 53,
        kind = Kind.GUIDE,
        section = "Astronomy",
        title = "How base sixty degrees and minutes add in an astrolabe table",
        originalTitle = "Elucidatio fabricae ususque astrolabii",
        author = "Johannes Stoeffler",
        year = 1513,
        summary = "Astrolabe tables count arcs in degrees and minutes, with sixty minutes to a degree, so a sum must carry each full sixty minutes into the degrees.",
        details = "Stoeffler's Elucidatio of 1513 taught how the scales of the astrolabe are read in degrees and minutes of arc. The example adds three pairs of arcs with carrying, using illustrative figures rather than values from the book.",
        instructions = """
            1. Save the example as astrolabe_arcs.py.
            2. Run it: python3 astrolabe_arcs.py
        """,
        example = """
            def add_arcs(a, b):
                # Arcs are written as (degrees, minutes); sixty minutes make one degree, so the minutes carry over.
                minutes = a[1] + b[1]
                degrees = a[0] + b[0] + minutes // 60
                return degrees, minutes % 60

            pairs = [((12, 45), (7, 30)), ((40, 50), (19, 20)), ((0, 59), (0, 2))]
            for a, b in pairs:
                degrees, minutes = add_arcs(a, b)
                print(f"{a[0]} deg {a[1]} min + {b[0]} deg {b[1]} min = {degrees} deg {minutes} min")
        """,
    ),

)
