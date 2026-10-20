package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 30 to 40: the years 30 to 40 written in Roman numerals, with their letters counted in code. */
val db201Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 201,
        kind = Kind.EXAMPLE,
        section = "Numerals",
        title = "The years 30 to 40 written in Roman numerals, with their letters counted in code",
        originalTitle = "Roman numerals",
        author = "Roman scribes and inscribers",
        year = 40,
        summary = "The years 30 to 40 written in Roman numerals use 52 letters in all, and a short program converts each year and counts its letters.",
        details = "The converter uses the subtractive pairs such as IV and XL, and it prints each year with its numeral and its letter count. The total is the sum of eleven small counts, so each line can be verified by hand against the list.",
        instructions = """
            1. Save the example as roman_years.py.
            2. Run it: python3 roman_years.py
        """,
        example = """
            VALUES = [(1000, "M"), (900, "CM"), (500, "D"), (400, "CD"), (100, "C"), (90, "XC"),
                      (50, "L"), (40, "XL"), (10, "X"), (9, "IX"), (5, "V"), (4, "IV"), (1, "I")]

            def roman(n):
                out = ""
                for value, symbol in VALUES:
                    while n >= value:
                        out += symbol
                        n -= value
                return out

            total = 0
            for year in range(30, 41):
                numeral = roman(year)
                total += len(numeral)
                print(year, numeral, len(numeral))
            print("letters in all:", total)
        """,
        rare = true,
    ),

)
