package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1200 to 1210: a short check that the first reigns of the Delhi sultanate follow one another. */
val db84Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 84,
        kind = Kind.PROJECT,
        section = "Succession",
        title = "Checking that the early sultans of Delhi reigned in turn",
        originalTitle = "Reign list",
        author = "The sultans of Delhi",
        year = 1206,
        summary = "A short program takes four reigns of the early Delhi sultanate, prints each reign's length and the gap between each pair of reigns, so an overlap would show as a negative number.",
        details = "The reign dates are the ones commonly given for these four rulers, and the list is used only to test the program's logic on a short run. A gap of zero means that one reign ended in the year the next began.",
        instructions = """
            1. Save the example as sultans_reigns.py.
            2. Run it: python3 sultans_reigns.py
        """,
        example = """
            reigns = [
                ("Aibak", 1206, 1210),
                ("Aram Shah", 1210, 1211),
                ("Iltutmish", 1211, 1236),
                ("Razia", 1236, 1240),
            ]
            for name, start, end in reigns:
                print(name, "reign length:", end - start)
            for (name_a, _, end), (name_b, start, _) in zip(reigns, reigns[1:]):
                print("gap between", name_a, "and", name_b + ":", start - end)
            print("reigns checked:", len(reigns))
        """,
    ),

)
