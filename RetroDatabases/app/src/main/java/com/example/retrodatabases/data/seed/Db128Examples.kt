package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One example of 760 to 770: the million Hyakumanto pagodas shared among ten temples and six years, counted in code. */
val db128Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 128,
        kind = Kind.EXAMPLE,
        section = "Japanese",
        title = "The million Hyakumanto pagodas, counted out to ten temples and across six years in code",
        originalTitle = "Hyakumanto Darani",
        author = "Empress Shotoku",
        year = 764,
        summary = "A short program shares the million Hyakumanto pagodas evenly among ten temples and over six years, and prints the remainders.",
        details = "No division of the pagodas among the temples is known, so the even shares are an illustration only. The six years match the span from 764 to about 770, and the program prints each share with the remainder left over.",
        rare = true,
        instructions = """
            1. Save the example as pagoda_shares.py.
            2. Run it: python3 pagoda_shares.py
        """,
        example = """
            PAGODAS = 1000000  # the million miniature pagodas ordered in 764
            TEMPLES = 10       # the ten leading temples that received them
            YEARS = 6          # the six years of work, from 764 to about 770
            per_temple, left = divmod(PAGODAS, TEMPLES)
            print("pagodas per temple if shared evenly:", per_temple, "left over:", left)
            per_year, left_year = divmod(PAGODAS, YEARS)
            print("pagodas per year if spread evenly over six years:", per_year, "left over:", left_year)
        """,
    ),

)
