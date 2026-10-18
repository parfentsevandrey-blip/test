package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex records of 910 to 917: one line of arithmetic each about two dated events of the decade. */
val db113Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 113,
        kind = Kind.FLEX,
        section = "Spans",
        title = "Years from the Cluny charter to the founding of Goryeo",
        originalTitle = "Years from the Cluny charter to the founding of Goryeo",
        author = "William I of Aquitaine and Wang Geon",
        year = 910,
        summary = "The Cluny charter of 910 and the founding of Goryeo in 918 are eight years apart, and one line of code prints that gap.",
        details = "The program subtracts the two years as commonly dated and prints the difference. Both dates are years only, so no day or calendar rule affects the result.",
        instructions = """
            1. Save the example as cluny_goryeo.py.
            2. Run it: python3 cluny_goryeo.py
        """,
        example = """
            cluny = 910     # the Cluny charter, dated in 910
            goryeo = 918    # the founding of Goryeo by Wang Geon
            print("years from the Cluny charter to the founding of Goryeo:", goryeo - cluny)
        """,
    ),

    entry(
        databaseId = 113,
        kind = Kind.FLEX,
        section = "Spans",
        title = "Years from the Fatimid enthronement at Raqqada to the order to build Mahdia",
        originalTitle = "Years from the Fatimid enthronement at Raqqada to the order to build Mahdia",
        author = "Ubayd Allah al-Mahdi and the Fatimid court",
        year = 910,
        summary = "The Fatimid enthronement of 910 and the order to build Mahdia in 916 are six years apart, and one line of code prints that gap.",
        details = "Mahdia's construction is dated to 916 in the sources, and some accounts tie its use as the capital to 921, which the program does not count. The program uses the years only, so the month of the enthronement and the day of the order do not affect the result.",
        instructions = """
            1. Save the example as fatimid_mahdia.py.
            2. Run it: python3 fatimid_mahdia.py
        """,
        example = """
            enthronement = 910   # Ubayd Allah al-Mahdi enthroned at Raqqada, January 910
            mahdia = 916         # the order to build Mahdia, as commonly dated
            print("years from the enthronement at Raqqada to the order to build Mahdia:", mahdia - enthronement)
        """,
    ),

    entry(
        databaseId = 113,
        kind = Kind.FLEX,
        section = "Spans",
        title = "Years from the founding of Southern Han in 917 to its surrender in 971",
        originalTitle = "Years from the founding of Southern Han in 917 to its surrender in 971",
        author = "Liu Yan and Liu Chang",
        year = 917,
        summary = "The Southern Han state lasted 54 years from its founding in 917 to its surrender in 971, and the program prints that span and its inclusive count.",
        details = "The span is counted as the end year minus the founding year, which gives 54, and counted inclusively, with both years included, it gives 55. The program prints both counts so that the convention is visible in the output.",
        rare = true,
        instructions = """
            1. Save the example as southern_han.py.
            2. Run it: python3 southern_han.py
        """,
        example = """
            founded = 917     # Southern Han set up at Guangzhou
            surrender = 971   # the last ruler surrenders to the Song
            print("years from 917 to 971:", surrender - founded)
            print("counted inclusively:", surrender - founded + 1)
        """,
    ),

)
