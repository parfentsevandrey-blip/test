package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 290 to 300: the aroura, a land square of Roman Egypt, counted in cubits and metres, placed at 297 because the measure is not dated. */
val db175Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 175,
        kind = Kind.EXAMPLE,
        section = "Measures",
        title = "The aroura, an Egyptian land square of 100 cubits a side, written as code",
        originalTitle = "Aroura",
        author = "Land surveyors of Roman Egypt",
        year = 297,
        summary = "The aroura, the land unit of Roman Egypt, was a square of 100 cubits a side, and a short program counts its square cubits and square metres.",
        details = "The sources do not date this measure in the 290s; 297 is the year of the anchor, the census in Egypt, not the year the measure was fixed. The example takes the royal cubit as 0.525 metres, a common modern value, and the length of the cubit varied by period and place.",
        instructions = """
            1. Save the example as aroura.py.
            2. Run it: python3 aroura.py
        """,
        example = """
            CUBIT_METRES = 0.525     # a royal cubit, illustrative: cubit lengths varied by period and place
            SIDE_CUBITS = 100        # an aroura was a square of 100 cubits on each side
            square_cubits = SIDE_CUBITS * SIDE_CUBITS
            print("square cubits in an aroura:", square_cubits)
            print("square metres in an aroura:", f"{square_cubits * CUBIT_METRES ** 2:.0f}")
        """,
        rare = true,
    ),

)
