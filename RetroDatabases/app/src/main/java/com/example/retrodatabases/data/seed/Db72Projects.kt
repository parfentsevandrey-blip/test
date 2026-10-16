package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1320 to 1330: a timeline of dated items, the gaps between four foundations, the lengths of Chinese dynasties and a merchant partnership's profit. */
val db72Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 72,
        kind = Kind.PROJECT,
        section = "Timelines",
        title = "A timeline of thirteen dated items of the 1320s, sorted by year and counted by kind",
        originalTitle = "Timeline of dated items",
        author = "No single author",
        year = 1330,
        summary = "A list of thirteen dated items from this decade is sorted by year, printed in order and counted by kind.",
        details = "The list holds items from this batch, so the counts describe the sample rather than the whole decade. Items that share a year keep the order in which they were listed.",
        instructions = """
            1. Save the example as timeline.py.
            2. Run it: python3 timeline.py
        """,
        example = """
            ITEMS = [
                (1320, "LANGUAGE", "Declaration of Arbroath"),
                (1320, "LANGUAGE", "Dante's Paradiso"),
                (1321, "SYSTEM", "Studium of Florence"),
                (1321, "TOOL", "Taqwim al-buldan"),
                (1322, "TOOL", "Ars nova"),
                (1322, "PRACTICE", "Isorhythm"),
                (1324, "SYSTEM", "Michaelhouse"),
                (1325, "SYSTEM", "Gold florin of Hungary"),
                (1326, "DEVICE", "Florentine gun order"),
                (1326, "SYSTEM", "Oriel College"),
                (1328, "TOOL", "Bradwardine's treatise"),
                (1329, "PRACTICE", "Tughluq copper tokens"),
                (1329, "DEVICE", "Levi ben Gerson's cross-staff"),
            ]
            counts = {}
            for year, kind, title in sorted(ITEMS, key=lambda item: item[0]):
                print(year, kind, title)
                counts[kind] = counts.get(kind, 0) + 1
            print("items", len(ITEMS))
            for kind in sorted(counts):
                print(kind, counts[kind])
        """,
    ),

    entry(
        databaseId = 72,
        kind = Kind.PROJECT,
        section = "Colleges",
        title = "Gaps between the founding years of four institutions of the 1320s",
        originalTitle = "Founding years",
        author = "The founders of the four institutions",
        year = 1326,
        summary = "Four foundations of the 1320s are sorted by founding year, and a short program prints the gaps between them and the span they cover.",
        details = "The foundations are the studium of Florence, Michaelhouse, University Hall and Oriel College, with the years 1321, 1324, 1326 and 1326. Two of them share the year 1326, so one of the gaps is zero.",
        instructions = """
            1. Save the example as foundations.py.
            2. Run it: python3 foundations.py
        """,
        example = """
            FOUNDINGS = [
                ("Studium of Florence", 1321),
                ("Michaelhouse", 1324),
                ("University Hall", 1326),
                ("Oriel College", 1326),
            ]
            for name, year in sorted(FOUNDINGS, key=lambda item: item[1]):
                print(name, year)
            years = sorted(year for _, year in FOUNDINGS)
            gaps = [later - earlier for earlier, later in zip(years, years[1:])]
            print("gaps", gaps)
            print("span", years[-1] - years[0])
        """,
    ),

    entry(
        databaseId = 72,
        kind = Kind.PROJECT,
        section = "Dynasties",
        title = "The lengths of four Chinese dynasties from the Song to the Qing",
        originalTitle = "Dynastic lengths",
        author = "Chinese chroniclers and historians",
        year = 1320,
        summary = "The lengths of four Chinese dynasties, from the Song to the Qing, are worked out from their founding and ending years, and a short program prints each length in years.",
        details = "The Song is counted from 960 to 1279, the Yuan from 1271 to 1368, the Ming from 1368 to 1644 and the Qing from 1644 to 1912. The Yuan overlaps the end of the Song, so the lengths are listed one by one rather than added together, and 1320 falls in the Yuan period.",
        instructions = """
            1. Save the example as dynasties.py.
            2. Run it: python3 dynasties.py
        """,
        example = """
            DYNASTIES = [("Song", 960, 1279), ("Yuan", 1271, 1368), ("Ming", 1368, 1644), ("Qing", 1644, 1912)]
            for name, start, end in DYNASTIES:
                print(name, start, "to", end, "is", end - start, "years")
        """,
    ),

    entry(
        databaseId = 72,
        kind = Kind.PROJECT,
        section = "Partnerships",
        title = "A merchant partnership's profit shared by capital and months",
        originalTitle = "Partnership shares",
        author = "Italian merchant partners",
        year = 1320,
        summary = "A profit is shared among three partners in proportion to the capital each put in multiplied by the months it stayed, and a short program prints each share.",
        details = "The shares follow the product of capital and months, a division that partnerships of the period could follow. The three partners are illustrative, and the shares add back to the full profit of 1700.",
        instructions = """
            1. Save the example as partnership.py.
            2. Run it: python3 partnership.py
        """,
        example = """
            PARTNERS = [("A", 100, 12), ("B", 200, 5), ("C", 300, 4)]   # capital and months, illustrative
            PROFIT = 1700
            weights = {name: capital * months for name, capital, months in PARTNERS}
            total = sum(weights.values())
            for name, _, _ in PARTNERS:
                print(name, PROFIT * weights[name] // total)
            print("total", PROFIT)
        """,
    ),

)
