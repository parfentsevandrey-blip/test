package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 30 to 40: four dated events of the 30s, sorted by year and spaced in code. */
val db201Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 201,
        kind = Kind.PROJECT,
        section = "Events",
        title = "Four dated events of 31 to 37 in Rome and Chengdu, sorted and spaced in code",
        originalTitle = "Four dated events",
        author = "No single author",
        year = 37,
        summary = "Four dated events of the 30s are put in order by year, and a short program prints the gap between each one and the one before.",
        details = "The events are Sejanus's execution in 31, Tiberius naming Caligula and Gemellus joint heirs in 35, the death of Gongsun Shu in 36 and the death of Tiberius in 37. The program uses only the years, so the gaps are counts of years and not of days, and accounts differ on the months of some of these events and on the year of Sejanus's fall, which some texts put about 29.",
        instructions = """
            1. Save the example as dated_events.py.
            2. Run it: python3 dated_events.py
        """,
        example = """
            EVENTS = [
                (31, "Sejanus executed"),
                (35, "Tiberius names Caligula and Gemellus joint heirs"),
                (36, "Gongsun Shu killed"),
                (37, "Tiberius dies"),
            ]
            ordered = sorted(EVENTS)
            previous = None
            for year, label in ordered:
                note = "first event" if previous is None else "gap " + str(year - previous)
                print(f"{year}: {label} ({note})")
                previous = year
            print("span of the four events:", ordered[-1][0] - ordered[0][0], "years")
        """,
        rare = true,
    ),

)
