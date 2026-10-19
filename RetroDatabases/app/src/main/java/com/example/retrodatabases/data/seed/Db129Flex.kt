package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex piece of 750 to 760: the span from the battle of the Zab in 750 to the emirate of Cordoba in 756, computed in one line of code. */
val db129Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 129,
        kind = Kind.FLEX,
        section = "Umayyad",
        title = "The span from the battle of the Zab in 750 to the emirate of Cordoba in 756",
        originalTitle = "Span between two Umayyad events",
        author = "Marwan II and Abd al-Rahman I",
        year = 750,
        summary = "The battle of the Zab came in 750 and the emirate of Cordoba in 756, and a short program counts the six years between the two dates.",
        details = "The Zab ended the Umayyad caliphate of Damascus, and six years later a surviving Umayyad founded the emirate of Cordoba, so the span links the two events only through their dates. The program prints the difference, which a reader can check against the two dates.",
        instructions = """
            1. Save the example as zab_cordoba.py.
            2. Run it: python3 zab_cordoba.py
        """,
        example = """
            ZAB = 750       # the battle of the Zab, as commonly dated
            CORDOBA = 756   # the emirate of Cordoba, as commonly dated
            print("years from the Zab to the emirate of Cordoba:", CORDOBA - ZAB)
        """,
    ),

)
