package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One guide of 820 to 830: al-Khwarizmi's Hindu reckoning, written out as a column method for adding two numbers, dated about 825. */
val db122Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 122,
        kind = Kind.GUIDE,
        section = "Arithmetic",
        title = "How al-Khwarizmi's Hindu reckoning adds two numbers column by column, carrying the ten",
        originalTitle = "Kitab al-jam' wa'l-tafriq bi-hisab al-Hind",
        author = "Muhammad ibn Musa al-Khwarizmi",
        year = 825,
        summary = "Al-Khwarizmi's arithmetic on the Hindu numerals, dated about 825, adds numbers column by column from the units upward and carries a ten into the next column.",
        details = "The Arabic original is lost, and the work survives in a twelfth-century Latin version, Algoritmi de numero Indorum, whose title gave us the word algorithm. The date of about 825 is the usual one for the Arabic work, and the program follows the column method with its carries, printing each column as it goes.",
        instructions = """
            1. Save the example as hindu_columns.py.
            2. Run it: python3 hindu_columns.py
        """,
        example = """
            def add_by_columns(a, b):
                xs = [int(d) for d in reversed(str(a))]
                ys = [int(d) for d in reversed(str(b))]
                carry = 0
                digits = []
                for i in range(max(len(xs), len(ys))):
                    x = xs[i] if i < len(xs) else 0
                    y = ys[i] if i < len(ys) else 0
                    total = x + y + carry
                    print(f"column {i + 1}: {x} + {y} + carry {carry} = {total}, write {total % 10}, carry {total // 10}")
                    digits.append(total % 10)
                    carry = total // 10
                if carry:
                    digits.append(carry)
                return int("".join(str(d) for d in reversed(digits)))

            print("sum:", add_by_columns(825, 976))
            print("sum:", add_by_columns(999, 1))
        """,
        rare = true,
    ),

)
