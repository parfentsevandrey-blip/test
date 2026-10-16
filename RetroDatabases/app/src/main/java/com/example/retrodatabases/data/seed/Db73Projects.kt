package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1313 to 1316: a register numbered afresh each year, a body tree walked from the head, copies of a lost exemplar and numbers for candidates' papers. */
val db73Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 73,
        kind = Kind.PROJECT,
        section = "Records",
        title = "A register that numbers its letters afresh in each year",
        originalTitle = "Register numbering",
        author = "Chancery clerks",
        year = 1316,
        summary = "A register that numbers its entries again at the start of each year is easy to keep in order, and a short program numbers a list of illustrative entries.",
        details = "The entries in the example are labels, not real letters, and each year begins again at number one. The program prints each entry with its year and number and then counts the entries and the years.",
        instructions = """
            1. Save the example as register.py.
            2. Run it: python3 register.py
        """,
        example = """
            entries = [(1316, "A"), (1316, "B"), (1317, "C"), (1317, "D"), (1317, "E"), (1318, "F")]
            count = {}
            for year, label in entries:
                count[year] = count.get(year, 0) + 1
                print(f"{year}/{count[year]} entry {label}")
            print("entries:", len(entries))
            print("years:", len(count))
        """,
    ),

    entry(
        databaseId = 73,
        kind = Kind.PROJECT,
        section = "Anatomy",
        title = "A body tree of organs listed by a walk from the head down",
        originalTitle = "Tree walk",
        author = "No single author",
        year = 1316,
        summary = "A body can be written as a tree of parts, and a short program lists the parts by a walk that visits each part before its own parts.",
        details = "The tree in the example is small and illustrative, and the walk prints each part with an indent for its depth. The program counts the nodes, which should match the nine parts that the tree holds.",
        instructions = """
            1. Save the example as body_tree.py.
            2. Run it: python3 body_tree.py
        """,
        example = """
            TREE = {
                "body": ["head", "trunk", "limbs"],
                "head": ["brain"],
                "trunk": ["heart", "lungs"],
                "limbs": ["arm", "leg"],
            }
            visited = 0

            def walk(name, depth=0):
                global visited
                visited += 1
                print("  " * depth + name)
                for child in TREE.get(name, []):
                    walk(child, depth + 1)

            walk("body")
            print("nodes:", visited)
        """,
    ),

    entry(
        databaseId = 73,
        kind = Kind.PROJECT,
        section = "Copying",
        title = "Copies of a lost exemplar counted down a family of manuscripts",
        originalTitle = "Family of copies",
        author = "Scribes of the period",
        year = 1316,
        summary = "A lost exemplar may survive only through the copies made from it, and a short program counts every copy in the family and the copies that have no further copies.",
        details = "The family in the example is small and illustrative, with two copies made from the exemplar and three copies made from those. The program walks the family as a tree and prints the total and the copies that end their line.",
        instructions = """
            1. Save the example as copies.py.
            2. Run it: python3 copies.py
        """,
        example = """
            FAMILY = {
                "exemplar": ["copy A", "copy B"],
                "copy A": ["copy C", "copy D"],
                "copy B": ["copy E"],
            }

            def copies(name):
                total = 0
                for child in FAMILY.get(name, []):
                    total += 1 + copies(child)
                return total

            def ends(name):
                children = FAMILY.get(name, [])
                if not children:
                    return 0 if name == "exemplar" else 1
                return sum(ends(child) for child in children)

            print("copies made from the exemplar:", copies("exemplar"))
            print("copies with no further copies:", ends("exemplar"))
        """,
    ),

    entry(
        databaseId = 73,
        kind = Kind.PROJECT,
        section = "Examinations",
        title = "Anonymous numbers for candidates' papers, matched back after marking",
        originalTitle = "Numbered papers",
        author = "No single author",
        year = 1313,
        summary = "Candidates' papers can be given numbers before marking so that the marker does not see the names, and a short program matches the marks back to the candidates afterwards.",
        details = "The names and marks in the example are illustrative. The program prints each candidate with the paper number and the marks, and the total of the marks, so the matching can be checked by hand.",
        instructions = """
            1. Save the example as sealed.py.
            2. Run it: python3 sealed.py
        """,
        example = """
            names = ["Ansel", "Berta", "Cosimo"]
            numbers = {name: index + 1 for index, name in enumerate(names)}
            marks = {1: 14, 2: 17, 3: 9}   # illustrative marks, given by paper number
            for name in sorted(names):
                number = numbers[name]
                print(name, "was paper", number, "with", marks[number], "marks")
            print("total marks:", sum(marks.values()))
        """,
    ),

)
