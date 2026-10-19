package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One project of 325 to 787: the seven first ecumenical councils, from Nicaea I to Nicaea II, counted by city and by century in a short program. */
val db126Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 126,
        kind = Kind.PROJECT,
        section = "Councils",
        title = "The seven first ecumenical councils, from Nicaea I in 325 to Nicaea II in 787, counted by city and by century",
        originalTitle = "Seven ecumenical councils",
        author = "The bishops of the Christian church",
        year = 787,
        summary = "The seven first ecumenical councils, from Nicaea I in 325 to Nicaea II in 787, are counted by city and by century in a short program that prints the 462 years between the first and the last.",
        details = "Constantinople hosted three of the councils, Nicaea two, and Ephesus and Chalcedon one each, and the program groups the seven by the century in which they met. Only Nicaea II falls in the 780s, and the year 680 given for Constantinople III is the year it opened, though it ended in 681.",
        instructions = """
            1. Save the example as councils.py.
            2. Run it: python3 councils.py
        """,
        example = """
            COUNCILS = [
                ("Nicaea I", 325, "Nicaea"),
                ("Constantinople I", 381, "Constantinople"),
                ("Ephesus", 431, "Ephesus"),
                ("Chalcedon", 451, "Chalcedon"),
                ("Constantinople II", 553, "Constantinople"),
                ("Constantinople III", 680, "Constantinople"),
                ("Nicaea II", 787, "Nicaea"),
            ]
            print("councils:", len(COUNCILS))
            print("years from Nicaea I to Nicaea II:", COUNCILS[-1][1] - COUNCILS[0][1])
            cities = {}
            centuries = {}
            for name, year, city in COUNCILS:
                cities[city] = cities.get(city, 0) + 1
                century = (year - 1) // 100 + 1
                centuries[century] = centuries.get(century, 0) + 1
            for city in sorted(cities):
                print("held at", city + ":", cities[city])
            for century in sorted(centuries):
                print("century", century, "councils:", centuries[century])
        """,
    ),

)
