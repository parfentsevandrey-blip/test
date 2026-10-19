package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex piece of 833: the years from al-Ma'mun's death to the move of the Abbasid court to Samarra. */
val db121Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 121,
        kind = Kind.FLEX,
        section = "Years",
        title = "Years from al-Ma'mun's death to the move of the Abbasid court to Samarra",
        originalTitle = "Years from al-Ma'mun's death to the move of the Abbasid court to Samarra",
        author = "Al-Ma'mun and al-Mu'tasim",
        year = 833,
        summary = "Al-Ma'mun died in 833 and the Abbasid court moved to Samarra in 836, and a short program counts the three years between the two events.",
        details = "The death of al-Ma'mun in August 833 passed the caliphate to al-Mu'tasim, who moved the court north to Samarra in 836. The example prints the span from the two years as commonly given.",
        instructions = """
            1. Save the example as ma_mun_span.py.
            2. Run it: python3 ma_mun_span.py
        """,
        example = """
            MAMUN_DEATH = 833     # al-Ma'mun dies in August 833, as commonly given
            SAMARRA_MOVE = 836    # the court moves to Samarra, as commonly given
            print("years from al-Ma'mun's death to the move to Samarra:", SAMARRA_MOVE - MAMUN_DEATH)
        """,
    ),

)
