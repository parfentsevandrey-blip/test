package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One example of 860 to 870: the years between the recorded dates of the Roda Nilometer, from 715 to its rebuild of 861. */
val db118Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 118,
        kind = Kind.EXAMPLE,
        section = "Nile",
        title = "The years between the recorded dates of the Roda Nilometer, counted in code",
        originalTitle = "Miqyas al-Nil",
        author = "Usama ibn Zayd and the Abbasid builders",
        year = 861,
        summary = "The years between the recorded dates of the Roda gauge, from its first version in 715 to the rebuild of 861, add up to 146, and a short program prints each interval.",
        details = "The first gauge on the island is usually dated to 715, and some accounts put a restoration in 815 and a flood that damaged the structure in 850. The intervals are 100, 35 and 11 years, and the example prints them with the total of 146 years from 715 to 861. The 815 and 850 dates come from some accounts only, so the total is less secure than the 715 and 861 dates.",
        rare = true,
        instructions = """
            1. Save the example as nilometer_years.py.
            2. Run it: python3 nilometer_years.py
        """,
        example = """
            events = [
                ("gauge", 715),
                ("restoration", 815),
                ("flood", 850),
                ("rebuild", 861),
            ]
            for (name_a, year_a), (name_b, year_b) in zip(events, events[1:]):
                print(name_a, "to", name_b + ":", year_b - year_a, "years")
            print("gauge to rebuild:", events[-1][1] - events[0][1], "years")
        """,
    ),

)
