package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1285 to 1290: a debt register under the Statute of Merchants, a note ledger for Yuan paper money, a tally of chained and loan books and the fines of an illustrative Vinodol court. */
val db76Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 76,
        kind = Kind.PROJECT,
        section = "Credit",
        title = "A debt register under the Statute of Merchants, with the overdue sum",
        originalTitle = "Recognizances",
        author = "Creditors and clerks of the statute",
        year = 1285,
        summary = "A register of debts with due days and amounts is totalled by a short program, which prints the sum owed and the sum overdue on a fixed day.",
        details = "The debtors, amounts and due days are illustrative and are not taken from any real register. The overdue test is made on a fixed day, so the output is the same on every run.",
        instructions = """
            1. Save the example as debt_register.py.
            2. Run it: python3 debt_register.py
        """,
        example = """
            DEBTS = [
                ("Ralph", 40, 10),      # (debtor, pounds owed, day the debt falls due), illustrative
                ("Matilda", 25, 35),
                ("Osbert", 60, 20),
                ("Alice", 15, 50),
            ]
            TODAY = 30              # the day of the register, fixed for the example
            overdue = [d for d in DEBTS if d[2] < TODAY]
            for name, amount, due in sorted(DEBTS, key=lambda d: d[2]):
                state = "overdue" if due < TODAY else "not due"
                print(f"{name} due day {due}: {amount} pounds, {state}")
            print("owed in all:", sum(d[1] for d in DEBTS), "pounds")
            print("overdue on day", TODAY, ":", sum(d[1] for d in overdue), "pounds from", len(overdue), "debts")
        """,
    ),

    entry(
        databaseId = 76,
        kind = Kind.PROJECT,
        section = "Money",
        title = "A ledger of Zhiyuan notes issued and burned, with the balance",
        originalTitle = "Paper note ledger",
        author = "Clerks of a Yuan paper-money office",
        year = 1287,
        summary = "A ledger of paper notes issued and burned is run through a short program that prints the balance after each entry and the largest balance reached.",
        details = "The amounts are illustrative and do not record the real issues of 1287, and the running balance can be checked by hand line by line. The largest balance, reached after the fourth entry, is printed at the end.",
        instructions = """
            1. Save the example as note_ledger.py.
            2. Run it: python3 note_ledger.py
        """,
        example = """
            ENTRIES = [("issue", 500), ("issue", 300), ("burn", 120), ("issue", 250), ("burn", 400)]   # illustrative notes
            balance = 0
            largest = 0
            for number, (kind, amount) in enumerate(ENTRIES, start=1):
                if kind == "issue":
                    balance += amount
                else:
                    balance -= amount
                largest = max(largest, balance)
                print(f"entry {number}: {kind} {amount}, balance {balance}")
            print("largest balance:", largest)
            print("notes in circulation:", balance)
        """,
    ),

    entry(
        databaseId = 76,
        kind = Kind.PROJECT,
        section = "Libraries",
        title = "Chained and loan copies tallied by rank of authority",
        originalTitle = "Chained copies",
        author = "No single author",
        year = 1290,
        summary = "A list of books, each flagged as chained or not, is tallied by four ranks of authority, and a short program prints the count and the chained copies in each rank.",
        details = "The books and flags are illustrative and are not the holdings of the Sorbonne, while the four ranks follow the order that the 1290 classification is said to have used. The program prints the ranks in a fixed order so that the output can be checked by hand.",
        instructions = """
            1. Save the example as chained_copies.py.
            2. Run it: python3 chained_copies.py
        """,
        example = """
            BOOKS = [
                ("Bible", "scripture", True),             # (title, rank, chained?), illustrative
                ("Psalm commentary", "scripture", False),
                ("Augustine", "church doctors", True),
                ("Jerome", "church doctors", False),
                ("Aquinas", "modern masters", True),
                ("Giles of Rome", "modern masters", True),
                ("Aristotle", "ancient philosophers", False),
            ]
            RANKS = ["scripture", "church doctors", "modern masters", "ancient philosophers"]
            for rank in RANKS:
                group = [book for book in BOOKS if book[1] == rank]
                chained = sum(1 for book in group if book[2])
                print(f"{rank}: {len(group)} listed, {chained} chained")
            print("books in all:", len(BOOKS), "with chains:", sum(1 for book in BOOKS if book[2]))
        """,
    ),

    entry(
        databaseId = 76,
        kind = Kind.PROJECT,
        section = "Courts",
        title = "Offences in an illustrative Vinodol court, tallied by kind",
        originalTitle = "Court fines",
        author = "The judges of an illustrative court",
        year = 1288,
        summary = "A list of offences heard in an illustrative Vinodol court is tallied by kind, and a short program prints the count for each kind in alphabetical order.",
        details = "The offences are invented for the example and the Vinodol Code itself is not reproduced, so the counts say nothing about the real court. The sort is by name, so the output is the same on every run.",
        instructions = """
            1. Save the example as vinodol_fines.py.
            2. Run it: python3 vinodol_fines.py
        """,
        example = """
            OFFENCES = ["theft", "brawl", "debt", "brawl", "theft", "debt", "debt"]   # illustrative cases
            counts = {}
            for kind in OFFENCES:
                counts[kind] = counts.get(kind, 0) + 1
            for kind in sorted(counts):
                print(kind, counts[kind])
            print("cases in all:", len(OFFENCES))
        """,
    ),

)
