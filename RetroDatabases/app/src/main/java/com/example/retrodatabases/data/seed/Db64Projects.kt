package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1403 to 1405: a bronze metal ledger, Zheng He's voyage span, a kango tally ledger and the months of 1404. */
val db64Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 64,
        kind = Kind.PROJECT,
        section = "Metals",
        title = "Copper and tin on hand for a bronze type run",
        originalTitle = "Metal ledger",
        author = "Founders of the Joseon court",
        year = 1403,
        summary = "A ledger for a bronze type run adds the copper and tin received and subtracts the amount cast, and a short program prints the balance for an illustrative run.",
        details = "Accounts say that Korea lacked enough domestic copper for the Gyemija alloy, and financing was a main obstacle. The figures in the example are illustrative and are not the court's accounts, so the balance is a model of the bookkeeping only.",
        instructions = """
            1. Save the example as metal_ledger.py.
            2. Run it: python3 metal_ledger.py
        """,
        example = """
            received = {"copper": 120, "tin": 30}   # illustrative weights received
            cast = {"copper": 95, "tin": 24}        # illustrative weights cast into type
            for metal in sorted(received):
                print(metal, "balance", received[metal] - cast[metal])
            print("total balance", sum(received.values()) - sum(cast.values()))
        """,
    ),

    entry(
        databaseId = 64,
        kind = Kind.PROJECT,
        section = "Voyages",
        title = "Counting the years of Zheng He's seven voyages",
        originalTitle = "Zheng He's treasure-fleet voyages",
        author = "Zheng He, for the Yongle emperor",
        year = 1405,
        summary = "Zheng He's seven voyages span from 1405 to 1433, and a short program divides that span by the number of voyages.",
        details = "The voyages were ordered by the Yongle emperor, the first began in 1405, and the last ended in 1433. The example uses the span from the first year to the last, not the length of each voyage, which the sources give in different ways.",
        instructions = """
            1. Save the example as voyages.py.
            2. Run it: python3 voyages.py
        """,
        example = """
            voyages = 7
            first_year = 1405
            last_year = 1433   # the seventh voyage ended in this year, as commonly given
            print("voyages", voyages)
            print("span in years", last_year - first_year)
            print("average years per voyage", (last_year - first_year) / voyages)
        """,
    ),

    entry(
        databaseId = 64,
        kind = Kind.PROJECT,
        section = "Trade",
        title = "A ledger that pairs kango tallies by their numbers",
        originalTitle = "Kango ledger",
        author = "Officials of the Ming and Japanese sides",
        year = 1404,
        summary = "A ledger pairs each ship's tally number with the matching number kept by the other side, and a short program lists which tallies match and which are left over.",
        details = "Only a pair of halves that fitted together was admitted, so a ledger of tally numbers shows which ships had a partner and which did not. The numbers in the example are illustrative and do not come from any surviving tally.",
        instructions = """
            1. Save the example as ledger.py.
            2. Run it: python3 ledger.py
        """,
        example = """
            ships = [101, 102, 104, 107]        # tally numbers carried by ships, illustrative
            offices = [101, 103, 104, 105]      # tally numbers kept by the other side, illustrative
            matched = sorted(set(ships) & set(offices))
            unmatched = sorted(set(ships) ^ set(offices))
            print("matched", matched)
            print("unmatched", unmatched)
        """,
        rare = true,
    ),

    entry(
        databaseId = 64,
        kind = Kind.PROJECT,
        section = "Calendars",
        title = "Days in each month of 1404, a leap year of the kango trade",
        originalTitle = "Month lengths",
        author = "Julian calendar reckoners",
        year = 1404,
        summary = "The months of 1404 are listed with their lengths, and a short program adds them to get the length of the year, which is a leap year under the Julian rule.",
        details = "The Julian rule makes 1404 a leap year, as it does every fourth year, so February has 29 days in that year. The example uses the standard month lengths and totals the year, so the total of 366 can be checked by hand.",
        instructions = """
            1. Save the example as months.py.
            2. Run it: python3 months.py
        """,
        example = """
            MONTHS = ["January", "February", "March", "April", "May", "June",
                      "July", "August", "September", "October", "November", "December"]
            DAYS = [31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]
            year = 1404
            if year % 4 == 0:
                DAYS[1] = 29
            for name, days in zip(MONTHS, DAYS):
                print(name, days)
            print("days in the year", sum(DAYS))
        """,
    ),

)
