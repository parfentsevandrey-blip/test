package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1400 to 1410: the Tibetan consonant order, the canonical hours, a Welsh soft mutation, a verge escapement and a check for duplicate years. */
val db64Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 64,
        kind = Kind.GUIDE,
        section = "Writing",
        title = "How the thirty Tibetan consonants are numbered in alphabetical order",
        originalTitle = "Tibetan alphabet",
        author = "Tibetan grammarians",
        year = 1402,
        summary = "The thirty Tibetan consonants follow one fixed alphabetical order, the order that Tibetan dictionaries use, and a short program gives each letter its place in that order.",
        details = "The order runs from ka to a, and the example uses romanized names so the output stays in plain ASCII. The year 1402 is the completion date of Tsongkhapa's Lam rim chen mo, which is written in the same script, and the example checks the places of three letters.",
        instructions = """
            1. Save the example as tibetan_order.py.
            2. Run it: python3 tibetan_order.py
        """,
        example = """
            ALPHABET = ["ka", "kha", "ga", "nga", "ca", "cha", "ja", "nya", "ta", "tha",
                        "da", "na", "pa", "pha", "ba", "ma", "tsa", "tsha", "dza", "wa",
                        "zha", "za", "a'", "ya", "ra", "la", "sha", "sa", "ha", "a"]
            for name in ["ka", "pa", "a"]:
                print(name, ALPHABET.index(name) + 1)
            print("letters:", len(ALPHABET))
        """,
    ),

    entry(
        databaseId = 64,
        kind = Kind.GUIDE,
        section = "Hours",
        title = "How the eight canonical hours divide the day",
        originalTitle = "Canonical hours",
        author = "The Latin Church",
        year = 1400,
        summary = "The eight canonical hours divide the church day into prayers from matins to compline, and a short program lists them in order with their places in the day.",
        details = "The hours are matins, lauds, prime, terce, sext, none, vespers and compline, and each one gives a set of prayers in the Book of Hours. The example prints the names in order and makes no claim about clock times.",
        instructions = """
            1. Save the example as hours.py.
            2. Run it: python3 hours.py
        """,
        example = """
            HOURS = ["matins", "lauds", "prime", "terce", "sext", "none", "vespers", "compline"]
            for position, name in enumerate(HOURS, start=1):
                print(position, name)
            print("hours in the day:", len(HOURS))
        """,
    ),

    entry(
        databaseId = 64,
        kind = Kind.GUIDE,
        section = "Language",
        title = "How a Welsh word changes its first letter by soft mutation",
        originalTitle = "Soft mutation",
        author = "Welsh grammarians",
        year = 1400,
        summary = "Welsh words change their first letter in set patterns called mutations, and a short program applies soft mutation to four words with a table of letters.",
        details = "Soft mutation changes p, t, c and m to b, d, g and f, and the change follows grammar rather than spelling. The example covers only these four letters, so words beginning with other letters are left as they are.",
        instructions = """
            1. Save the example as mutation.py.
            2. Run it: python3 mutation.py
        """,
        example = """
            SOFT = {"p": "b", "t": "d", "c": "g", "m": "f"}

            def soft_mutation(word):
                # Soft mutation changes the first letter for these letters only; other words are returned unchanged.
                return SOFT.get(word[0], word[0]) + word[1:]

            for word in ["pen", "tad", "cath", "mam"]:
                print(word, "becomes", soft_mutation(word))
        """,
    ),

    entry(
        databaseId = 64,
        kind = Kind.GUIDE,
        section = "Clocks",
        title = "How a verge escapement releases the going train in steps",
        originalTitle = "Verge escapement",
        author = "Makers of church clocks",
        year = 1400,
        summary = "A verge escapement lets the drive wheel advance one tooth at each swing of the foliot, and a short program counts the teeth released in a set number of swings.",
        details = "The escapement releases one tooth for each swing, so the count of teeth follows the count of swings. The example uses a crown wheel of 30 teeth and one second per swing, both chosen for illustration rather than taken from a real clock.",
        instructions = """
            1. Save the example as verge.py.
            2. Run it: python3 verge.py
        """,
        example = """
            TEETH = 30              # crown wheel teeth, illustrative
            SECONDS_PER_SWING = 1   # one swing of the foliot, illustrative

            for swing in range(1, 9):
                print(f"swing {swing}: wheel at tooth {swing % TEETH}")
            print(f"one full turn takes {TEETH * SECONDS_PER_SWING} seconds")
        """,
    ),

    entry(
        databaseId = 64,
        kind = Kind.GUIDE,
        section = "Lists",
        title = "How a list of years is checked for duplicates",
        originalTitle = "Duplicate check",
        author = "No single author",
        year = 1410,
        summary = "A list of dated events is checked for duplicates by keeping the years already seen, and a short program reports the years that occur twice.",
        details = "The list in the example holds years of events from the decade, with two years repeated, and the program prints each repeated year once. The output is fixed, so it can be checked by hand.",
        instructions = """
            1. Save the example as duplicates.py.
            2. Run it: python3 duplicates.py
        """,
        example = """
            events = [1400, 1403, 1405, 1403, 1409, 1405, 1410]   # years of dated events, with repeats
            seen = set()
            repeated = []
            for year in events:
                if year in seen and year not in repeated:
                    repeated.append(year)
                seen.add(year)
            print("repeated years:", repeated)
        """,
    ),

)
