package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 790 to 800: the years from the Council of Frankfurt in 794 to the attack on Pope Leo III in 799. */
val db125Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 125,
        kind = Kind.FLEX,
        section = "Years",
        title = "Years from the Council of Frankfurt in 794 to the attack on Pope Leo III in 799",
        originalTitle = "Years between two Frankish events",
        author = "Charlemagne",
        year = 794,
        summary = "The Council of Frankfurt met in 794 and the attack on Pope Leo III came in 799, and a short program counts the five years between the two dates.",
        details = "Both dates are the usual ones, and the span only compares them: the council met under Charlemagne's direction, and the attack came in Rome in April 799. The program prints the difference, which a reader can check against the two dates.",
        instructions = """
            1. Save the example as frankfurt_leo.py.
            2. Run it: python3 frankfurt_leo.py
        """,
        example = """
            FRANKFURT = 794   # the council of Frankfurt, as commonly dated
            LEO_ATTACK = 799  # the attack on Pope Leo III in Rome, as commonly dated
            print("years from the council of Frankfurt to the attack on Leo III:", LEO_ATTACK - FRANKFURT)
        """,
    ),

)
