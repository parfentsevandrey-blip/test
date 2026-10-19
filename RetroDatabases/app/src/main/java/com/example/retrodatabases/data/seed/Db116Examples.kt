package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One example of 885: the years Choe Chiwon spent in the Tang, from his departure in 868 to his return in 885, counted in code. */
val db116Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 116,
        kind = Kind.EXAMPLE,
        section = "Silla",
        title = "The years Choe Chiwon spent in the Tang, counted from his departure to his return",
        originalTitle = "Choe Chiwon's years in the Tang",
        author = "Choe Chiwon",
        year = 885,
        summary = "Choe Chiwon, the Silla scholar, spent seventeen years in the Tang between his departure in 868 and his return in 885, and a short program counts the span.",
        details = "The departure year 868 is the one usually given for his journey as a youth, and 885 is the year of his return to Silla. The Gyewon pilgyong was put together in the year after the return, and the example prints the span and the gap from the return to that year, so the counting can be checked by hand.",
        instructions = """
            1. Save the example as choe_years.py.
            2. Run it: python3 choe_years.py
        """,
        example = """
            LEFT_SILLA = 868       # Choe Chiwon's departure for the Tang, as usually given
            RETURNED = 885         # his return to Silla
            COLLECTION = 886       # the year the Gyewon pilgyong is usually dated
            print("years in the Tang:", RETURNED - LEFT_SILLA)
            print("years from his return to the collection:", COLLECTION - RETURNED)
        """,
    ),

)
