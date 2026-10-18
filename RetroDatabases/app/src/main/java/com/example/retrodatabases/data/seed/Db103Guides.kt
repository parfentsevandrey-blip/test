package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1010: al-Karaji's argument that the cubes of the first whole numbers add up to a square, written out as a short program. */
val db103Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 103,
        kind = Kind.GUIDE,
        section = "Algebra",
        title = "How al-Karaji's argument shows the cubes of 1 to n add up to a square",
        originalTitle = "How al-Karaji's argument shows the cubes of 1 to n add up to a square",
        author = "Abu Bakr al-Karaji",
        year = 1010,
        summary = "Al-Karaji's argument, as later writers give it, shows that the cubes of the first n whole numbers add up to the square of their running sum, and a short program checks the steps for n up to 6 and at n = 10.",
        details = "At each step the running sum grows by n, and the square of the running sum grows by exactly n cubed, so the two totals match for every n. The case n = 10 gives 3025, which is 55 squared, and whether this is induction in al-Karaji's own sense is a matter of interpretation.",
        rare = true,
        instructions = """
            1. Save the example as cubes.py.
            2. Run it: python3 cubes.py
        """,
        example = """
            total_cubes = 0
            running_sum = 0
            for n in range(1, 7):
                total_cubes += n ** 3
                running_sum += n
                print(n, total_cubes, running_sum * running_sum)
            print("n = 10:", sum(k ** 3 for k in range(1, 11)), (10 * 11 // 2) ** 2)
            print("cubes equal squares at n = 6:", total_cubes == running_sum * running_sum)
        """,
    ),

)
