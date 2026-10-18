package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1099: a short program that sorts three dated steps of the First Crusade, from Nicaea to Jerusalem, and prints the days between them. */
val db95Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 95,
        kind = Kind.PROJECT,
        section = "Crusade",
        title = "Days between three dated steps of the First Crusade, from Nicaea to Jerusalem",
        originalTitle = "Days between the steps of the First Crusade",
        author = "The crusading army of the First Crusade",
        year = 1099,
        summary = "A short program sorts three dated steps of the First Crusade, the surrender of Nicaea, the taking of Antioch and the fall of Jerusalem, and prints the days between each pair of neighbouring steps and the whole span.",
        details = "The dates are the usual ones for the surrender of Nicaea, the taking of Antioch and the fall of Jerusalem, and they are given as commonly stated. No century year falls inside the span, so the day counts are the same in the Julian and the Gregorian reckoning.",
        instructions = """
            1. Save the example as crusade_steps.py.
            2. Run it: python3 crusade_steps.py
        """,
        example = """
            from datetime import date

            steps = [
                ("taking of Antioch", date(1098, 6, 3)),
                ("surrender of Nicaea", date(1097, 6, 19)),
                ("fall of Jerusalem", date(1099, 7, 15)),
            ]
            steps.sort(key=lambda step: step[1])
            for (name_a, day_a), (name_b, day_b) in zip(steps, steps[1:]):
                print(f"{name_a} to {name_b}: {(day_b - day_a).days} days")
            print(f"from Nicaea to Jerusalem: {(steps[-1][1] - steps[0][1]).days} days")
        """,
    ),

)
