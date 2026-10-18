package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1170: a planning sum for an illustrative pilgrimage road to Santiago, stage by stage. */
val db87Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 87,
        kind = Kind.PROJECT,
        section = "Roads",
        title = "Counting the days of a pilgrimage road to Santiago, stage by stage",
        originalTitle = "Pilgrimage road",
        author = "Pilgrims on the Way of Saint James",
        year = 1170,
        summary = "A pilgrimage road is planned as a list of stages, and a short program adds up the miles and the days of an illustrative road to Santiago de Compostela.",
        details = "The stage lengths and the daily distance are illustrative and are not taken from a surviving itinerary, so only the arithmetic is meant to be checked. Each stage is rounded up to whole days of travel.",
        instructions = """
            1. Save the example as pilgrim_road.py.
            2. Run it: python3 pilgrim_road.py
        """,
        example = """
            # illustrative stages of a road to Santiago, in miles, not from a surviving itinerary
            STAGES = [("first stage", 120), ("second stage", 90), ("third stage", 150)]
            MILES_PER_DAY = 30   # illustrative daily distance
            total_miles = 0
            total_days = 0
            for name, miles in STAGES:
                days = -(-miles // MILES_PER_DAY)   # whole days of travel, rounded up
                total_miles += miles
                total_days += days
                print(name, miles, "miles", days, "days")
            print("total miles", total_miles)
            print("total days", total_days)
        """,
    ),

)
