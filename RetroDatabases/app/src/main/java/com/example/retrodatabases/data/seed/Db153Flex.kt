package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One flex piece of 510 to 520: the thirteen years from Shen Yue's death in 513 to the death of Theoderic in 526. */
val db153Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 153,
        kind = Kind.FLEX,
        section = "Years",
        title = "Years from Shen Yue's death in 513 to the death of Theoderic in 526, thirteen years",
        originalTitle = "Two deaths of the early sixth century",
        author = "Chinese dynastic historians and Ostrogothic chroniclers",
        year = 513,
        summary = "Shen Yue died in 513 and Theoderic in 526, thirteen years apart, a gap that a one-line program prints.",
        details = "Shen Yue, the Liang scholar whose rules of tone shaped later regulated verse, died in 513, and Theoderic, king of the Ostrogoths in Italy, died in 526, both dates as usually given. The program only subtracts the two years, and the answer would change if a different death date were used for either man.",
        rare = true,
        instructions = """
            1. Save the example as shen_theoderic.py.
            2. Run it: python3 shen_theoderic.py
        """,
        example = """
            SHEN_YUE_DEATH = 513   # the year Shen Yue died, as usually given
            THEODERIC_DEATH = 526  # the year Theoderic died, as usually given
            print("years from Shen Yue's death to Theoderic's:", THEODERIC_DEATH - SHEN_YUE_DEATH)
        """,
    ),

)
