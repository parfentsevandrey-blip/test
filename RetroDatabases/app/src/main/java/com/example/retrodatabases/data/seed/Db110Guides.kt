package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** A guide of 947: how a route given in farsakhs and miles is totalled in miles, with the farsakh of three miles used by Arab and Persian geographers. */
val db110Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 110,
        kind = Kind.GUIDE,
        section = "Geography",
        title = "How a route of farsakhs and miles is totalled in miles",
        originalTitle = "Farsakh and mil",
        author = "Arab and Persian geographers",
        year = 947,
        summary = "A route given in farsakhs and miles is totalled in miles by multiplying each farsakh by three and adding the miles, a conversion of Arabic and Persian distance reckoning.",
        details = "The sources do not date this method, so 947 is the placed year: it is the year usually given for the completion of al-Mas'udi's Meadows of Gold, a geographical history. The rule that one farsakh equals three miles is the usual convention of the period.",
        rare = true,
        instructions = """
            1. Save the example as route_miles.py.
            2. Run it: python3 route_miles.py
        """,
        example = """
            # a route as (farsakhs, miles) pairs, one pair per stage
            stages = [(3, 2), (5, 1), (0, 4), (2, 0)]
            FARSAKH_IN_MILES = 3
            total = 0
            for farsakhs, miles in stages:
                total += farsakhs * FARSAKH_IN_MILES + miles
            print("stages:", len(stages))
            print("total in miles:", total)
        """,
    ),

)
