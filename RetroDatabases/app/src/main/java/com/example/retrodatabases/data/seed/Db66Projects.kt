package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1380 to 1390: quarter days of the rent year, dated battles and foundations, and the fixed feasts of the church year in 1386. */
val db66Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 66,
        kind = Kind.PROJECT,
        section = "Rent",
        title = "The quarter days of the English rent year in 1386",
        originalTitle = "Quarter days",
        author = "English landlords and their stewards",
        year = 1386,
        summary = "The four quarter days of the English rent year divide the year into unequal parts, and a short program counts the days between them in 1386.",
        details = "Lady Day, Midsummer, Michaelmas and Christmas were the days on which rents fell due. The example counts days with Python dates, which give the same gaps here because 1386 and 1387 are common years in either calendar.",
        instructions = """
            1. Save the example as quarter_days.py.
            2. Run it: python3 quarter_days.py
        """,
        example = """
            from datetime import date

            quarter_days = [
                ("Lady Day", date(1386, 3, 25)),
                ("Midsummer", date(1386, 6, 24)),
                ("Michaelmas", date(1386, 9, 29)),
                ("Christmas", date(1386, 12, 25)),
            ]
            next_lady_day = date(1387, 3, 25)

            gaps = []
            for index, (name, start) in enumerate(quarter_days):
                end = quarter_days[index + 1][1] if index + 1 < len(quarter_days) else next_lady_day
                gaps.append((end - start).days)
                print(f"{name} to the next quarter day: {(end - start).days} days")
            print("days in the rent year:", sum(gaps))
        """,
    ),

    entry(
        databaseId = 66,
        kind = Kind.PROJECT,
        section = "Grouping",
        title = "Battles and foundations of the 1380s grouped by country",
        originalTitle = "Dated events list",
        author = "No single author",
        year = 1386,
        summary = "A list of dated events of the 1380s is sorted by year, and a short program counts how many fall in each country.",
        details = "The events are a short list of battles and foundations of the decade, with the years given by the usual accounts. The program sorts the list by year and then counts the events for each country, so the output can be checked by hand.",
        instructions = """
            1. Save the example as events_by_country.py.
            2. Run it: python3 events_by_country.py
        """,
        example = """
            from collections import Counter

            events = [
                (1380, "Kulikovo", "Russia"),
                (1381, "Wat Tyler killed", "England"),
                (1382, "Winchester College founded", "England"),
                (1385, "Aljubarrota", "Portugal"),
                (1386, "Heidelberg opened", "Germany"),
                (1386, "Sempach", "Switzerland"),
                (1388, "Cologne founded", "Germany"),
                (1389, "Kosovo", "Serbia"),
            ]

            for year, label, country in sorted(events):
                print(year, label, "-", country)
            for country, count in sorted(Counter(country for _, _, country in events).items()):
                print(country, count)
        """,
    ),

    entry(
        databaseId = 66,
        kind = Kind.PROJECT,
        section = "Months",
        title = "The dated events of the 1380s grouped by month of the year",
        originalTitle = "Dated events list",
        author = "No single author",
        year = 1381,
        summary = "A list of eight dated events of the 1380s is grouped by the month of the year, and a short program counts the events in each month.",
        details = "The months are those of the dates as the sources give them, which are Julian dates for the 1380s, so the grouping can differ from a modern calendar by a few days at the edge of a month. June holds the most events of the list.",
        instructions = """
            1. Save the example as events_by_month.py.
            2. Run it: python3 events_by_month.py
        """,
        example = """
            from collections import defaultdict

            events = [
                (1380, 6, 21, "battle of Chioggia"),
                (1380, 9, 8, "battle of Kulikovo"),
                (1380, 9, 16, "Charles VI becomes king of France"),
                (1381, 6, 14, "Savoy Palace sacked in London"),
                (1381, 6, 15, "Wat Tyler killed at Smithfield"),
                (1385, 8, 14, "battle of Aljubarrota"),
                (1386, 7, 9, "battle of Sempach"),
                (1389, 6, 15, "battle of Kosovo"),
            ]

            by_month = defaultdict(list)
            for year, month, day, label in events:
                by_month[month].append(label)
            for month in sorted(by_month):
                print("month", month, len(by_month[month]), "events")
            print("events in all:", len(events))
        """,
    ),

    entry(
        databaseId = 66,
        kind = Kind.PROJECT,
        section = "Feasts",
        title = "The fixed feasts of the church year in 1386, sorted by day",
        originalTitle = "Fixed feasts",
        author = "Church calendar makers",
        year = 1386,
        summary = "Fourteen fixed feasts of the church year are sorted by their day in 1386, and a short program finds the longest gap between them.",
        details = "The feasts are fixed dates of the church calendar, such as Candlemas and the Assumption, and the example counts each feast's day of the year. The year 1386 is a common year in both the Julian and the Gregorian reckonings, so the day numbers are the same in each.",
        instructions = """
            1. Save the example as church_feasts.py.
            2. Run it: python3 church_feasts.py
        """,
        example = """
            from datetime import date

            FEASTS = [
                ("Epiphany", 1, 6),
                ("Candlemas", 2, 2),
                ("St George", 4, 23),
                ("Holy Cross", 5, 3),
                ("St James", 7, 25),
                ("St Lawrence", 8, 10),
                ("Assumption", 8, 15),
                ("Nativity of Mary", 9, 8),
                ("All Saints", 11, 1),
                ("St Martin", 11, 11),
                ("St Andrew", 11, 30),
                ("St Nicholas", 12, 6),
                ("St Lucy", 12, 13),
                ("Holy Innocents", 12, 28),
            ]

            dated = sorted((date(1386, month, day).timetuple().tm_yday, name) for name, month, day in FEASTS)
            for yday, name in dated:
                print(yday, name)
            gaps = [(later - earlier, name) for (earlier, _), (later, name) in zip(dated, dated[1:])]
            longest = max(gaps)
            print("longest gap:", longest[0], "days, before", longest[1])
            first_half = sum(1 for yday, _ in dated if yday <= 181)
            print("feasts in the first half of 1386:", first_half)
            print("feasts in the second half of 1386:", len(dated) - first_half)
        """,
    ),

)
