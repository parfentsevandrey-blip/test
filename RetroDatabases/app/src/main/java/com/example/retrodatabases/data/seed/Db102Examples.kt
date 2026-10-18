package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1020 to 1030: the years of Mahmud of Ghazni's reign, counted in code. */
val db102Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 102,
        kind = Kind.EXAMPLE,
        section = "Rulers",
        title = "Mahmud of Ghazni's reign, 998 to 1030, counted in years",
        originalTitle = "Mahmud of Ghazni's reign, 998 to 1030, counted in years",
        author = "Mahmud of Ghazni",
        year = 1030,
        summary = "Mahmud of Ghazni is usually dated from 998 to his death in 1030, a reign of 32 years.",
        details = "The example counts the years between the usual accession and death dates, and it checks only that arithmetic, not his campaigns or his court. Ghazna was the seat of the Ghaznavid rulers, and al-Biruni's Kitab al-Tafhim is dated there in 1029.",
        instructions = """
            1. Save the example as mahmud_reign.py.
            2. Run it: python3 mahmud_reign.py
        """,
        example = """
            accession = 998     # Mahmud's accession, as commonly given
            death = 1030        # Mahmud's death, as commonly given
            print("years from accession to death:", death - accession)
        """,
    ),

)
