package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1350 to 1360: a timeline of the decade's dated events, the calendar years in office of its rulers, its cities sorted by latitude and the arches of the Charles Bridge in Prague. */
val db69Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 69,
        kind = Kind.PROJECT,
        section = "Timeline",
        title = "A timeline of the 1350s built from dated events",
        originalTitle = "A timeline of the 1350s built from dated events",
        author = "No single author",
        year = 1350,
        summary = "A short program groups a list of dated events of the 1350s by year and counts how many fall in each year.",
        details = "The list holds fourteen events from this database, each with the year used in its record or in its text. The program prints the count for each year, the number of events and the number of years, so the output can be checked against the list.",
        instructions = """
            1. Save the example as timeline_1350s.py.
            2. Run it: python3 timeline_1350s.py
        """,
        example = """
            events = [
                (1350, "Holy Year"),
                (1351, "Statute of Labourers"),
                (1351, "Order of the Star"),
                (1352, "Strasbourg rooster clock"),
                (1353, "Decameron"),
                (1353, "Ordinance of the Staple"),
                (1354, "Passavanti's Mirror"),
                (1355, "Majestas Carolina"),
                (1355, "Ibn Battuta's Rihla"),
                (1356, "Golden Bull"),
                (1356, "battle of Poitiers"),
                (1357, "Mandeville's Travels"),
                (1358, "Jacquerie"),
                (1360, "treaty of Bretigny"),
            ]
            counts = {}
            for year, label in events:
                counts[year] = counts.get(year, 0) + 1
            for year in sorted(counts):
                print(year, counts[year])
            print("events", len(events))
            print("years", len(counts))
        """,
    ),

    entry(
        databaseId = 69,
        kind = Kind.PROJECT,
        section = "Rulers",
        title = "The rulers of the 1350s and the calendar years each held office",
        originalTitle = "The rulers of the 1350s and the calendar years each held office",
        author = "No single author",
        year = 1350,
        summary = "A short program counts, for six rulers of the decade, the calendar years between 1350 and 1360 in which each one held office.",
        details = "The list has three kings, two popes and an emperor, with each reign taken from its first and last years. A calendar year counts when the ruler held office in it at any point, so the totals are counts of years rather than exact months, and the program prints the total for each ruler and the number of rulers.",
        instructions = """
            1. Save the example as rulers_1350s.py.
            2. Run it: python3 rulers_1350s.py
        """,
        example = """
            rulers = [
                ("Philip VI of France", 1328, 1350),
                ("John II of France", 1350, 1364),
                ("Edward III of England", 1327, 1377),
                ("Clement VI", 1342, 1352),
                ("Innocent VI", 1352, 1362),
                ("Charles IV as emperor", 1355, 1378),
            ]
            for name, start, end in rulers:
                years = min(end, 1360) - max(start, 1350) + 1
                print(name, years)
            print("rulers", len(rulers))
        """,
    ),

    entry(
        databaseId = 69,
        kind = Kind.PROJECT,
        section = "Geography",
        title = "Cities of the 1350s sorted from north to south by latitude",
        originalTitle = "Cities of the 1350s sorted from north to south by latitude",
        author = "No single author",
        year = 1355,
        summary = "A short program sorts eight cities of the 1350s from north to south by latitude and prints each one, then the span from the northernmost to the southernmost.",
        details = "The latitudes are degrees north, given to two decimal places, and the example sorts them in descending order. The span is the difference between the first and last latitudes, so the output can be checked by hand.",
        instructions = """
            1. Save the example as cities_latitude.py.
            2. Run it: python3 cities_latitude.py
        """,
        example = """
            cities = [
                ("Cairo", 30.04),
                ("London", 51.51),
                ("Venice", 45.44),
                ("Prague", 50.08),
                ("Florence", 43.77),
                ("Delhi", 28.61),
                ("Paris", 48.86),
                ("Constantinople", 41.01),
            ]
            ordered = sorted(cities, key=lambda city: city[1], reverse=True)
            for name, latitude in ordered:
                print(name, f"{latitude:.2f}")
            span = ordered[0][1] - ordered[-1][1]
            print("span", f"{span:.2f}")
            print("cities", len(ordered))
        """,
    ),

    entry(
        databaseId = 69,
        kind = Kind.PROJECT,
        section = "Bridges",
        title = "The arches of the Charles Bridge in Prague, begun in 1357",
        originalTitle = "The arches of the Charles Bridge in Prague, begun in 1357",
        author = "Charles IV and the builders of Prague",
        year = 1357,
        summary = "A short program works out the number of piers and the average span of the arches of the Charles Bridge in Prague, begun in 1357, from its length and arch count.",
        details = "The bridge was begun in 1357 under Charles IV, and the figures used are 16 arches and a length of 516 metres, which are commonly given. The average span is the length divided by the number of arches, so it is a rough figure that includes the ends of the bridge, and the example prints it to two decimal places.",
        instructions = """
            1. Save the example as charles_bridge.py.
            2. Run it: python3 charles_bridge.py
        """,
        example = """
            arches = 16
            length = 516
            average = length / arches
            print("arches", arches)
            print("piers between arches", arches - 1)
            print("length", length, "m")
            print("average span", f"{average:.2f}", "m")
        """,
    ),

)
