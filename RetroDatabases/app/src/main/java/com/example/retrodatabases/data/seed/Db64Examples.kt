package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1400 to 1409: change ringing for three bells, a manuscript's quires, electors by obedience, a Mediterranean course, ranked panels and two meshing wheels. */
val db64Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 64,
        kind = Kind.EXAMPLE,
        section = "Bells",
        title = "Every row of three church bells in change ringing",
        originalTitle = "Change ringing",
        author = "Church ringers",
        year = 1400,
        summary = "Three church bells can be rung in six orders, and a short program lists every row of change ringing for three bells.",
        details = "Change ringing rings each bell once in every row, and a method moves from row to row by swapping neighbouring bells. Change ringing as a practice came later than 1400, so the example lists the six orders in plain order, not in the order of a ringing method.",
        instructions = """
            1. Save the example as rows.py.
            2. Run it: python3 rows.py
        """,
        example = """
            from itertools import permutations

            bells = ["1", "2", "3"]
            rows = ["".join(order) for order in permutations(bells)]
            print("rows:", len(rows))
            for row in rows:
                print(row)
        """,
    ),

    entry(
        databaseId = 64,
        kind = Kind.EXAMPLE,
        section = "Manuscripts",
        title = "How many quires of eight leaves a manuscript needs",
        originalTitle = "Quire",
        author = "Scribes of the Latin West",
        year = 1400,
        summary = "A manuscript was sewn in quires, folded gatherings of leaves, and a short program finds how many quires of eight leaves an illustrative book of 97 leaves needs.",
        details = "Most quires were made of eight leaves, and the last quire of a book was often short, which is why the count rounds up. The example uses an illustrative book of 97 leaves, not a real manuscript, and it prints the full quires and the leaves left over.",
        instructions = """
            1. Save the example as quires.py.
            2. Run it: python3 quires.py
        """,
        example = """
            LEAVES = 97      # illustrative book, not a real manuscript
            PER_QUIRE = 8    # leaves in each gathering
            full, left = divmod(LEAVES, PER_QUIRE)
            print("full quires:", full)
            print("leaves left over:", left)
            print("quires needed:", full + (1 if left else 0))
        """,
    ),

    entry(
        databaseId = 64,
        kind = Kind.EXAMPLE,
        section = "Elections",
        title = "Electors of 1409 grouped by the pope they followed",
        originalTitle = "Obedience",
        author = "The cardinals of the rival popes",
        year = 1409,
        summary = "Electors at Pisa in 1409 followed one of the rival popes, and a short program groups an illustrative list of electors by obedience and counts each group.",
        details = "The list in the example is invented and does not record the cardinals who met at Pisa, so the counts say nothing about the real council. The program sorts the groups by name, so the output is the same on every run.",
        instructions = """
            1. Save the example as obedience.py.
            2. Run it: python3 obedience.py
        """,
        example = """
            electors = ["Rome", "Avignon", "Rome", "Pisa", "Pisa", "Pisa", "Avignon", "Rome"]   # illustrative obediences
            counts = {}
            for obedience in electors:
                counts[obedience] = counts.get(obedience, 0) + 1
            for name in sorted(counts):
                print(name, counts[name])
            print("total", len(electors))
        """,
    ),

    entry(
        databaseId = 64,
        kind = Kind.EXAMPLE,
        section = "Charts",
        title = "Course changes that bring a Mediterranean ship round to a heading",
        originalTitle = "Course changes",
        author = "Mediterranean pilots of about 1400",
        year = 1400,
        summary = "A ship that turns by a series of course changes ends on the heading found by adding them and taking the remainder after full turns, and a short program does that sum for a Mediterranean route.",
        details = "Headings on a sea chart are measured in degrees from north, so a total of turns above 360 degrees comes back to a smaller heading. The changes in the example are illustrative and are not taken from any chart.",
        instructions = """
            1. Save the example as heading.py.
            2. Run it: python3 heading.py
        """,
        example = """
            course_changes = [75, 140, 200, 15]   # illustrative changes of course in degrees
            total = sum(course_changes)
            print("total", total, "degrees")
            print("heading", total % 360, "degrees")
        """,
    ),

    entry(
        databaseId = 64,
        kind = Kind.EXAMPLE,
        section = "Competitions",
        title = "Ranking the panels of a 1401 competition by judges' scores",
        originalTitle = "Ranking by score",
        author = "Judges of a competition",
        year = 1401,
        summary = "A ranking sorts the entries by their total score, breaking a tie by name, and the example ranks four illustrative panels from the competition of 1401.",
        details = "The Florence competition of 1401 was judged on its panels, but the scores in the example are illustrative and do not record any real judges' votes. The sort breaks ties by name, so the output is the same on every run.",
        instructions = """
            1. Save the example as ranking.py.
            2. Run it: python3 ranking.py
        """,
        example = """
            entries = [("panel A", 9, 8), ("panel B", 9, 8), ("panel C", 7, 9), ("panel D", 6, 7)]
            # each entry has two judges' scores; sort by the total, then by name
            ranked = sorted(entries, key=lambda e: (-(e[1] + e[2]), e[0]))
            for place, (name, first, second) in enumerate(ranked, start=1):
                print(place, name, first + second)
        """,
    ),

    entry(
        databaseId = 64,
        kind = Kind.EXAMPLE,
        section = "Gears",
        title = "How many turns pass before two wheels mesh the same teeth again",
        originalTitle = "Gear meshing",
        author = "Clockmakers",
        year = 1400,
        summary = "Two wheels with 30 and 12 teeth mesh the same pair of teeth again after 60 teeth have passed, and a short program finds that common multiple and the turns each wheel makes.",
        details = "The two wheels meet the same teeth again when the number of teeth passed is a common multiple of both tooth counts, and the smallest such number is their least common multiple. The tooth counts are illustrative, chosen for the example, and not taken from a real clock.",
        instructions = """
            1. Save the example as meshing.py.
            2. Run it: python3 meshing.py
        """,
        example = """
            from math import gcd

            def lcm(a, b):
                return a * b // gcd(a, b)

            crown = 30    # teeth on the crown wheel, illustrative
            pinion = 12   # teeth on the second wheel, illustrative
            steps = lcm(crown, pinion)
            print("teeth passed before the teeth meet again:", steps)
            print("turns of the crown wheel:", steps // crown)
            print("turns of the second wheel:", steps // pinion)
        """,
    ),

)
