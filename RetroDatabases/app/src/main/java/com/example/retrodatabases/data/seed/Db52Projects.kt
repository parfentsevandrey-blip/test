package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1520 to 1530: a Hebrew root index, the New Testament's chapters, a hymnal index and a tithe ledger for three parishes. */
val db52Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 52,
        kind = Kind.PROJECT,
        section = "Hebrew",
        title = "A root index of Hebrew words under their roots",
        originalTitle = "Root index of transliterated Hebrew words",
        author = "Santes Pagninus, after the root method",
        year = 1529,
        summary = "A root index groups Hebrew words under their three-letter roots, as Pagninus's Thesaurus does, and a short program prints the groups in order of root.",
        details = "Pagninus arranged his lexicon by root, so that a reader could find the words of one family together. The example indexes six transliterated words with invented data, to show the grouping, and prints the roots in alphabetical order.",
        instructions = """
            1. Save the example as root_index.py.
            2. Run it: python3 root_index.py
        """,
        example = """
            WORDS = [("melekh", "MLK"), ("dabar", "DBR"), ("shalom", "SLM"),
                     ("malkah", "MLK"), ("davar", "DBR"), ("shelem", "SLM")]

            index = {}
            for word, root in WORDS:
                index.setdefault(root, []).append(word)   # group each word under its three-letter root

            for root in sorted(index):
                print(f"{root}: {', '.join(index[root])}")
        """,
    ),

    entry(
        databaseId = 52,
        kind = Kind.PROJECT,
        section = "Scripture",
        title = "The New Testament's chapters, sorted by book",
        originalTitle = "The newe Testament",
        author = "Chapter divisions of the New Testament",
        year = 1526,
        summary = "The New Testament has 27 books and 260 chapters, and a short program totals the chapters and lists the three longest books.",
        details = "The counts are those of the familiar chapter division of the New Testament, which goes back to the Middle Ages. The example lists the chapters of each book, adds them up and sorts the books by length, with equal lengths taken in alphabetical order.",
        instructions = """
            1. Save the example as nt_chapters.py.
            2. Run it: python3 nt_chapters.py
        """,
        example = """
            BOOKS = [("Matthew", 28), ("Mark", 16), ("Luke", 24), ("John", 21), ("Acts", 28),
                     ("Romans", 16), ("1 Corinthians", 16), ("2 Corinthians", 13), ("Galatians", 6),
                     ("Ephesians", 6), ("Philippians", 4), ("Colossians", 4), ("1 Thessalonians", 5),
                     ("2 Thessalonians", 3), ("1 Timothy", 6), ("2 Timothy", 4), ("Titus", 3),
                     ("Philemon", 1), ("Hebrews", 13), ("James", 5), ("1 Peter", 5), ("2 Peter", 3),
                     ("1 John", 5), ("2 John", 1), ("3 John", 1), ("Jude", 1), ("Revelation", 22)]

            print("books:", len(BOOKS))
            print("chapters:", sum(chapters for _, chapters in BOOKS))
            for name, chapters in sorted(BOOKS, key=lambda book: (-book[1], book[0]))[:3]:
                print(f"{name} {chapters}")
        """,
    ),

    entry(
        databaseId = 52,
        kind = Kind.PROJECT,
        section = "Hymns",
        title = "A hymnal index sorted by first line",
        originalTitle = "Index of hymns by first line",
        author = "Illustrative entries for a hymnal index",
        year = 1524,
        summary = "A hymnal index lists each hymn by its first line in alphabetical order with its page, and a short program builds one from four entries.",
        details = "Printed hymnals of the 1520s gave the first line of each hymn with its place in the book, so that a singer could find it quickly. The example uses four entries with invented page numbers to show the sorting, and it ignores case when it sorts the lines.",
        instructions = """
            1. Save the example as hymnal_index.py.
            2. Run it: python3 hymnal_index.py
        """,
        example = """
            hymns = [
                ("Nun freut euch lieben Christen", 2),
                ("Mit Fried und Freud ich fahr dahin", 1),
                ("Komm Gott Schoepfer Heiliger Geist", 3),
                ("Aus tiefer Not schrei ich zu dir", 4),
            ]

            for first_line, page in sorted(hymns, key=lambda hymn: hymn[0].lower()):   # the sort ignores case
                print(f"{first_line}: page {page}")
        """,
    ),

    entry(
        databaseId = 52,
        kind = Kind.PROJECT,
        section = "Tithes",
        title = "A grain tithe ledger for three parishes",
        originalTitle = "A grain tithe ledger for three parishes",
        author = "Parish accounts, with illustrative harvests",
        year = 1525,
        summary = "A tithe of one tenth of a harvest is owed to the church, and a short program keeps the tithe ledger of three parishes in exact fractions.",
        details = "The tithe was a grievance in the German Peasants' War of 1525, when the Twelve Articles asked for changes in how the tithe was paid. The example uses illustrative harvests in bushels, not real parish records, and keeps each tithe as an exact fraction.",
        instructions = """
            1. Save the example as tithe_ledger.py.
            2. Run it: python3 tithe_ledger.py
        """,
        example = """
            from fractions import Fraction as F

            harvest = {"parish A": 125, "parish B": 340, "parish C": 77}   # bushels of grain harvested
            total = F(0)
            for parish, bushels in harvest.items():
                tithe = F(bushels, 10)          # the tenth part of the harvest is owed to the church
                total += tithe
                print(f"{parish}: tithe {tithe} bushels")
            print(f"total tithe: {total} bushels")
        """,
    ),

)
