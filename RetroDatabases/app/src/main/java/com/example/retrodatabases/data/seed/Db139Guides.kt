package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One guide of 650 to 660: the fangcheng method of the Nine Chapters, which solves three grades of grain by elimination, worked out by a program. */
val db139Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 139,
        kind = Kind.GUIDE,
        section = "Chinese",
        title = "How three grades of grain are found by elimination, the fangcheng method of the Nine Chapters, worked out by a program",
        originalTitle = "Jiuzhang suanshu",
        author = "Anonymous, Han period",
        year = 656,
        summary = "The fangcheng method, which finds the values of three grades of grain by elimination, comes from the Nine Chapters and is worked out here by a program.",
        details = "The sources do not date this method, which is far older than 656, so the year here is the anchor of the Tang edition of the Ten Canons that carries it, not the year of the method. Its grain problem gives 9 and a quarter, 4 and a quarter, and 2 and three quarters dou per bundle, and the program prints each figure with a check on the three totals.",
        rare = true,
        instructions = """
            1. Save the example as fangcheng_grain.py.
            2. Run it: python3 fangcheng_grain.py
        """,
        example = """
            from fractions import Fraction

            # Three fields of grain, one row each: the coefficients of the superior, medium and inferior bundles, then the total.
            FIELDS = [
                [Fraction(3), Fraction(2), Fraction(1), Fraction(39)],
                [Fraction(2), Fraction(3), Fraction(1), Fraction(34)],
                [Fraction(1), Fraction(2), Fraction(3), Fraction(26)],
            ]
            GRADES = ["superior", "medium", "inferior"]


            def solve(rows):
                size = len(rows)
                table = [list(row) for row in rows]
                for col in range(size):
                    pivot = table[col][col]
                    table[col] = [value / pivot for value in table[col]]
                    for other in range(size):
                        if other != col and table[other][col] != 0:
                            factor = table[other][col]
                            table[other] = [a - factor * b for a, b in zip(table[other], table[col])]
                return [table[i][size] for i in range(size)]


            answer = solve(FIELDS)
            for grade, value in zip(GRADES, answer):
                print(grade, "bundle:", float(value), "dou")
            for number, row in enumerate(FIELDS, start=1):
                total = sum(c * x for c, x in zip(row[:3], answer))
                print("field", number, "check:", total)
        """,
    ),

)
