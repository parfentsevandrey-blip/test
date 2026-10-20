package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One flex piece of 670 to 680: the years from the Kogo household register of 670 to the Greek fire of about 673. */
val db137Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 137,
        kind = Kind.FLEX,
        section = "Years",
        title = "Years from the Kogo household register of 670 to the Greek fire devised about 673",
        originalTitle = "Years between two dated items",
        author = "Emperor Tenji's government",
        year = 670,
        summary = "The Kogo household register of 670 and the Greek fire devised about 673 are three years apart, and a short program prints the gap.",
        details = "The two items are unrelated, so the span only compares the dates usually given for them. The program prints the difference, which a reader can check against the two dates.",
        instructions = """
            1. Save the example as register_fire.py.
            2. Run it: python3 register_fire.py
        """,
        example = """
            REGISTER = 670  # the Kogo household register, as commonly dated
            FIRE = 673      # the Greek fire devised about 673, as commonly dated
            print("years from the register of 670 to the Greek fire of 673:", FIRE - REGISTER)
        """,
    ),

)
