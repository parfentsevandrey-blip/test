package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1540 to 1545: a silver ledger with the royal fifth, a gunpowder mix for a matchlock, a gazetteer sorted by longitude and a ship's day run. */
val db50Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 50,
        kind = Kind.PROJECT,
        section = "Silver",
        title = "A ledger of silver bars with the royal fifth",
        originalTitle = "Quinto real",
        author = "Crown officials at Potosi",
        year = 1545,
        summary = "The royal fifth takes a fifth of each batch of silver, and a short ledger program adds the bars and splits off the crown's share.",
        details = "The crown's share of mined silver in the Spanish colonies was a fifth, the royal fifth, and the Potosi mine opened in 1545 became one of the richest sources. The example uses illustrative bar weights and whole numbers, so the ledger balances to the last mark.",
        instructions = """
            1. Save the example as royal_fifth.py.
            2. Run it: python3 royal_fifth.py
        """,
        example = """
            bars = {"bar A": 10, "bar B": 15, "bar C": 25}   # illustrative weights in marks

            total = sum(bars.values())
            fifth = total // 5
            print("total silver:", total, "marks")
            print("royal fifth:", fifth, "marks")
            print("left for the owners:", total - fifth, "marks")
        """,
    ),

    entry(
        databaseId = 50,
        kind = Kind.PROJECT,
        section = "Gunpowder",
        title = "Mixing gunpowder by parts, for a matchlock",
        originalTitle = "Gunpowder",
        author = "Gunpowder makers of the sixteenth century",
        year = 1543,
        summary = "A sixteenth-century powder recipe of about 75, 15 and 10 parts of saltpetre, charcoal and sulphur is scaled by a short program to two exact batches.",
        details = "Recipes for gunpowder varied from maker to maker, but mixes near 75, 15 and 10 parts were common in the sixteenth century, and a matchlock gun fired powder of this kind. The example uses illustrative batch weights and whole numbers, so each batch splits without rounding.",
        instructions = """
            1. Save the example as gunpowder_mix.py.
            2. Run it: python3 gunpowder_mix.py
        """,
        example = """
            PARTS = {"saltpetre": 75, "charcoal": 15, "sulphur": 10}   # parts by weight, out of 100

            for batch in (200, 500):
                amounts = {name: batch * parts // 100 for name, parts in PARTS.items()}
                print(f"batch of {batch} g: saltpetre {amounts['saltpetre']} g, charcoal {amounts['charcoal']} g, sulphur {amounts['sulphur']} g")
        """,
    ),

    entry(
        databaseId = 50,
        kind = Kind.PROJECT,
        section = "Gazetteer",
        title = "Sorting places by longitude, in minutes",
        originalTitle = "Geographia",
        author = "Sebastian Munster",
        year = 1540,
        summary = "Turning each place's degrees and minutes into minutes lets a short program sort a gazetteer by longitude.",
        details = "Munster's 1540 edition of Ptolemy's Geography, printed at Basel, gave places by their degrees and minutes of longitude and latitude. The example sorts three illustrative places, not Ptolemy's own list, by converting each longitude to minutes first.",
        instructions = """
            1. Save the example as gazetteer.py.
            2. Run it: python3 gazetteer.py
        """,
        example = """
            places = [
                ("Alpha", 12, 30),   # longitude in degrees and minutes, illustrative
                ("Beta", 4, 45),
                ("Gamma", 9, 0),
            ]

            def minutes(degrees, extra):
                return degrees * 60 + extra

            for name, degrees, extra in sorted(places, key=lambda p: minutes(p[1], p[2])):
                print(f"{name} at {degrees} degrees {extra} minutes, which is {minutes(degrees, extra)} minutes")
        """,
    ),

    entry(
        databaseId = 50,
        kind = Kind.PROJECT,
        section = "Navigation",
        title = "A ship's day run from logged watches",
        originalTitle = "Arte de navegar",
        author = "Pedro de Medina",
        year = 1545,
        summary = "A pilot's day run adds the leagues logged for each course, and a short program totals the run by course.",
        details = "Medina's navigation book of 1545 taught pilots to log courses and distances, and the day's run is the sum of the leagues logged on each course. The example uses illustrative watches and whole leagues, so the totals are exact.",
        instructions = """
            1. Save the example as day_run.py.
            2. Run it: python3 day_run.py
        """,
        example = """
            watches = [("NE", 3), ("NE", 2), ("E", 4), ("SE", 1)]   # (course, leagues) for four watches, illustrative

            totals = {}
            for course, leagues in watches:
                totals[course] = totals.get(course, 0) + leagues
            for course in ("NE", "E", "SE"):
                print(course, totals[course], "leagues")
            print("day's run:", sum(totals.values()), "leagues")
        """,
    ),

)
