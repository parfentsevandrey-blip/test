package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1291: a count of the thirteen Swiss cantons by the year each joined, and a count of four dated sieges and battles of the 1290s by year. */
val db75Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 75,
        kind = Kind.PROJECT,
        section = "Alps",
        title = "The thirteen Swiss cantons, counted by the year each joined the league",
        originalTitle = "Cantons of the Swiss league",
        author = "The cantons of the Swiss league",
        year = 1291,
        summary = "A short program groups the thirteen Swiss cantons by the year each joined the league, starting from the three that joined in 1291, and counts how many joined in each year.",
        details = "The years are the traditional dates of admission, and the list is a set of dated items rather than a full history of the league. The program prints one line per year, the total, and the number of cantons that joined after 1400.",
        instructions = """
            1. Save the example as cantons.py.
            2. Run it: python3 cantons.py
        """,
        example = """
            joined = [(1291, "Uri"), (1291, "Schwyz"), (1291, "Unterwalden"), (1332, "Lucerne"),
                      (1351, "Zurich"), (1352, "Glarus"), (1352, "Zug"), (1353, "Bern"),
                      (1481, "Fribourg"), (1481, "Solothurn"), (1501, "Basel"),
                      (1501, "Schaffhausen"), (1513, "Appenzell")]
            counts = {}
            for year, name in joined:
                counts[year] = counts.get(year, 0) + 1
            for year in sorted(counts):
                print(year, counts[year])
            print("cantons:", len(joined))
            print("joined after 1400:", sum(1 for year, name in joined if year > 1400))
        """,
    ),

    entry(
        databaseId = 75,
        kind = Kind.PROJECT,
        section = "Warfare",
        title = "Dated sieges and battles of the 1290s, counted by year",
        originalTitle = "Sieges and battles",
        author = "The chroniclers of the sieges and battles",
        year = 1291,
        summary = "A short program lists four dated sieges and battles of the 1290s, sorts them by year and counts how many fell in each year.",
        details = "The list gives the year of each event only: the fall of Acre in 1291, the capture of Berwick in 1296, and the battles of Falkirk and Curzola in 1298. The counts check the list and do not study the wars themselves.",
        instructions = """
            1. Save the example as sieges.py.
            2. Run it: python3 sieges.py
        """,
        example = """
            events = [(1298, "Falkirk"), (1291, "Acre"), (1298, "Curzola"), (1296, "Berwick")]
            counts = {}
            for year, name in sorted(events):
                counts[year] = counts.get(year, 0) + 1
            for year in sorted(counts):
                print(year, counts[year])
            print("events:", len(events))
            print("years with events:", len(counts))
        """,
    ),

)
