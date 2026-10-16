package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1470 to 1480: a family record book's generations, a university register, a loan ledger and a printers' pedigree tree. */
val db57Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 57,
        kind = Kind.PROJECT,
        section = "Family records",
        title = "How many generations a family record book spans",
        originalTitle = "Ricordanze",
        author = "Florentine households",
        year = 1480,
        summary = "A family record book covers a span of years, and dividing the span by the length of a generation shows how many full generations it reaches.",
        details = "The thirty-year generation in the example is illustrative and is not a historical rule. The program prints the span, the full generations inside it, the years left over and the starting year of each generation.",
        instructions = """
            1. Save the example as generations.py.
            2. Run it: python3 generations.py
        """,
        example = """
            FIRST_BIRTH = 1400
            LAST_ENTRY = 1480
            GENERATION = 30   # illustrative length of one generation

            span = LAST_ENTRY - FIRST_BIRTH
            print("years spanned:", span)
            print("full generations:", span // GENERATION)
            print("left over:", span % GENERATION, "years")
            for generation in range(span // GENERATION + 1):
                print("generation", generation, "starts in", FIRST_BIRTH + generation * GENERATION)
        """,
    ),

    entry(
        databaseId = 57,
        kind = Kind.PROJECT,
        section = "Universities",
        title = "A matriculation register for a new university",
        originalTitle = "Universitas Tubingensis",
        author = "Count Eberhard im Bart",
        year = 1477,
        summary = "A matriculation register lists each student by faculty, and a program counts the entries in each faculty to report the totals.",
        details = "The faculty list is invented for illustration, and it is not the register of any real university. The program counts the entries with a dictionary and prints the total first, then the count for each of four faculties.",
        instructions = """
            1. Save the example as register.py.
            2. Run it: python3 register.py
        """,
        example = """
            from collections import Counter

            faculties = ["arts", "law", "arts", "medicine", "arts", "theology"]
            counts = Counter(faculties)
            print("entries:", len(faculties))
            for faculty in ["arts", "law", "medicine", "theology"]:
                print(faculty, counts[faculty])
        """,
    ),

    entry(
        databaseId = 57,
        kind = Kind.PROJECT,
        section = "Libraries",
        title = "A loan ledger for manuscripts borrowed from a library",
        originalTitle = "Bibliotheca Apostolica Vaticana",
        author = "Vatican Library",
        year = 1475,
        summary = "A loan ledger compares the days each manuscript was out with the allowed loan period, and a program reports which loans are late.",
        details = "The loan period and the three loans are invented for illustration. The program prints each loan with its number of days out and the days late, if any.",
        instructions = """
            1. Save the example as loans.py.
            2. Run it: python3 loans.py
        """,
        example = """
            LOAN_DAYS = 30
            loans = [("codex A", 12), ("codex B", 45), ("codex C", 30)]
            for title, days_out in loans:
                late = max(0, days_out - LOAN_DAYS)
                status = f"{late} days late" if late else "on time"
                print(f"{title}: out {days_out} days, {status}")
        """,
    ),

    entry(
        databaseId = 57,
        kind = Kind.PROJECT,
        section = "Printing trade",
        title = "A pedigree of printers' apprentices, drawn as a tree",
        originalTitle = "Guillaume Le Roy",
        author = "Guillaume Le Roy",
        year = 1473,
        summary = "A tree of masters and apprentices is walked from the master downwards, and each level of the tree is indented by two spaces.",
        details = "The names in the example are only the labels master, apprentice and journeyman, invented for illustration and not a record of any workshop. The program prints the tree with its indents, using a recursive function that visits each child in turn.",
        instructions = """
            1. Save the example as pedigree.py.
            2. Run it: python3 pedigree.py
        """,
        example = """
            TREE = {
                "master": ["first apprentice", "second apprentice"],
                "first apprentice": ["journeyman"],
                "second apprentice": [],
                "journeyman": [],
            }

            def show(name, depth=0):
                print("  " * depth + name)
                for child in TREE[name]:
                    show(child, depth + 1)

            show("master")
        """,
    ),

)
