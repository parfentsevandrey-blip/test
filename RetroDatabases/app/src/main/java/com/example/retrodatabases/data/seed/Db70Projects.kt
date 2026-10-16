package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1343 to 1349: a timeline of the decade, a ledger of loans to a king, a manor's worker-days after the plague and the reign lengths of six rulers. */
val db70Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 70,
        kind = Kind.PROJECT,
        section = "Timelines",
        title = "A timeline of the 1340s with the years of each entry sorted",
        originalTitle = "Timeline",
        author = "No single author",
        year = 1343,
        summary = "Eighteen dated entries of this decade are sorted by year, and a short program prints them in order and counts the entries in each year.",
        details = "The entries are dated items of the decade, typed in as pairs of year and short name, and the program sorts them by year and then by name. The counts for each year let the reader check the list by eye.",
        instructions = """
            1. Save the example as timeline.py.
            2. Run it: python3 timeline.py
        """,
        example = """
            EVENTS = [
                (1340, "Ayenbite of Inwyt"),
                (1341, "Teseida"),
                (1340, "Remede de Fortune"),
                (1343, "Unigenitus"),
                (1343, "Bardi and Peruzzi default"),
                (1346, "Crecy guns"),
                (1346, "Caffa catapults"),
                (1347, "Bahmani Sultanate"),
                (1347, "Siena bell"),
                (1348, "Charles University"),
                (1348, "Garter"),
                (1348, "Paris plague report"),
                (1348, "Villani chronicle"),
                (1349, "Dusan Code"),
                (1349, "Ibn al-Khatib treatise"),
                (1349, "Ordinance of Labourers"),
                (1350, "Holy Year"),
                (1350, "Konrad Book of Nature"),
            ]
            events = sorted(EVENTS)
            for year, item in events:
                print(year, item)
            counts = {}
            for year, item in events:
                counts[year] = counts.get(year, 0) + 1
            for year in sorted(counts):
                print("count", year, counts[year])
            print("events:", len(events))
        """,
    ),

    entry(
        databaseId = 70,
        kind = Kind.PROJECT,
        section = "Banking",
        title = "A ledger of loans to a king, with illustrative sums left unpaid",
        originalTitle = "Loan ledger",
        author = "Bankers of Florence, illustratively",
        year = 1343,
        summary = "A ledger of three loans to a king adds the sums lent and repaid and prints the balance left unpaid, using illustrative sums.",
        details = "The sums are illustrative and do not come from the Bardi or Peruzzi books, and the surviving records of their lending are incomplete. The example shows the method of a ledger only, so the balance of 2400 can be checked by hand.",
        instructions = """
            1. Save the example as bardi_ledger.py.
            2. Run it: python3 bardi_ledger.py
        """,
        example = """
            LOANS = [1000, 1500, 500]   # illustrative sums lent, in units of account
            REPAID = [200, 300, 100]    # illustrative sums repaid
            print("lent:", sum(LOANS))
            print("repaid:", sum(REPAID))
            print("unpaid:", sum(LOANS) - sum(REPAID))
        """,
    ),

    entry(
        databaseId = 70,
        kind = Kind.PROJECT,
        section = "Labour",
        title = "A manor's worker-days before and after the plague, illustratively",
        originalTitle = "Worker-days",
        author = "Stewards of English manors, illustratively",
        year = 1349,
        summary = "A manor's crew of twelve falls to nine after the plague in an illustrative week of six working days, and a short program counts the worker-days lost.",
        details = "The crew sizes and the six-day week are illustrative and do not come from any manor roll, and the program shows only the arithmetic of a labour service. The ordinance of 1349 set wages rather than crew sizes, so the figures are a model of the accounts and not a record of them.",
        instructions = """
            1. Save the example as worker_days.py.
            2. Run it: python3 worker_days.py
        """,
        example = """
            BEFORE = 12   # illustrative crew before the plague
            AFTER = 9     # illustrative crew after it
            DAYS = 6      # working days in the week
            print("worker-days before:", BEFORE * DAYS)
            print("worker-days after:", AFTER * DAYS)
            print("worker-days lost:", (BEFORE - AFTER) * DAYS)
        """,
    ),

    entry(
        databaseId = 70,
        kind = Kind.PROJECT,
        section = "Rulers",
        title = "Reign lengths of six rulers of the 1340s, sorted from longest to shortest",
        originalTitle = "Reign lengths",
        author = "No single author",
        year = 1345,
        summary = "Six rulers and popes who held office in the 1340s are listed with the years of their reigns, and a short program sorts them from longest to shortest.",
        details = "The start and end years are given as commonly stated, and the reign of Edward III runs longest at fifty years. Bahman Shah and Clement VI are the shortest, with eleven and ten years, so the sorted list can be checked by eye.",
        instructions = """
            1. Save the example as reigns.py.
            2. Run it: python3 reigns.py
        """,
        example = """
            REIGNS = [
                ("Edward III", 1327, 1377),
                ("Philip VI", 1328, 1350),
                ("Alfonso XI", 1312, 1350),
                ("Clement VI", 1342, 1352),
                ("Stefan Dusan", 1331, 1355),
                ("Bahman Shah", 1347, 1358),
            ]
            rows = sorted(REIGNS, key=lambda row: row[2] - row[1], reverse=True)
            for name, start, end in rows:
                print(name, end - start)
            print("rulers:", len(REIGNS))
        """,
    ),

)
