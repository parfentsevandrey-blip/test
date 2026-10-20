package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 480 to 490: four years from Zeno's Henotikon of 482 to the battle of Soissons of 486. */
val db156Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 156,
        kind = Kind.FLEX,
        section = "Years",
        title = "Years from Zeno's Henotikon of 482 to Clovis's victory at Soissons of 486",
        originalTitle = "Years between two events",
        author = "No single author",
        year = 486,
        summary = "The Henotikon of 482 and the battle of Soissons of 486 are four years apart, and a one-line program prints that gap.",
        details = "Both years are given as usually stated, and the program subtracts the earlier from the later, so the answer is four years. The span covers the years 483 to 485, in which the quarrel over the decree began in 484.",
        instructions = """
            1. Save the example as henotikon_soissons.py.
            2. Run it: python3 henotikon_soissons.py
        """,
        example = """
            henotikon = 482   # Zeno's Henotikon, as usually dated
            soissons = 486    # Clovis's victory at Soissons, as usually dated
            print("years from the Henotikon to Soissons:", soissons - henotikon)
        """,
    ),

)
