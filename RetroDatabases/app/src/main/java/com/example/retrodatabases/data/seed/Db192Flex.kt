package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 120 to 130: the years from Emperor Shun's accession, put at 125 or 126, to the founding of Antinopolis in 130. */
val db192Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 192,
        kind = Kind.FLEX,
        section = "Spans",
        title = "Years from Emperor Shun's accession to the founding of Antinopolis",
        originalTitle = "Years from Emperor Shun's accession to the founding of Antinopolis",
        author = "No single author",
        year = 125,
        summary = "Emperor Shun's accession is put at 125 or 126, and a short program counts the years from either year to the founding of Antinopolis in 130.",
        details = "Accounts differ on whether Shun's accession was in 125 or 126, so the example prints both spans, 5 years and 4 years. The founding of Antinopolis is given as 130, and the arithmetic is the only claim here, not the day of either event.",
        instructions = """
            1. Save the example as shun_antinopolis.py.
            2. Run it: python3 shun_antinopolis.py
        """,
        example = """
            founding = 130   # Antinopolis, as commonly given
            for accession in (125, 126):   # accounts differ on Shun's accession
                print("accession", accession, "to founding", founding, ":", founding - accession, "years")
        """,
        rare = true,
    ),

)
