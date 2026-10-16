package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1362 to 1366: mensural breves, a letter-book index, a pulse count, grouping by century and numbered statute clauses. */
val db68Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 68,
        kind = Kind.GUIDE,
        section = "Notation",
        title = "How a perfect or imperfect breve is divided in mensural notation",
        originalTitle = "Mensural notation",
        author = "Composers of the ars nova, such as Machaut",
        year = 1365,
        summary = "In mensural notation a perfect breve holds three semibreves and an imperfect breve holds two, and a short program adds up the semibreves of a run of breves.",
        details = "The rule of perfection and imperfection decided how many shorter notes a longer note held, and it was the working grammar of the music of Machaut's time. The breves in the example are illustrative and are not taken from any one piece, so the total checks the arithmetic only.",
        instructions = """
            1. Save the example as breve.py.
            2. Run it: python3 breve.py
        """,
        example = """
            # Mensural notation: a perfect breve holds three semibreves, an imperfect breve holds two.
            BREVES = ["perfect", "imperfect", "perfect", "imperfect"]
            SEMIBREVES = {"perfect": 3, "imperfect": 2}
            total = 0
            for number, kind in enumerate(BREVES, start=1):
                count = SEMIBREVES[kind]
                total += count
                print(f"breve {number} {kind}: {count} semibreves")
            print("total semibreves:", total)
        """,
    ),

    entry(
        databaseId = 68,
        kind = Kind.GUIDE,
        section = "Letters",
        title = "How a letter book is indexed by the names of its addressees",
        originalTitle = "Letter index",
        author = "Compilers of medieval letter books",
        year = 1366,
        summary = "A letter book is indexed by listing each addressee once with the numbers of the letters sent to that person, and a short program builds such an index.",
        details = "Petrarch's letters were gathered into books, and an index by addressee is a practical way to find all the letters to one friend. The letter numbers and names in the example are illustrative and do not follow the order of any real collection.",
        instructions = """
            1. Save the example as letters.py.
            2. Run it: python3 letters.py
        """,
        example = """
            # Illustrative letter numbers and addressees, not the order of any real collection.
            LETTERS = [("Boccaccio", 1), ("Colonna", 2), ("Boccaccio", 3), ("Dionigi", 4), ("Colonna", 5), ("Boccaccio", 6)]
            index = {}
            for addressee, number in LETTERS:
                index.setdefault(addressee, []).append(number)
            for name in sorted(index):
                print(name, index[name])
            print("addressees:", len(index))
        """,
    ),

    entry(
        databaseId = 68,
        kind = Kind.GUIDE,
        section = "Medicine",
        title = "How a pulse is counted in beats per minute with a sand-glass",
        originalTitle = "Pulse count",
        author = "No single author",
        year = 1363,
        summary = "The beats counted over one run of a sand-glass are turned into beats per minute, and a short program averages three runs.",
        details = "The method is a plain count of beats over a fixed time, scaled to sixty seconds. The thirty-second glass and the beat counts in the example are illustrative, and the year 1363 is the date of Guy de Chauliac's surgical handbook, which is cited for the date only.",
        instructions = """
            1. Save the example as pulse.py.
            2. Run it: python3 pulse.py
        """,
        example = """
            # A glass that runs for thirty seconds; the beats counted in one run are scaled to a minute.
            GLASS_SECONDS = 30
            BEATS = [17, 19, 18]   # illustrative beat counts for three runs of the glass
            for run, beats in enumerate(BEATS, start=1):
                per_minute = beats * 60 // GLASS_SECONDS
                print(f"run {run}: {beats} beats, {per_minute} per minute")
            average = sum(BEATS) * 60 // (GLASS_SECONDS * len(BEATS))
            print("average per minute:", average)
        """,
    ),

    entry(
        databaseId = 68,
        kind = Kind.GUIDE,
        section = "Biographies",
        title = "How a list of biographies is grouped by century",
        originalTitle = "Grouping by century",
        author = "Compilers of biographical books",
        year = 1362,
        summary = "A list of biographies is grouped by the century of each subject's birth, and a short program counts the lives in each century.",
        details = "Boccaccio's book of lives covers subjects from many centuries, so a grouping by century shows how a collection spreads through time. The birth years in the example are illustrative and do not describe the subjects of Boccaccio's book.",
        instructions = """
            1. Save the example as centuries.py.
            2. Run it: python3 centuries.py
        """,
        example = """
            # Illustrative birth years of six biographies, not the subjects of any real book.
            BIRTH_YEARS = [1100, 1234, 1199, 1305, 1280, 1050]
            groups = {}
            for year in BIRTH_YEARS:
                century = (year - 1) // 100 + 1
                groups.setdefault(century, []).append(year)
            for century in sorted(groups):
                print(f"century {century}: {len(groups[century])}")
            print("centuries used:", len(groups))
        """,
    ),

    entry(
        databaseId = 68,
        kind = Kind.GUIDE,
        section = "Statutes",
        title = "How the clauses of a statute are numbered by chapter and clause",
        originalTitle = "Clause numbering",
        author = "Draftsmen of statutes",
        year = 1366,
        summary = "The clauses of a statute are numbered by chapter and then by clause within the chapter, and a short program lists the labels and counts the clauses in each chapter.",
        details = "A long statute needs labels so that a reader can find a point again, and the chapter and clause pair is one simple scheme. The clause list in the example is illustrative and does not reproduce the text of the Statutes of Kilkenny.",
        instructions = """
            1. Save the example as clauses.py.
            2. Run it: python3 clauses.py
        """,
        example = """
            # Illustrative clauses, each given as a chapter number and a clause number within the chapter.
            CLAUSES = [(1, 1), (1, 2), (1, 3), (2, 1), (2, 2), (3, 1)]
            labels = [f"{chapter}.{clause}" for chapter, clause in CLAUSES]
            print("clauses:", " ".join(labels))
            print("count:", len(labels))
            for chapter in sorted({chapter for chapter, _ in CLAUSES}):
                count = sum(1 for number, _ in CLAUSES if number == chapter)
                print(f"chapter {chapter} has {count} clauses")
        """,
    ),

)
