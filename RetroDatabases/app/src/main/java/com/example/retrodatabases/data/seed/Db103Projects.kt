package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1016: the reigns of three emperors of Japan from 986 to 1036, listed and totalled in code. */
val db103Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 103,
        kind = Kind.PROJECT,
        section = "Japan",
        title = "Reign lengths of Ichijo, Sanjo and Go-Ichijo, emperors of Japan from 986 to 1036",
        originalTitle = "Ichijo, Sanjo and Go-Ichijo",
        author = "The Heian court",
        year = 1016,
        summary = "A short program lists the reigns of three Heian emperors, totals their years and picks out the longest and the shortest reign.",
        details = "The reign dates used are the usual ones: Ichijo 986 to 1011, Sanjo 1011 to 1016 and Go-Ichijo 1016 to 1036, giving reigns of 25, 5 and 20 years. The example tests the arithmetic of the dates and says nothing about the politics of the period.",
        instructions = """
            1. Save the example as emperors.py.
            2. Run it: python3 emperors.py
        """,
        example = """
            REIGNS = [
                ("Ichijo", 986, 1011),
                ("Sanjo", 1011, 1016),
                ("Go-Ichijo", 1016, 1036),
            ]
            lengths = []
            for name, start, end in REIGNS:
                years = end - start
                lengths.append((years, name))
                print(name, years, "years")
            print("emperors:", len(REIGNS))
            print("total years:", sum(years for years, _ in lengths))
            print("longest:", max(lengths)[1], max(lengths)[0], "years")
            print("shortest:", min(lengths)[1], min(lengths)[0], "years")
        """,
    ),

)
