package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1340 to 1348: the rhymes of an ottava rima, an estate in thirds, a lance of 1346, the quartered arms of 1340 and a Caesar shift of the Garter motto. */
val db70Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 70,
        kind = Kind.GUIDE,
        section = "Verse",
        title = "How the eight lines of an ottava rima stanza rhyme",
        originalTitle = "Ottava rima",
        author = "Giovanni Boccaccio, who used the stanza in the Teseida",
        year = 1341,
        summary = "An ottava rima stanza has eight lines that rhyme ABABABCC, and a short program reads the line endings of a stanza and names its rhyme scheme.",
        details = "Boccaccio used the stanza first in the shorter Filostrato and then in the Teseida of about 1341, an epic. The program uses illustrative line endings, not the endings of a real stanza, and it counts the rhyme sounds so the result can be checked by hand.",
        instructions = """
            1. Save the example as ottava.py.
            2. Run it: python3 ottava.py
        """,
        example = """
            ENDINGS = ["ore", "ano", "ore", "ano", "ore", "ano", "ame", "ame"]   # illustrative line endings
            letters = {}
            scheme = ""
            for ending in ENDINGS:
                if ending not in letters:
                    letters[ending] = chr(ord("A") + len(letters))
                scheme += letters[ending]
            print("lines:", len(ENDINGS))
            print("rhyme sounds:", len(letters))
            print("rhyme scheme:", scheme)
        """,
    ),

    entry(
        databaseId = 70,
        kind = Kind.GUIDE,
        section = "Inheritance",
        title = "How an estate divides into three shares in an illustrative will of 1348",
        originalTitle = "Division into thirds",
        author = "Testators and their executors",
        year = 1348,
        summary = "An estate is divided into three shares in one illustrative will, and a short program gives the first two shares equal parts and leaves the remainder to the last share.",
        details = "Medieval wills in several regions divided goods by fixed shares, and the rule of thirds is one of the customs historians have described. The estate in the example is illustrative, and the program keeps the remainder so that the shares add up to the whole.",
        instructions = """
            1. Save the example as estate.py.
            2. Run it: python3 estate.py
        """,
        example = """
            ESTATE = 1000   # illustrative estate in units of account
            widow = ESTATE // 3
            children = ESTATE // 3
            soul = ESTATE - widow - children
            print("widow:", widow)
            print("children:", children)
            print("for the soul:", soul)
            print("total:", widow + children + soul)
        """,
    ),

    entry(
        databaseId = 70,
        kind = Kind.GUIDE,
        section = "Armies",
        title = "How a lance of 1346 is counted: one man-at-arms, two archers and a page",
        originalTitle = "Lance",
        author = "English commanders of the Crecy campaign",
        year = 1346,
        summary = "The lance was the basic unit of a feudal army, and a short program counts the men of an illustrative number of lances from their usual make-up.",
        details = "A lance in the English army of the 1340s was usually one man-at-arms with two archers and a page, though the mix varied from army to army. The program uses an illustrative number of lances, so the totals can be checked by hand.",
        instructions = """
            1. Save the example as lances.py.
            2. Run it: python3 lances.py
        """,
        example = """
            LANCES = 12              # illustrative number of lances
            ARCHERS_PER_LANCE = 2    # usual make-up of an English lance
            PAGES_PER_LANCE = 1
            men_at_arms = LANCES
            archers = LANCES * ARCHERS_PER_LANCE
            pages = LANCES * PAGES_PER_LANCE
            print("lances:", LANCES)
            print("archers:", archers)
            print("pages:", pages)
            print("men in all:", men_at_arms + archers + pages)
        """,
    ),

    entry(
        databaseId = 70,
        kind = Kind.GUIDE,
        section = "Heraldry",
        title = "How the royal arms of England were quartered in 1340",
        originalTitle = "Quartering",
        author = "Edward III of England",
        year = 1340,
        summary = "In 1340 Edward III quartered the arms of France and England on one shield, and a short program lays out the four fields in their order.",
        details = "The quarters put France in the first and fourth fields and England in the second and third, and the program prints that order. The year 1340 is the one usually given for the change, and the program checks the count of fields so the result can be read by hand.",
        instructions = """
            1. Save the example as quarters.py.
            2. Run it: python3 quarters.py
        """,
        example = """
            FIELDS = ["France", "England", "England", "France"]   # the four quarters, read from the top left
            print("fields:", len(FIELDS))
            for number, arms in enumerate(FIELDS, start=1):
                print("quarter", number, arms)
        """,
    ),

    entry(
        databaseId = 70,
        kind = Kind.GUIDE,
        section = "Ciphers",
        title = "How a Caesar shift turns the Garter's motto into a cipher",
        originalTitle = "Caesar cipher",
        author = "Roman writers, named after Julius Caesar",
        year = 1348,
        summary = "A Caesar shift moves every letter of a message a fixed number of places along the alphabet, and a short program applies a shift of three to the motto of the Garter.",
        details = "The motto Honi soit qui mal y pense is traditionally linked with the Garter founded in 1348, and the shift of three is an illustrative choice. The program keeps the spaces and changes only capital letters, so the output can be checked letter by letter.",
        instructions = """
            1. Save the example as caesar.py.
            2. Run it: python3 caesar.py
        """,
        example = """
            MOTTO = "HONI SOIT QUI MAL Y PENSE"   # traditionally linked with the Garter of 1348
            SHIFT = 3
            text = ""
            for ch in MOTTO:
                if "A" <= ch <= "Z":
                    text += chr((ord(ch) - ord("A") + SHIFT) % 26 + ord("A"))
                else:
                    text += ch
            print("shift", SHIFT)
            print(text)
        """,
    ),

)
