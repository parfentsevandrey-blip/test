package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One guide of 1120 to 1130: how Hugh of St Victor's classification of the arts is set out in order. */
val db92Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 92,
        kind = Kind.GUIDE,
        section = "Arts",
        title = "How Hugh of St Victor sorts the arts into four parts and seven mechanical crafts",
        originalTitle = "Didascalicon de studio legendi",
        author = "Hugh of St Victor",
        year = 1125,
        summary = "Hugh of St Victor's Didascalicon divides philosophy into four parts and names seven mechanical arts, and a short program prints the division in order.",
        details = "The four parts are theoretical, practical, mechanical and logical, and the seven mechanical arts are weaving, armour-making, navigation, farming, hunting, medicine and theatre. The example prints both lists with their counts, so the output can be checked against the names.",
        instructions = """
            1. Save the example as hugh_arts.py.
            2. Run it: python3 hugh_arts.py
        """,
        example = """
            parts = ["theoretical", "practical", "mechanical", "logical"]
            arts = ["weaving", "armour-making", "navigation", "farming", "hunting", "medicine", "theatre"]
            for number, part in enumerate(parts, start=1):
                print(f"part {number}: {part}")
            print("parts of philosophy:", len(parts))
            for number, art in enumerate(arts, start=1):
                print(f"mechanical art {number}: {art}")
            print("mechanical arts:", len(arts))
        """,
    ),

)
