package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One flex piece of 610 to 620: the years from Heraclius's accession in 610 to the Sasanian conquest of Egypt in 619. */
val db143Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 143,
        kind = Kind.FLEX,
        section = "Years",
        title = "Years from Heraclius's accession in 610 to the Sasanian conquest of Egypt in 619",
        originalTitle = "Years between two Byzantine and Persian events",
        author = "Heraclius and Khosrow II",
        year = 610,
        summary = "Heraclius's accession in 610 and the Sasanian conquest of Egypt in 619 are nine years apart, and a short program prints the gap.",
        details = "The two events belong to the war between Byzantium and Persia, but the span only compares the usual dates given for them. The program uses 619, the year Persian forces held Alexandria, and accounts differ on when that conquest began.",
        instructions = """
            1. Save the example as heraclius_egypt.py.
            2. Run it: python3 heraclius_egypt.py
        """,
        example = """
            ACCESSION = 610  # Heraclius overthrows Phocas, as usually dated
            EGYPT = 619      # the Sasanian conquest of Egypt, as usually dated
            print("years from the accession of Heraclius to the conquest of Egypt:", EGYPT - ACCESSION)
        """,
    ),

)
