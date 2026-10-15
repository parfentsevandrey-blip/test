package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1500 to 1510: distances from latitude differences, a ship's provisions, the Julian Easter dates and the shares of a sea venture. */
val db54Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 54,
        kind = Kind.PROJECT,
        section = "Navigation",
        title = "Distances along a meridian from latitude differences",
        originalTitle = "Mapa mundi",
        author = "Juan de la Cosa",
        year = 1500,
        summary = "A difference in latitude gives the north-south distance of a voyage, and a short program turns three illustrative latitude differences into miles at sixty to the degree.",
        details = "A pilot judged a north-south leg from the difference in latitude between two ports, and Juan de la Cosa's chart of 1500 belongs to the same practice of charting. The example uses sixty miles to the degree as a simple rule, and its ports are invented for the purpose.",
        instructions = """
            1. Save the example as meridian_legs.py.
            2. Run it: python3 meridian_legs.py
        """,
        example = """
            # Illustrative latitudes in degrees and minutes, not the positions of real ports.
            MILES_PER_DEGREE = 60

            legs = [
                ("port A", (36, 0), "port B", (40, 30)),
                ("port B", (40, 30), "port C", (38, 0)),
                ("port C", (38, 0), "port D", (43, 15)),
            ]
            for start, from_lat, end, to_lat in legs:
                degrees = (to_lat[0] + to_lat[1] / 60) - (from_lat[0] + from_lat[1] / 60)
                print(f"{start} to {end}: {abs(degrees) * MILES_PER_DEGREE:.1f} miles")
        """,
    ),

    entry(
        databaseId = 54,
        kind = Kind.PROJECT,
        section = "Voyages",
        title = "Provisioning a ship's crew for a voyage",
        originalTitle = "Ship's biscuit allowance",
        author = "Ships' provisioners",
        year = 1500,
        summary = "A ship's crew needs a fixed weight of biscuit for each man and day, and a short program totals the biscuit for a voyage and the casks that carry it.",
        details = "Ships of the early sixteenth century carried their biscuit in casks, and the crew's daily allowance set the size of the store. The crew, the days and the cask size are illustrative, so the output is fixed.",
        instructions = """
            1. Save the example as provisioning.py.
            2. Run it: python3 provisioning.py
        """,
        example = """
            # Illustrative figures: a crew, a voyage length, a daily ration of biscuit for each man, and the size of a cask.
            crew = 60
            days = 125
            ration = 1      # pounds of biscuit per man per day
            cask = 200      # pounds of biscuit in one cask
            total = crew * days * ration
            casks = -(-total // cask)
            print("biscuit needed:", total, "pounds")
            print("casks of 200 pounds:", casks)
            print("left over:", casks * cask - total, "pounds")
        """,
    ),

    entry(
        databaseId = 54,
        kind = Kind.PROJECT,
        section = "Church calendar",
        title = "Easter dates for 1500, 1505 and 1510 by the Julian reckoning",
        originalTitle = "Computus",
        author = "Church computists",
        year = 1500,
        summary = "The Julian reckoning finds the date of Easter from the year alone, and a short program works it out for three years of the decade.",
        details = "Church computists used rules of this kind to fix the feast days, in the Julian calendar that was in use across Catholic Europe in 1500. The example applies a modern form of the Julian computus to three years and prints the month and day.",
        instructions = """
            1. Save the example as easter_julian.py.
            2. Run it: python3 easter_julian.py
        """,
        example = """
            MONTHS = ["January", "February", "March", "April", "May", "June",
                      "July", "August", "September", "October", "November", "December"]

            def julian_easter(year):
                # A modern form of the Julian computus: the rule depends on the year alone.
                a = year % 4
                b = year % 7
                c = year % 19
                d = (19 * c + 15) % 30
                e = (2 * a + 4 * b - d + 34) % 7
                month = (d + e + 114) // 31
                day = (d + e + 114) % 31 + 1
                return month, day

            for year in [1500, 1505, 1510]:
                month, day = julian_easter(year)
                print(f"{year}: {MONTHS[month - 1]} {day}")
        """,
    ),

    entry(
        databaseId = 54,
        kind = Kind.PROJECT,
        section = "Trade",
        title = "Splitting a voyage's profit between partners",
        originalTitle = "Commenda",
        author = "Venetian merchants",
        year = 1500,
        summary = "A commenda gave the partners of a sea venture shares of the profit in fixed parts, and a short program splits the profit of two voyages among three partners.",
        details = "The commenda was a partnership used by Italian merchants to finance sea trade, and each investor's share of the profit was fixed in the agreement. The two voyages and their profits are illustrative, so the output is fixed.",
        instructions = """
            1. Save the example as commenda_shares.py.
            2. Run it: python3 commenda_shares.py
        """,
        example = """
            # Each partner gets the profit times his parts, divided by the total parts.
            voyages = [
                (1200, {"investor": 2, "merchant": 1, "crew": 1}),
                (1500, {"investor": 3, "merchant": 1, "crew": 1}),
            ]
            for number, (profit, shares) in enumerate(voyages, start=1):
                total = sum(shares.values())
                for partner, parts in shares.items():
                    print(f"voyage {number} {partner} {profit * parts // total}")
        """,
    ),

)
