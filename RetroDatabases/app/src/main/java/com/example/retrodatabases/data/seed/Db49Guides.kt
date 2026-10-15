package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1550 to 1560: keyed shifts, an equals sign, a cipher grille, Tartaglia's rows and a verse address. */
val db49Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 49,
        kind = Kind.GUIDE,
        section = "Ciphers",
        title = "How Bellaso's keyed shifts hide a word",
        originalTitle = "La cifra del Sig. Giovan Battista Bellaso",
        author = "Giovan Battista Bellaso",
        year = 1553,
        summary = "Bellaso's keyed shifts add the letter of a repeating keyword to each plain letter, and a short program enciphers a word with the key CIFRA and reads it back.",
        details = "The method was set out in Bellaso's book of 1553, and the example uses the keyword CIFRA, repeated along the word. The output gives the ciphertext for BELLASO and then the decipherment, which returns the word.",
        instructions = """
            1. Save the example as bellaso_shift.py.
            2. Run it: python3 bellaso_shift.py
        """,
        example = """
            KEY = "CIFRA"

            def shift(text, key, sign):
                # Each letter moves by the value of the key letter beside it: forward to encipher, back to decipher.
                out = ""
                for i, ch in enumerate(text):
                    k = ord(key[i % len(key)]) - ord("A")
                    out += chr((ord(ch) - ord("A") + sign * k) % 26 + ord("A"))
                return out

            cipher = shift("BELLASO", KEY, 1)
            print("cipher:", cipher)
            print("decoded:", shift(cipher, KEY, -1))
        """,
    ),

    entry(
        databaseId = 49,
        kind = Kind.GUIDE,
        section = "Music",
        title = "How 31 equal steps place a fifth and a third",
        originalTitle = "L'antica musica ridotta alla moderna prattica",
        author = "Nicola Vicentino",
        year = 1555,
        summary = "If the octave is divided into 31 equal steps, a fifth takes 18 steps and a major third takes 10, which a short program reads off as exact fractions.",
        details = "Vicentino's treatise of 1555 is linked with a division of the octave into 31 parts, though accounts differ on the number of keys. The example uses that illustrative division and gives the fraction of the octave for each interval, so the output never changes.",
        instructions = """
            1. Save the example as keyboard_steps.py.
            2. Run it: python3 keyboard_steps.py
        """,
        example = """
            from fractions import Fraction as F

            STEPS = 31   # illustrative division of the octave into equal steps
            intervals = {"fifth": 18, "major third": 10, "minor third": 8, "whole tone": 5}
            for name, steps in intervals.items():
                print(f"{name}: {steps} steps, which is {F(steps, STEPS)} of the octave")
        """,
    ),

    entry(
        databaseId = 49,
        kind = Kind.GUIDE,
        section = "Astrolabes",
        title = "How the disc of an astrolabe turns through one hour",
        originalTitle = "London astrolabe",
        author = "Thomas Gemini",
        year = 1559,
        summary = "An astrolabe's disc turns 15 degrees for each hour of the sky, and a short program shows the turn for each quarter of an hour as exact fractions.",
        details = "Gemini's London astrolabe of 1559 follows Gemma Frisius's universal design, and the example uses only the rule that the sky turns 15 degrees in an hour. The angles are exact fractions, so the output never changes.",
        instructions = """
            1. Save the example as astrolabe_hour.py.
            2. Run it: python3 astrolabe_hour.py
        """,
        example = """
            from fractions import Fraction as F

            # the sky turns 15 degrees in an hour, so each minute turns the disc 15/60 of a degree
            for minutes in (0, 15, 30, 45, 60):
                print(f"{minutes} minutes: {F(minutes * 15, 60)} degrees")
        """,
    ),

    entry(
        databaseId = 49,
        kind = Kind.GUIDE,
        section = "Arithmetic",
        title = "How Tartaglia's array builds each row from the one above",
        originalTitle = "General trattato di numeri et misure",
        author = "Niccolo Tartaglia",
        year = 1556,
        summary = "Each row of Tartaglia's array of binomial coefficients is built by adding neighbours from the row above, with a one at each end.",
        details = "The array appears in the second part of Tartaglia's General trattato of 1556, and accounts differ on the priority of similar layouts. The example prints the first six rows by adding neighbours, which needs no multiplication.",
        instructions = """
            1. Save the example as tartaglia_rows.py.
            2. Run it: python3 tartaglia_rows.py
        """,
        example = """
            row = [1]
            for n in range(6):
                print(f"row {n}:", " ".join(str(x) for x in row))
                row = [1] + [row[i] + row[i + 1] for i in range(len(row) - 1)] + [1]
        """,
    ),

    entry(
        databaseId = 49,
        kind = Kind.GUIDE,
        section = "Citation",
        title = "How a chapter and verse address finds a position",
        originalTitle = "Novum Testamentum graece",
        author = "Robert Estienne",
        year = 1551,
        summary = "A chapter and verse address becomes a position in the whole text by counting the verses of the chapters before it, the idea behind Estienne's numbering.",
        details = "Estienne's numbering of 1551 gave every verse a chapter and verse address. The example uses an illustrative book of three chapters with five, three and four verses, and turns four addresses into running positions.",
        instructions = """
            1. Save the example as verse_address.py.
            2. Run it: python3 verse_address.py
        """,
        example = """
            verses = [5, 3, 4]   # verses in chapters 1, 2 and 3 of an illustrative book

            def position(chapter, verse):
                # Count the verses of the chapters before this one, then add the verse number.
                return sum(verses[:chapter - 1]) + verse

            for chapter, verse in [(1, 1), (2, 1), (2, 3), (3, 4)]:
                print(f"chapter {chapter} verse {verse} is verse number {position(chapter, verse)}")
        """,
    ),

)
