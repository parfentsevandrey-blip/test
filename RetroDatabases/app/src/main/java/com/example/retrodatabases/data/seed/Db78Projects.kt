package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1260 to 1270: a short program that sorts three Latin translations of Greek science and prints the years between them. */
val db78Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 78,
        kind = Kind.PROJECT,
        section = "Translations",
        title = "Gaps between the Latin translations of Ptolemy, Archimedes and Euclid",
        originalTitle = "Latin translations of Greek science",
        author = "Gerard of Cremona, William of Moerbeke and Federico Commandino",
        year = 1269,
        summary = "A short program sorts three Latin translations of Greek science by their dates and prints the years between them.",
        details = "Gerard's Almagest is dated to about 1175 and Commandino's Latin Euclid to 1572, both as commonly given, and Moerbeke's Archimedes of 1269 falls between them. The Almagest reached Latin from Arabic, so the years count the arrival of each text in Latin rather than a translation from Greek.",
        instructions = """
            1. Save the example as translation_gaps.py.
            2. Run it: python3 translation_gaps.py
        """,
        example = """
            translations = [
                ("Almagest, by Gerard of Cremona", 1175),
                ("Archimedes, by William of Moerbeke", 1269),
                ("Euclid, by Federico Commandino", 1572),
            ]
            years = sorted(year for _, year in translations)
            print("translations listed:", len(translations))
            for earlier, later in zip(years, years[1:]):
                print("years between:", later - earlier)
        """,
    ),

)
