package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1220 to 1230: a small program that sorts five Mongol captures of Khwarazmian cities by year and counts them. */
val db82Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 82,
        kind = Kind.PROJECT,
        section = "Conquests",
        title = "The Mongol capture of five Khwarazmian cities, sorted and counted by year",
        originalTitle = "Cities taken in 1220 and 1221",
        author = "Mongol commanders under Genghis Khan",
        year = 1221,
        summary = "A short program sorts five cities of the Khwarazmian empire by the year the Mongols took them, then counts the captures in each year.",
        details = "Bukhara and Samarkand fell in 1220, and Urgench, Merv and Nishapur fell in 1221, with the years given as commonly stated, so the output is fixed. The list covers only five cities of the campaign, and the program does not claim that it is complete.",
        instructions = """
            1. Save the example as cities.py.
            2. Run it: python3 cities.py
        """,
        example = """
            CAPTURES = [
                ("Bukhara", 1220),
                ("Samarkand", 1220),
                ("Urgench", 1221),
                ("Merv", 1221),
                ("Nishapur", 1221),
            ]
            by_year = {}
            for city, year in sorted(CAPTURES, key=lambda item: (item[1], item[0])):
                by_year.setdefault(year, []).append(city)
            for year in sorted(by_year):
                print(year, len(by_year[year]), ", ".join(by_year[year]))
            print("cities listed:", len(CAPTURES))
            print("years of capture:", max(year for _, year in CAPTURES) - min(year for _, year in CAPTURES) + 1)
        """,
    ),

)
