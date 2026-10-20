package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One guide of the 450s: the five great sees in rank order, with Constantinople second after Rome, as Chalcedon's canon gives it. */
val db159Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 159,
        kind = Kind.GUIDE,
        section = "Church",
        title = "How the five great sees are put in rank order, with Constantinople second after Rome",
        originalTitle = "Pentarchia",
        author = "The council of Chalcedon and later imperial law",
        year = 451,
        summary = "A short program sets the five great sees in rank order, with Rome first and Constantinople second, the place that the council gave to New Rome.",
        details = "Canon twenty-eight of Chalcedon gave the see of Constantinople the second place after Rome, because the city was New Rome. The full list of five patriarchal sees, with Alexandria, Antioch and Jerusalem in the later places, comes from imperial law of the sixth century, so the year 451 belongs to the second-place rule alone.",
        instructions = """
            1. Save the example as sees.py.
            2. Run it: python3 sees.py
        """,
        example = """
            old_rome = "Rome"
            new_rome = "Constantinople"
            others = ["Alexandria", "Antioch", "Jerusalem"]
            order = [old_rome, new_rome] + others
            for rank, city in enumerate(order, start=1):
                print(rank, city)
            print("seats in the order:", len(order))
            print("Constantinople second:", order[1] == new_rome)
        """,
        rare = true,
    ),

)
