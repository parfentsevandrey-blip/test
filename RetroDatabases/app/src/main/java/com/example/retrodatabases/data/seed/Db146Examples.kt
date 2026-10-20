package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One example of 580 to 590: the 24 years that the Sui ran without commanderies, from their abolition in 583 to their return in 607. */
val db146Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 146,
        kind = Kind.EXAMPLE,
        section = "Chinese",
        title = "The 24 years that the Sui ran without commanderies, from their abolition in 583 to their return in 607, counted in code",
        originalTitle = "jun (commandery)",
        author = "Emperor Wen and Emperor Yang of Sui",
        year = 583,
        summary = "The Sui abolished the commandery tier in 583 and Emperor Yang restored it in 607, a span of 24 years that a short program counts.",
        details = "One account credits the reform of 583 to the official Su Wei, and the change left prefectures and counties as the two local tiers, although some modern maps render the upper tier as provinces. The figure counts the years between two dates, not the years in which the system worked as the reform intended.",
        rare = true,
        instructions = """
            1. Save the example as commandery_span.py.
            2. Run it: python3 commandery_span.py
        """,
        example = """
            ABOLISHED = 583  # Emperor Wen abolishes the commandery tier
            RESTORED = 607   # Emperor Yang brings the commanderies back
            print("years without commanderies:", RESTORED - ABOLISHED)
        """,
    ),

)
