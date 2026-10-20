package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 263: Liu Hui's Sea Island method, which finds the height of a far island from two poles and two sightlines. */
val db178Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 178,
        kind = Kind.GUIDE,
        section = "Surveying",
        title = "How the height of a far island is found from two poles and two sightlines",
        originalTitle = "Haidao suanjing",
        author = "Liu Hui",
        year = 263,
        summary = "The Sea Island Mathematical Manual finds the height of a far island from two poles and two sightlines, and a short program works the height from the withdrawals.",
        details = "The manual, dated to 263, sets two poles of three zhang, a thousand bu apart, and sights the island from ground level after backing off 123 bu from the front pole and 127 bu from the rear one. Working these data by similar triangles gives a height of 1255 bu, which is 4 li 55 bu, and the example works it out from the two withdrawals. The manual was later issued as a separate book, apart from the commentary.",
        instructions = """
            1. Save the example as sea_island.py.
            2. Run it: python3 sea_island.py
        """,
        example = """
            # Units as the manual uses them: 1 zhang is 10 chi, 1 bu is 6 chi, 1 li is 300 bu.
            POLE_BU = 5          # a pole of 3 zhang, which is 30 chi, is 5 bu
            SPACING_BU = 1000    # the two poles stand 1000 bu apart
            FRONT_BU = 123       # the observer backs off 123 bu from the front pole
            REAR_BU = 127        # and 127 bu from the rear pole

            height = POLE_BU + POLE_BU * SPACING_BU // (REAR_BU - FRONT_BU)
            li, bu = divmod(height, 300)
            print("island height in bu:", height)
            print(f"island height: {li} li {bu} bu")
        """,
        rare = true,
    ),

)
