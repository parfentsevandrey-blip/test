package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1510 to 1517: a four-column polyglot page, a Lateran approval queue, a post relay schedule and a sorted press list. */
val db53Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 53,
        kind = Kind.PROJECT,
        section = "Polyglots",
        title = "A page for four columns of a polyglot Bible",
        originalTitle = "Biblia Polyglotta Complutensis",
        author = "Cardinal Francisco Jimenez de Cisneros and his scholars",
        year = 1514,
        summary = "A program lays out a polyglot page with four parallel columns, one language per column, separated by bars, and prints two rows of sample words.",
        details = "The Complutensian Polyglot printed Hebrew, Greek, Latin and Aramaic in parallel columns, and the example builds the same kind of layout from short placeholder words. The words are illustrative and are not a verse of the Complutensian text.",
        instructions = """
            1. Save the example as polyglot_columns.py.
            2. Run it: python3 polyglot_columns.py
        """,
        example = """
            columns = {
                "Hebrew": ["one", "two"],
                "Greek": ["hen", "duo"],
                "Latin": ["unum", "duo"],
                "Aramaic": ["had", "tren"],
            }

            print(" | ".join(columns))
            for row in range(2):
                print(" | ".join(words[row] for words in columns.values()))
        """,
    ),

    entry(
        databaseId = 53,
        kind = Kind.PROJECT,
        section = "Printing",
        title = "A queue of books for prior approval under the Lateran rule",
        originalTitle = "Inter sollicitudines",
        author = "Fifth Lateran Council",
        year = 1515,
        summary = "A program moves book titles through a queue for approval and prints how many were approved and how many were returned.",
        details = "Under the bull of 1515 a book was to be approved before it was printed, so the queue stands for that step. The titles and decisions are invented for the example, and the output counts them.",
        instructions = """
            1. Save the example as lateran_queue.py.
            2. Run it: python3 lateran_queue.py
        """,
        example = """
            queue = [
                ("Psalter gloss", "approved"),
                ("Almanac for 1516", "returned"),
                ("Grammar of Hebrew", "approved"),
                ("Dialogue of the Saints", "returned"),
                ("Book of hours", "approved"),
            ]

            approved = [title for title, decision in queue if decision == "approved"]
            returned = [title for title, decision in queue if decision == "returned"]
            print("approved:", len(approved))
            print("returned:", len(returned))
            print("in order:", "; ".join(approved))
        """,
    ),

    entry(
        databaseId = 53,
        kind = Kind.PROJECT,
        section = "Costing",
        title = "A printer's costing in ducats at a fixed exchange rate",
        originalTitle = "Ducats into florins",
        author = "Printers of the period, as an illustration",
        year = 1516,
        summary = "A program prices three printing materials in ducats, converts each price into florins at a fixed rate, and prints the total in florins.",
        details = "The prices and the rate are invented for the example, which keeps every conversion as an exact fraction. The total in florins is the sum of the converted prices, so the program prints it without rounding.",
        instructions = """
            1. Save the example as costing.py.
            2. Run it: python3 costing.py
        """,
        example = """
            from fractions import Fraction

            FLORINS_PER_DUCAT = Fraction(7, 6)  # a fixed rate chosen for this example

            items = [("paper", 4), ("type", 9), ("ink", 2)]
            total = 0
            for name, ducats in items:
                florins = ducats * FLORINS_PER_DUCAT
                total += florins
                print(f"{name}: {ducats} ducats = {florins} florins")
            print(f"total: {total} florins")
        """,
    ),

    entry(
        databaseId = 53,
        kind = Kind.PROJECT,
        section = "Records",
        title = "Sorting a press list of 1510s books by year, then title",
        originalTitle = "Press list",
        author = "Printers' stock lists of the period",
        year = 1515,
        summary = "A program sorts a press list of books by year and then by title, the order a printer's stock list needs for the shelves.",
        details = "The list in the example is invented, with titles and years from the decade, and the sort uses a key of year and then title. Books of the same year come out in alphabetical order, so the printed list reads in date order.",
        instructions = """
            1. Save the example as press_list.py.
            2. Run it: python3 press_list.py
        """,
        example = """
            books = [
                ("Psalter", 1513), ("Grammar", 1516), ("Almanac", 1518),
                ("Dialogues", 1516), ("Adages", 1515), ("Atlas", 1516),
            ]

            for title, year in sorted(books, key=lambda book: (book[1], book[0])):
                print(year, title)
        """,
    ),

)
