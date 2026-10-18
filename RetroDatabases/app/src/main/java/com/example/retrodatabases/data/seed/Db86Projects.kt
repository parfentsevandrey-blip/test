package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1180 to 1190: a count of the deaths of eight rulers, grouped by year. */
val db86Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 86,
        kind = Kind.PROJECT,
        section = "Rulers",
        title = "The deaths of eight rulers in the 1180s, counted by year",
        originalTitle = "Deaths of rulers",
        author = "Chroniclers of the courts concerned",
        year = 1180,
        summary = "Eight rulers and princes died or were killed between 1180 and 1190, and a short program counts how many died in each year of the list.",
        details = "The list holds Louis VII of France, Alexios II Komnenos, Abu Yaqub Yusuf of the Almohads, Baldwin IV of Jerusalem, Andronikos I Komnenos, Henry II of England, the Jin emperor Shizong and Frederick I Barbarossa. The program groups the deaths by year and prints the totals, which stay the same on every run.",
        instructions = """
            1. Save the example as deaths.py.
            2. Run it: python3 deaths.py
        """,
        example = """
            deaths = [
                ("Louis VII of France", 1180),
                ("Alexios II Komnenos", 1183),
                ("Abu Yaqub Yusuf of the Almohads", 1184),
                ("Baldwin IV of Jerusalem", 1185),
                ("Andronikos I Komnenos", 1185),
                ("Henry II of England", 1189),
                ("Jin emperor Shizong", 1189),
                ("Frederick I Barbarossa", 1190),
            ]
            by_year = {}
            for name, year in deaths:
                by_year[year] = by_year.get(year, 0) + 1
            for year in sorted(by_year):
                print(year, by_year[year])
            print("rulers listed", len(deaths))
        """,
    ),

)
