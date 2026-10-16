package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1330 to 1339: a timeline of the decade's records, the lengths of three wars, medieval universities by founding year and three cathedral building spans. */
val db71Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 71,
        kind = Kind.PROJECT,
        section = "Records",
        title = "A timeline of the 1330s records, sorted by year",
        originalTitle = "Timeline",
        author = "No single author",
        year = 1339,
        summary = "A short program sorts ten dated items of the 1330s by year and counts the items in each year.",
        details = "The items are dated entries of this decade, and the program prints them in year order, then the count for each year, then the total. Three years, 1330, 1338 and 1339, hold two items each.",
        instructions = """
            1. Save the example as timeline.py.
            2. Run it: python3 timeline.py
        """,
        example = """
            ITEMS = [
                (1330, "Odoric of Pordenone's account, written at Padua"),
                (1330, "Kenko's Essays in Idleness"),
                (1332, "The university of Cahors"),
                (1333, "The Damascus astrolabic quadrant"),
                (1335, "The clock of San Gottardo in Milan"),
                (1336, "The Kenmu code of seventeen articles"),
                (1338, "The Rhense declaration of the electors"),
                (1338, "The hourglass in Lorenzetti's fresco"),
                (1339, "The university of Grenoble"),
                (1339, "Dulcert's portolan chart"),
            ]
            ITEMS.sort()
            for year, title in ITEMS:
                print(year, title)
            counts = {}
            for year, title in ITEMS:
                counts[year] = counts.get(year, 0) + 1
            for year in sorted(counts):
                print("items in", year, ":", counts[year])
            print("items in the decade:", len(ITEMS))
        """,
    ),

    entry(
        databaseId = 71,
        kind = Kind.PROJECT,
        section = "Wars",
        title = "Lengths of three long wars in years, from 1337 to 1648",
        originalTitle = "Years of war",
        author = "No single author",
        year = 1337,
        summary = "A short program takes three long wars with their conventional start and end years, sorts them from longest to shortest and totals the years they lasted.",
        details = "The Hundred Years' War is counted from 1337 to 1453, the Eighty Years' War from 1568 to 1648 and the Thirty Years' War from 1618 to 1648, all by conventional dates. The program prints the wars from longest to shortest and their total, so the arithmetic can be checked by hand.",
        instructions = """
            1. Save the example as wars.py.
            2. Run it: python3 wars.py
        """,
        example = """
            WARS = [
                ("Hundred Years' War", 1337, 1453),
                ("Eighty Years' War", 1568, 1648),
                ("Thirty Years' War", 1618, 1648),
            ]
            durations = [(end - start, name) for name, start, end in WARS]
            for years, name in sorted(durations, reverse=True):
                print(name, years, "years")
            print("total of the three:", sum(years for years, _ in durations), "years")
        """,
    ),

    entry(
        databaseId = 71,
        kind = Kind.PROJECT,
        section = "Universities",
        title = "Medieval universities by founding year, from Bologna to Coimbra",
        originalTitle = "Founding years",
        author = "No single author",
        year = 1330,
        summary = "A short program takes seven medieval universities with their founding years, finds the oldest and the newest and counts the foundations in each century.",
        details = "The founding years are the conventional dates of each university, and Bologna in 1088 and Coimbra in 1290 mark the ends of the list. The program prints the oldest and newest, the span between them and the count for each century, so the figures can be checked by hand.",
        instructions = """
            1. Save the example as universities.py.
            2. Run it: python3 universities.py
        """,
        example = """
            FOUNDED = [
                ("Bologna", 1088),
                ("Cambridge", 1209),
                ("Salamanca", 1218),
                ("Padua", 1222),
                ("Naples", 1224),
                ("Toulouse", 1229),
                ("Coimbra", 1290),
            ]
            oldest = min(FOUNDED, key=lambda item: item[1])
            newest = max(FOUNDED, key=lambda item: item[1])
            print("oldest:", oldest[0], oldest[1])
            print("newest:", newest[0], newest[1])
            print("years from oldest to newest:", newest[1] - oldest[1])
            centuries = {}
            for name, year in FOUNDED:
                century = year // 100 + 1
                centuries[century] = centuries.get(century, 0) + 1
            for century in sorted(centuries):
                print("founded in century", century, ":", centuries[century])
            print("universities listed:", len(FOUNDED))
        """,
    ),

    entry(
        databaseId = 71,
        kind = Kind.PROJECT,
        section = "Architecture",
        title = "Building spans of three cathedrals, from Cologne to Florence",
        originalTitle = "Cathedral building spans",
        author = "Cathedral building works",
        year = 1339,
        summary = "A short program takes three cathedrals with the years their building began and ended, sorts the spans from longest to shortest and totals them.",
        details = "The dates are the conventional years when each cathedral's building began and its main structure was finished: Cologne from 1248 to 1880, Ulm from 1377 to 1890 and Florence from 1296 to 1436. The program prints the spans from longest to shortest, the longest cathedral and the total, so each figure can be checked by hand.",
        instructions = """
            1. Save the example as cathedrals.py.
            2. Run it: python3 cathedrals.py
        """,
        example = """
            CATHEDRALS = [
                ("Cologne", 1248, 1880),
                ("Ulm", 1377, 1890),
                ("Florence", 1296, 1436),
            ]
            spans = [(end - start, name) for name, start, end in CATHEDRALS]
            for years, name in sorted(spans, reverse=True):
                print(name, years, "years")
            print("longest:", max(spans)[1])
            print("total of the three:", sum(years for years, _ in spans), "years")
        """,
    ),

)
