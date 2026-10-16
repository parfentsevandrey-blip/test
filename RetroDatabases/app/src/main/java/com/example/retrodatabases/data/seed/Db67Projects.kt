package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1371 to 1379: a shelf list of a royal library, a ranking of examination candidates, gaps between decade institutions and an illustrative cannon ledger. */
val db67Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 67,
        kind = Kind.PROJECT,
        section = "Library",
        title = "Books of Charles V's library by subject, from an illustrative shelf list",
        originalTitle = "Shelf list",
        author = "Royal librarians",
        year = 1373,
        summary = "An illustrative shelf list of a royal library is totalled by subject, and a short program prints each subject's count and the largest subject.",
        details = "The counts in the example are invented to show the method, and they do not come from the 1373 inventory. The program sorts the subjects by name, so the output is the same on every run.",
        instructions = """
            1. Save the example as shelf.py.
            2. Run it: python3 shelf.py
        """,
        example = """
            shelf = [("theology", 40), ("law", 25), ("history", 30), ("theology", 15), ("science", 20), ("history", 10)]
            totals = {}
            for subject, volumes in shelf:
                totals[subject] = totals.get(subject, 0) + volumes
            for subject in sorted(totals):
                print(subject, totals[subject])
            largest = max(totals, key=totals.get)
            print("largest subject:", largest)
            print("volumes in all:", sum(totals.values()))
        """,
    ),

    entry(
        databaseId = 67,
        kind = Kind.PROJECT,
        section = "Examinations",
        title = "Ranking an illustrative list of examination candidates by score",
        originalTitle = "Examination ranking",
        author = "No single author",
        year = 1371,
        summary = "An illustrative list of examination candidates is ranked by score, with equal scores kept in the order of the list, and a short program prints the places.",
        details = "The names and scores are invented for the example and do not come from any examination of 1371. The program uses a stable sort, so candidates with equal scores keep their order from the list.",
        instructions = """
            1. Save the example as ranking.py.
            2. Run it: python3 ranking.py
        """,
        example = """
            candidates = [("Wang", 82), ("Li", 91), ("Zhao", 82), ("Chen", 77), ("Liu", 91)]
            ranked = sorted(candidates, key=lambda item: -item[1])   # sorted() keeps the order of equal scores
            for place, (name, score) in enumerate(ranked, start=1):
                print(place, name, score)
        """,
    ),

    entry(
        databaseId = 67,
        kind = Kind.PROJECT,
        section = "Dates",
        title = "Gaps in years between five dated institutions of the decade",
        originalTitle = "Dated institutions",
        author = "No single author",
        year = 1379,
        summary = "Five dated institutions of the decade are sorted by year, and a short program prints the gap in years between each pair and the largest gap.",
        details = "The dates used are the civil examination of 1371, the Ming paper money of 1375, the Goryeo gunpowder office of 1377, the Western Schism of 1378 and the University of Erfurt of 1379. The program sorts the list before it counts the gaps, so the order of the list does not matter.",
        instructions = """
            1. Save the example as gaps.py.
            2. Run it: python3 gaps.py
        """,
        example = """
            events = [("Ming civil examination", 1371), ("Ming paper money", 1375), ("Goryeo gunpowder office", 1377), ("Western Schism", 1378), ("University of Erfurt", 1379)]
            events.sort(key=lambda item: item[1])
            gaps = []
            for (name_a, year_a), (name_b, year_b) in zip(events, events[1:]):
                gaps.append(year_b - year_a)
                print(year_a, "to", year_b, "gap", year_b - year_a)
            print("largest gap:", max(gaps), "years")
        """,
    ),

    entry(
        databaseId = 67,
        kind = Kind.PROJECT,
        section = "Ledgers",
        title = "Cannon ordered by the Goryeo office in a year, from an illustrative ledger",
        originalTitle = "Ordnance ledger",
        author = "Officials of the gunpowder office",
        year = 1377,
        summary = "An illustrative ledger of cannon orders is totalled by year, and a short program prints the total for each year and the overall total.",
        details = "The orders in the example are invented to show the method and do not record the office's real work. The program adds the orders for each year, and it prints the years in order so the output is the same on every run.",
        instructions = """
            1. Save the example as ordnance.py.
            2. Run it: python3 ordnance.py
        """,
        example = """
            orders = [(1377, 12), (1377, 8), (1378, 5), (1379, 20), (1379, 3)]   # illustrative orders, not a ledger
            totals = {}
            for year, count in orders:
                totals[year] = totals.get(year, 0) + count
            for year in sorted(totals):
                print(year, totals[year])
            print("cannon in all:", sum(totals.values()))
        """,
    ),

)
