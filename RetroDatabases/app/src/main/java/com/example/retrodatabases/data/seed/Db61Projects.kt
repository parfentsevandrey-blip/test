package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1430 to 1440: a two-thirds majority counted by nations, abbreviations on an inscription, a scribe's pay by the line and a rota of copyists. */
val db61Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 61,
        kind = Kind.PROJECT,
        section = "Councils",
        title = "Counting a two-thirds majority of nations",
        originalTitle = "Concilium Basiliense",
        author = "The council fathers",
        year = 1431,
        summary = "A motion that needs two thirds of the votes can be tested by a short program that adds the votes of four nations and compares the total with the rule.",
        details = "A council could count votes in different ways, and this example uses an invented count by four nations to test a two-thirds rule. The tallies are made up for the exercise, so the output shows the rule and not any real council's figures.",
        instructions = """
            1. Save the example as two_thirds.py.
            2. Run it: python3 two_thirds.py
        """,
        example = """
            NATIONS = {"France": 9, "Germany": 7, "England": 4, "Italy": 6}   # votes per nation, invented
            first = {"France": 6, "Germany": 5, "England": 2, "Italy": 4}     # yes votes per nation, invented
            second = {"France": 6, "Germany": 6, "England": 3, "Italy": 4}    # yes votes per nation, invented

            total = sum(NATIONS.values())
            # The smallest count that is at least two thirds of the total is the ceiling of 2 * total / 3.
            needed = -(-2 * total // 3)
            print("total votes:", total, "needed for two thirds:", needed)
            for label, tally in (("first motion", first), ("second motion", second)):
                count = sum(tally.values())
                print(label, count, "passes" if count >= needed else "fails")
        """,
    ),

    entry(
        databaseId = 61,
        kind = Kind.PROJECT,
        section = "Inscriptions",
        title = "Expanding the abbreviations of a Roman inscription",
        originalTitle = "Inscriptions copied in Greece and Italy",
        author = "Cyriac of Ancona",
        year = 1435,
        summary = "Abbreviated words on an inscription can be expanded from a table, and a short program expands an invented line and counts how many tokens the table covers.",
        details = "Inscriptions abbreviate common words, and copyists who collected them needed tables to read the abbreviations. The line and the table in the example are invented, and the program leaves any token it does not know unchanged.",
        instructions = """
            1. Save the example as inscription_expand.py.
            2. Run it: python3 inscription_expand.py
        """,
        example = """
            EXPAND = {"IMP": "imperator", "CAES": "caesar", "AVG": "augustus", "COS": "consul", "PP": "pater patriae"}
            LINE = "IMP CAES AVG COS PP ANNO"   # an invented line

            tokens = LINE.split()
            expanded = [EXPAND.get(token, token) for token in tokens]
            print(" ".join(expanded))
            found = sum(1 for token in tokens if token in EXPAND)
            print(f"expanded {found} of {len(tokens)} tokens")
        """,
    ),

    entry(
        databaseId = 61,
        kind = Kind.PROJECT,
        section = "Books",
        title = "Paying a scribe by the line for a dictated book",
        originalTitle = "The Book of Margery Kempe",
        author = "Margery Kempe and her scribes",
        year = 1436,
        summary = "A scribe paid by the line can be paid by a short program that multiplies the lines of each quire by a rate and keeps the total in pence and shillings.",
        details = "Scribes were often paid for the text they copied, and the rate and the number of lines in the example are invented. The program works with whole pence, so the total is fixed and the shillings are found by dividing by twelve.",
        instructions = """
            1. Save the example as scribe_pay.py.
            2. Run it: python3 scribe_pay.py
        """,
        example = """
            LINES = [120, 95, 210]   # lines copied for three quires, invented
            RATE = 3                 # pence per line, invented

            total = 0
            for quire, lines in enumerate(LINES, start=1):
                pay = lines * RATE
                total += pay
                print(f"quire {quire}: {lines} lines, {pay} pence")
            print(f"total: {total} pence, which is {total // 12} shillings and {total % 12} pence")
        """,
    ),

    entry(
        databaseId = 61,
        kind = Kind.PROJECT,
        section = "Chronicles",
        title = "A rota of four copyists for a twelve-part chronicle",
        originalTitle = "Sejong Sillok",
        author = "The royal historians of Joseon Korea",
        year = 1437,
        summary = "Four copyists can share a chronicle of twelve parts by a fixed rota, and a short program prints which copyist takes each part.",
        details = "The rota in the example is invented and shows only a round-robin share of the work, not how the court's historians were really organized. The program assigns the parts in order, so the output is fixed and needs no clock.",
        instructions = """
            1. Save the example as copyist_rota.py.
            2. Run it: python3 copyist_rota.py
        """,
        example = """
            COPYISTS = ["Yi", "Park", "Kim", "Choi"]   # invented names
            PARTS = 12

            for part in range(1, PARTS + 1):
                copyist = COPYISTS[(part - 1) % len(COPYISTS)]
                print(f"part {part:2d}: {copyist}")
        """,
    ),

)
