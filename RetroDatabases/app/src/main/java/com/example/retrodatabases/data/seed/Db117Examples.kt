package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 870 to 880: the zero of the Gwalior tablet, its numbers 270 and 50 split into place values in code. */
val db117Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 117,
        kind = Kind.EXAMPLE,
        section = "India",
        title = "The numbers 270 and 50 of the Gwalior tablet, split into place values in code",
        originalTitle = "Gwalior numerals",
        author = "The carvers of the Gwalior tablet",
        year = 876,
        summary = "The Gwalior tablet writes 270 and 50 with a zero in the units place, and a short program splits each number into place values to show what the zero does.",
        details = "The zero adds nothing to either sum, but it keeps the 7 in the tens place of 270 and the 5 in the tens place of 50, so it works as a placeholder. The example prints each breakdown and counts the zeros, so the result can be checked by hand.",
        instructions = """
            1. Save the example as places.py.
            2. Run it: python3 places.py
        """,
        example = """
            def place_value_sum(number):
                digits = str(number)
                terms = []
                total = 0
                for index, digit in enumerate(digits):
                    place = 10 ** (len(digits) - index - 1)
                    terms.append(f"{digit} x {place}")
                    total += int(digit) * place
                print(f"{number}: " + " + ".join(terms) + f" = {total}")
                return total

            place_value_sum(270)
            place_value_sum(50)
            zeros = str(270).count("0") + str(50).count("0")
            print("zeros in the two numbers:", zeros)
        """,
    ),

)
