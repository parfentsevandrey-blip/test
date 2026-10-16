package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1460 to 1470: a table of first dated books, an illustrative shelf list, an illustrative faculty budget and a gauge table for casks. */
val db58Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 58,
        kind = Kind.PROJECT,
        section = "Printing",
        title = "Gaps between the first dated books of early presses",
        originalTitle = "First dated books of the early presses",
        author = "Early presses of Mainz, Bamberg, Subiaco, Cologne, Eltville and Paris",
        year = 1470,
        summary = "A short program sorts six early books by year and prints the gap in years between each book and the one before it.",
        details = "The six books are the Catholicon of 1460, the Edelstein of 1461, the Subiaco Lactantius of 1465, Zell's first dated book of 1466, the Eltville word list of 1467 and the Paris letters of 1470. The dates are fixed in the program, so the output does not change from run to run.",
        instructions = """
            1. Save the example as first_books.py.
            2. Run it: python3 first_books.py
        """,
        example = """
            books = [
                (1460, "Mainz", "Catholicon"),
                (1461, "Bamberg", "Edelstein"),
                (1465, "Subiaco", "Lactantius"),
                (1466, "Cologne", "Zell's first dated book"),
                (1467, "Eltville", "Vocabularius ex quo"),
                (1470, "Paris", "Gasparinus's letters"),
            ]
            previous = None
            for year, town, title in sorted(books):
                gap = "first" if previous is None else str(year - previous)
                print(f"{year} {town}: {title} (years since previous: {gap})")
                previous = year
        """,
    ),

    entry(
        databaseId = 58,
        kind = Kind.PROJECT,
        section = "Libraries",
        title = "Shelving Bessarion's Greek books by century",
        originalTitle = "Donatio Bessarionis",
        author = "Cardinal Basilios Bessarion",
        year = 1468,
        summary = "A small program counts an illustrative shelf list of Greek books by century and prints each shelf with its count.",
        details = "The shelf list is made up for the example and is not Bessarion's own catalogue, so the counts show only how the program works. Counting by century uses a dictionary of counts, and the shelves print in century order.",
        instructions = """
            1. Save the example as shelf_count.py.
            2. Run it: python3 shelf_count.py
        """,
        example = """
            from collections import Counter

            # An illustrative shelf list made up for this example: each number is the century of one Greek book.
            centuries = [13, 14, 15, 14, 13, 15, 15, 12, 14, 15]
            counts = Counter(centuries)
            for century in sorted(counts):
                print(f"century {century}: {counts[century]}")
            print(f"total entries: {len(centuries)}")
        """,
    ),

    entry(
        databaseId = 58,
        kind = Kind.PROJECT,
        section = "Universities",
        title = "A budget split among Basel's four faculties",
        originalTitle = "Universitas studii Basiliensis",
        author = "Pope Pius II, by the founding bull of 1459",
        year = 1460,
        summary = "A total budget is split among four faculties in proportion to illustrative shares, and the program prints each faculty's sum in florins.",
        details = "The shares are illustrative numbers chosen for the example, not the figures of any university account. The program uses integer arithmetic, so each share comes out as a whole number of florins.",
        instructions = """
            1. Save the example as faculty_budget.py.
            2. Run it: python3 faculty_budget.py
        """,
        example = """
            # Illustrative shares for four faculties, not the figures of any real university account.
            shares = {"arts": 4, "theology": 3, "law": 2, "medicine": 1}
            TOTAL_FLORINS = 120

            total_shares = sum(shares.values())
            for faculty, share in shares.items():
                print(f"{faculty}: {TOTAL_FLORINS * share // total_shares} florins")
            print(f"total: {TOTAL_FLORINS} florins")
        """,
    ),

    entry(
        databaseId = 58,
        kind = Kind.PROJECT,
        section = "Casks",
        title = "A gauge table for cylindrical casks",
        originalTitle = "Cologne wine casks, illustrative",
        author = "Cologne merchants, illustrative",
        year = 1466,
        summary = "A gauge table computes the volume of cylindrical casks from length and diameter, using pi as 22 over 7 so that every volume is an exact fraction.",
        details = "The casks in the example are illustrative sizes, and the volume rule is the plain cylinder formula with pi set to 22 over 7. Each volume comes out as a whole number here, so the output needs no rounding.",
        instructions = """
            1. Save the example as cask_gauge.py.
            2. Run it: python3 cask_gauge.py
        """,
        example = """
            from fractions import Fraction as F

            PI = F(22, 7)   # pi as 22 over 7, kept exact

            def cask_volume(length, diameter):
                # Volume of a cylinder: pi times the square of the radius times the length.
                radius = F(diameter, 2)
                return PI * radius * radius * length

            casks = [(7, 4), (14, 6), (21, 2)]
            for length, diameter in casks:
                print(f"cask of length {length} and diameter {diameter}: volume {cask_volume(length, diameter)} cubic units")
        """,
    ),

)
