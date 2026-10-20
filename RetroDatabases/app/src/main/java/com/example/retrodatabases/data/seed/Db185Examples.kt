package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 190 to 200: the five men usually counted among the emperors of 193, the Year of the Five Emperors. */
val db185Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 185,
        kind = Kind.EXAMPLE,
        section = "Emperors",
        title = "The five men counted among the emperors of 193, the Year of the Five Emperors",
        originalTitle = "Year of the Five Emperors",
        author = "No single author",
        year = 193,
        summary = "Five men are usually counted among the emperors of 193, and a short program counts them and prints their names in alphabetical order.",
        details = "The five are Pertinax, Didius Julianus, Pescennius Niger, Clodius Albinus and Septimius Severus, and historians count them together as the Year of the Five Emperors. Albinus held the title of Caesar in 193 and his claim to the throne came later, so the count follows the usual list rather than the titles each man held that year.",
        instructions = """
            1. Save the example as five_emperors.py.
            2. Run it: python3 five_emperors.py
        """,
        example = """
            emperors_of_193 = [
                "Pertinax",
                "Didius Julianus",
                "Pescennius Niger",
                "Clodius Albinus",
                "Septimius Severus",
            ]
            print("emperors of 193:", len(emperors_of_193))
            for name in sorted(emperors_of_193):
                print(name)
        """,
        rare = true,
    ),

)
