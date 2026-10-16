package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1440 to 1450: Hangul syllable blocks, a sixty-year cycle, a balance's levers, organ letters and a lectern grid, each with a short program. */
val db60Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 60,
        kind = Kind.GUIDE,
        section = "Hangul",
        title = "How Hangul builds a syllable block from its letters",
        originalTitle = "Hunminjeongeum",
        author = "King Sejong and his scholars",
        year = 1446,
        summary = "A Korean syllable block is built from an initial, a vowel and an optional final letter, and a short program turns those three numbers into a code point.",
        details = "The letters of the Hunminjeongeum of 1446 combine in a fixed order, so every syllable block has its place in one numbered run. The example prints four blocks from their letter numbers and the total number of blocks the letters allow, using exact integer arithmetic.",
        instructions = """
            1. Save the example as hangul_blocks.py.
            2. Run it: python3 hangul_blocks.py
        """,
        example = """
            BASE = 0xAC00            # the first syllable block, ga
            INITIALS = 19
            MEDIALS = 21
            FINALS = 28

            def block(initial, medial, final):
                # The blocks run in order: each initial has 21 vowel groups, and each vowel group has 28 final options.
                return BASE + (initial * MEDIALS + medial) * FINALS + final

            samples = [("ga", 0, 0, 0), ("geul", 0, 18, 8), ("han", 18, 0, 4), ("se", 9, 5, 0)]
            for name, initial, medial, final in samples:
                print(f"{name} U+{block(initial, medial, final):04X}")
            print("syllable blocks:", INITIALS * MEDIALS * FINALS)
        """,
    ),

    entry(
        databaseId = 60,
        kind = Kind.GUIDE,
        section = "Calendars",
        title = "How the sixty-year cycle names a year",
        originalTitle = "Chiljeongsan",
        author = "Sejong's astronomers",
        year = 1444,
        summary = "The sixty-year cycle names each year by a stem and a branch, and a short program computes the name of a year from its number.",
        details = "The Chiljeongsan of 1444 used the sixty-year cycle of stems and branches to name years. The example counts from 4 CE, which is a jiazi year, and prints the names of three years in the 1440s and 1450s.",
        instructions = """
            1. Save the example as cycle_names.py.
            2. Run it: python3 cycle_names.py
        """,
        example = """
            STEMS = ["jia", "yi", "bing", "ding", "wu", "ji", "geng", "xin", "ren", "gui"]
            BRANCHES = ["zi", "chou", "yin", "mao", "chen", "si", "wu", "wei", "shen", "you", "xu", "hai"]

            def sexagenary(year):
                # Year 4 is a jiazi year, so the cycle is counted from it, sixty years at a time.
                index = (year - 4) % 60
                return STEMS[index % 10] + BRANCHES[index % 12]

            for year in (1444, 1446, 1450):
                print(year, sexagenary(year))
        """,
    ),

    entry(
        databaseId = 60,
        kind = Kind.GUIDE,
        section = "Weighing",
        title = "How a balance's arms weigh an unknown load",
        originalTitle = "Idiota de staticis experimentis",
        author = "Nicholas of Cusa",
        year = 1450,
        summary = "A level balance holds when weight times arm length is equal on both sides, so one missing weight follows from the other three numbers.",
        details = "Nicholas of Cusa's balance book of 1450 treated the balance as an instrument for experiments. The example applies the lever rule with exact fractions to find the balancing weight for three illustrative pairs of arms, and its numbers are not the book's own.",
        instructions = """
            1. Save the example as lever_balance.py.
            2. Run it: python3 lever_balance.py
        """,
        example = """
            from fractions import Fraction as F

            def balancing_weight(known_weight, known_arm, other_arm):
                # A level beam balances when weight times arm length is the same on both sides.
                return F(known_weight * known_arm, other_arm)

            cases = ((3, 8, 6), (5, 4, 10), (7, 9, 12))
            for known, known_arm, other_arm in cases:
                weight = balancing_weight(known, known_arm, other_arm)
                print(f"known {known} at arm {known_arm}: balancing weight at arm {other_arm} is {weight}")
        """,
        rare = true,
    ),

    entry(
        databaseId = 60,
        kind = Kind.GUIDE,
        section = "Organ music",
        title = "How a letter names a key in organ tablature",
        originalTitle = "Organ tablature by Adam Ileborgh",
        author = "Adam Ileborgh",
        year = 1448,
        summary = "In letter tablature each letter names a key, and a short program turns a letter and an octave into a count of semitones above the note a.",
        details = "Adam Ileborgh's tablature of 1448 uses letters to name the keys of the organ. The example uses a simplified mapping of letters to semitone offsets, which is an illustration and not a rule taken from the tablature itself.",
        instructions = """
            1. Save the example as letter_keys.py.
            2. Run it: python3 letter_keys.py
        """,
        example = """
            # Simplified offsets of the letters above the note a, in semitones; an illustration only.
            OFFSETS = {"a": 0, "b": 2, "c": 3, "d": 5, "e": 7, "f": 8, "g": 10}

            def semitones_above_a(letter, octave):
                # Each octave adds twelve semitones to the offset of the letter.
                return OFFSETS[letter] + 12 * octave

            for letter, octave in (("a", 0), ("c", 1), ("g", 1), ("f", 2)):
                print(f"{letter}{octave}: {semitones_above_a(letter, octave)} semitones above a0")
        """,
    ),

    entry(
        databaseId = 60,
        kind = Kind.GUIDE,
        section = "Libraries",
        title = "How a lectern hall numbers its places",
        originalTitle = "Biblioteca di San Marco",
        author = "Michelozzo, for Cosimo de' Medici",
        year = 1444,
        summary = "Places in a rectangular hall can be numbered row by row, so a row and a column give one number, and a short program computes that number.",
        details = "The library of San Marco opened in 1444 with its books on desks. The example uses an illustrative hall of four rows and six places to show row-by-row numbering, which is a method and not the library's own layout.",
        instructions = """
            1. Save the example as lectern_places.py.
            2. Run it: python3 lectern_places.py
        """,
        example = """
            ROWS = 4
            COLUMNS = 6

            def place_number(row, column):
                # Places are numbered from one, row by row, so the first place of a row follows the last place of the row before.
                return row * COLUMNS + column + 1

            for row, column in ((0, 0), (1, 5), (3, 2)):
                print(f"row {row + 1}, column {column + 1}: place {place_number(row, column)}")
            print("places in the hall:", ROWS * COLUMNS)
        """,
    ),

)
