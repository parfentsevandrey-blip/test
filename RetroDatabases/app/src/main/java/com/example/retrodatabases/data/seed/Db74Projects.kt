package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1300 to 1310: a timeline of the decade's events, a check of papal acts, the rare records by year and a check of the decade's years. */
val db74Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 74,
        kind = Kind.PROJECT,
        section = "Timeline",
        title = "Gaps between the events of the 1300s, listed by year",
        originalTitle = "Decade timeline",
        author = "No single author",
        year = 1309,
        summary = "A short program sorts ten dated events of the 1300s and prints the gap in years between each event and the one before.",
        details = "The events are the ones named in this database, each placed in its year. The program prints a gap of zero for the first event, so the list reads as a simple timeline.",
        instructions = """
            1. Save the example as timeline.py.
            2. Run it: python3 timeline.py
        """,
        example = """
            events = [
                (1300, "Der Renner"),
                (1301, "Planudes's anthology"),
                (1302, "Estates-General"),
                (1303, "Studium Urbis"),
                (1304, "Warwolf at Stirling"),
                (1305, "Lilium medicinae"),
                (1306, "spectacles sermon"),
                (1307, "Templar arrests"),
                (1308, "Perugia studium"),
                (1309, "Avignon court"),
            ]
            previous = None
            for year, name in events:
                gap = 0 if previous is None else year - previous
                print(year, name, "gap", gap)
                previous = year
            print("events:", len(events))
        """,
    ),

    entry(
        databaseId = 74,
        kind = Kind.PROJECT,
        section = "Papal acts",
        title = "Which papal acts of the early 1300s fall inside the decade",
        originalTitle = "Papal acts, 1298 to 1308",
        author = "Popes of the period",
        year = 1308,
        summary = "A short program checks a short list of papal acts against the decade and prints which ones fall inside it.",
        details = "The list holds Unam sanctam, the Studium Urbis bull, Super specula and the Liber Sextus, and the check keeps the years from 1300 to 1310. The program prints each act and then the number that fall inside the decade.",
        instructions = """
            1. Save the example as papal_acts.py.
            2. Run it: python3 papal_acts.py
        """,
        example = """
            acts = [
                ("Unam sanctam", 1302),
                ("Studium Urbis bull", 1303),
                ("Super specula", 1308),
                ("Liber Sextus", 1298),
            ]
            for name, year in acts:
                status = "inside" if 1300 <= year <= 1310 else "outside"
                print(f"{name}: {year}, {status} the decade")
            print("acts inside the decade:", sum(1 for _, y in acts if 1300 <= y <= 1310))
        """,
    ),

    entry(
        databaseId = 74,
        kind = Kind.PROJECT,
        section = "Rare items",
        title = "The rare titles of this batch in year order, with their count",
        originalTitle = "Rare records by year",
        author = "No single author",
        year = 1305,
        summary = "A short program sorts the nine rare records of this batch by year and prints each with its year, so the rare set can be checked against the list.",
        details = "The list holds the titles of the rare records in this database, each with its year. The program sorts by year and then by title, and it prints the count at the end.",
        instructions = """
            1. Save the example as rare_by_year.py.
            2. Run it: python3 rare_by_year.py
        """,
        example = """
            rare_items = [
                (1308, "Llull's figures"),
                (1303, "Zhu's Jade Mirror"),
                (1304, "Warwolf at Stirling"),
                (1300, "Der Renner"),
                (1300, "Roll of Caerlaverock"),
                (1302, "Hauksbok"),
                (1300, "Canon Musique clock"),
                (1300, "Farolfi ledger"),
                (1308, "Perugia studium"),
            ]
            for year, name in sorted(rare_items):
                print(year, name)
            print("rare items:", len(rare_items))
        """,
    ),

    entry(
        databaseId = 74,
        kind = Kind.PROJECT,
        section = "Checks",
        title = "A range check on record years from 1300 to 1310",
        originalTitle = "Decade year check",
        author = "No single author",
        year = 1300,
        summary = "A short program tests a list of years against the decade 1300 to 1310 and prints any year that falls outside it.",
        details = "The list holds the years from 1300 to 1310, and the check is inclusive at both ends. The program prints the number of years checked and the list of years outside the decade, which is empty here.",
        instructions = """
            1. Save the example as decade_years.py.
            2. Run it: python3 decade_years.py
        """,
        example = """
            record_years = [1300, 1301, 1302, 1303, 1304, 1305, 1306, 1307, 1308, 1309, 1310]
            outside = [y for y in record_years if not 1300 <= y <= 1310]
            print("years checked:", len(record_years))
            print("years outside 1300 to 1310:", outside)
        """,
    ),

)
