package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1450 to 1460: paper for an edition, the letters a founder must cast, a press's week and a scribe's pay by the hundred lines. */
val db59Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 59,
        kind = Kind.PROJECT,
        section = "Printing shop",
        title = "Paper stock for an edition of 200 copies",
        originalTitle = "Edition",
        author = "The printers of Mainz",
        year = 1455,
        summary = "A program multiplies the copies of an edition by the sheets each copy needs, adds two per cent for spoilage and rounds the reams up to whole reams of 500 sheets.",
        details = "The edition, the sheets per copy and the spoilage rate are illustrative figures chosen for the example, and no printed edition of the 1450s is meant. The program prints the sheets for the copies, the spoilage and the reams to order.",
        instructions = """
            1. Save the example as paper_stock.py.
            2. Run it: python3 paper_stock.py
        """,
        example = """
            import math

            COPIES = 200
            SHEETS_PER_COPY = 320
            SPOILAGE_PERCENT = 2

            sheets = COPIES * SHEETS_PER_COPY
            spoilage = sheets * SPOILAGE_PERCENT // 100
            to_print = sheets + spoilage
            print("sheets for the copies:", sheets)
            print("spoilage at 2 per cent:", spoilage)
            print("sheets to print:", to_print)
            print("reams of 500 sheets:", math.ceil(to_print / 500))
        """,
    ),

    entry(
        databaseId = 59,
        kind = Kind.PROJECT,
        section = "Type founding",
        title = "Ordering cast type by the letter counts of a sample line",
        originalTitle = "Sorts",
        author = "The founders of Gutenberg's shop",
        year = 1450,
        summary = "A type founder orders the most-used letters first, and a short program counts the letters of a sample line and lists the five most frequent.",
        details = "Modern accounts of type founding describe letter counts of this kind, though it is not recorded whether the Mainz shop used them. The sample line is an invented phrase built from the names Gutenberg and Donatus, and the program prints the five most frequent letters with their counts.",
        instructions = """
            1. Save the example as type_order.py.
            2. Run it: python3 type_order.py
        """,
        example = """
            from collections import Counter

            sample = "gutenberg printed donatus"
            counts = Counter(ch for ch in sample if ch.isalpha())
            ranked = sorted(counts.items(), key=lambda pair: (-pair[1], pair[0]))
            for letter, times in ranked[:5]:
                print(letter, times)
        """,
    ),

    entry(
        databaseId = 59,
        kind = Kind.PROJECT,
        section = "Printing shop",
        title = "Sheets pulled by one press in a working week",
        originalTitle = "Press work",
        author = "The workshop of a Mainz printer",
        year = 1455,
        summary = "One press pulling sheets at a steady rate gives a weekly total that a short program computes from the pulls per hour, the hours per day and the days worked.",
        details = "The rates in the example are illustrative and are not recorded for any press. A hand press prints one side of a sheet with each pull, so a sheet printed on both sides takes two pulls, and the program prints the pulls and sheets for a day and the total for a six-day week.",
        instructions = """
            1. Save the example as press_week.py.
            2. Run it: python3 press_week.py
        """,
        example = """
            PULLS_PER_HOUR = 100
            HOURS_PER_DAY = 8
            PULLS_PER_SHEET = 2   # one pull for each side of the sheet
            DAYS = 6

            pulls_per_day = PULLS_PER_HOUR * HOURS_PER_DAY
            per_day = pulls_per_day // PULLS_PER_SHEET
            print("pulls per day:", pulls_per_day)
            print("sheets per day:", per_day)
            print("sheets in the week:", per_day * DAYS)
        """,
    ),

    entry(
        databaseId = 59,
        kind = Kind.PROJECT,
        section = "Manuscripts",
        title = "A scribe's pay for copying by the hundred lines",
        originalTitle = "Copying",
        author = "A scribe hired for a book",
        year = 1450,
        summary = "A scribe paid three pence for each hundred lines of copy earns a total that a short program works out for a book of 1200 lines, in pence and in shillings.",
        details = "Scribes were often paid by the line, and the rate and the length of the book in the example are illustrative figures. The program prints the pay in pence and the same sum in shillings, with twelve pence to the shilling.",
        instructions = """
            1. Save the example as scribe_pay.py.
            2. Run it: python3 scribe_pay.py
        """,
        example = """
            LINES = 1200
            PENCE_PER_HUNDRED = 3

            pence = LINES // 100 * PENCE_PER_HUNDRED
            print("pence:", pence)
            print("shillings:", pence // 12)
        """,
    ),

)
