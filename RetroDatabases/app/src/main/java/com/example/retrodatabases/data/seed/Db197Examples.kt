package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One example of 70 to 80: the eleven years of the decade written in Roman numerals, counted letter by letter in code. */
val db197Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 197,
        kind = Kind.EXAMPLE,
        section = "Numerals",
        title = "The Roman numerals of the years 70 to 80, counted letter by letter",
        originalTitle = "Numeri Romani",
        author = "Roman scribes",
        year = 79,
        summary = "Written in Roman numerals, the eleven years from 70 to 80 take 54 letters in all, and a short program converts each year and counts its letters.",
        details = "The program uses the standard subtractive rule with the letters I, V, X, L, C, D and M, so the year of the eruption, 79, is written LXXIX with five letters. The decade total is fixed, so it can be checked by hand.",
        rare = true,
        instructions = """
            1. Save the example as numerals.py.
            2. Run it: python3 numerals.py
        """,
        example = """
            PAIRS = [(1000, "M"), (900, "CM"), (500, "D"), (400, "CD"), (100, "C"), (90, "XC"),
                     (50, "L"), (40, "XL"), (10, "X"), (9, "IX"), (5, "V"), (4, "IV"), (1, "I")]

            def roman(number):
                text = ""
                for value, letters in PAIRS:
                    while number >= value:
                        text += letters
                        number -= value
                return text

            total = 0
            for year in range(70, 81):
                numeral = roman(year)
                total += len(numeral)
                print(year, numeral, len(numeral))
            print("letters in all:", total)
        """,
    ),

)
