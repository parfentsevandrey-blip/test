package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One example of 800 to 810: the reign of Charlemagne from 768 to 814, split at the death of his brother Carloman in 771. */
val db124Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 124,
        kind = Kind.EXAMPLE,
        section = "Frankish",
        title = "Charlemagne's reign of 46 years, split at the death of his brother Carloman in 771",
        originalTitle = "Years of a reign split at a brother's death",
        author = "Charlemagne",
        year = 800,
        summary = "Charlemagne's reign ran from 768 to his death in 814, and a short program splits it at the death of his brother Carloman in 771 into 3 years shared and 43 as sole king.",
        details = "He succeeded his father Pippin in 768 and shared the kingdom with Carloman until Carloman died at the end of 771, so the first three years of the count are the shared ones. He died at Aachen in January 814, and the program uses the usual dates of the brother's death and of Charlemagne's own.",
        instructions = """
            1. Save the example as charlemagne_reign.py.
            2. Run it: python3 charlemagne_reign.py
        """,
        example = """
            ACCESSION = 768       # king of the Franks after his father Pippin
            CARLOMAN_DEATH = 771  # the brother's death leaves Charlemagne sole king
            DEATH = 814           # died at Aachen
            print("years shared with Carloman:", CARLOMAN_DEATH - ACCESSION)
            print("years as sole king:", DEATH - CARLOMAN_DEATH)
            print("reign in all:", DEATH - ACCESSION)
        """,
    ),

)
