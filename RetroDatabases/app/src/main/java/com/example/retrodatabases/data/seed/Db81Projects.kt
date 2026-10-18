package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1230: a surveyor's sum that turns three strip fields measured in rods into square rods, acres and square yards. */
val db81Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 81,
        kind = Kind.PROJECT,
        section = "Measure",
        title = "Acres in three strip fields, from their length and width in rods",
        originalTitle = "Acre and rod",
        author = "English surveyors of the thirteenth century",
        year = 1230,
        summary = "A surveyor measured strip fields in rods, and a short program turns three strips into square rods and acres, since one acre is 160 square rods.",
        details = "The rod or perch is 5.5 yards and the acre is 40 rods by 4 rods, the units that English surveyors used in the thirteenth century and later. The example adds the three strips in square rods, prints the acres and the square yards of one acre, and uses illustrative strip sizes, so the outputs can be checked by hand.",
        instructions = """
            1. Save the example as acres.py.
            2. Run it: python3 acres.py
        """,
        example = """
            ROD_YARDS = 5.5                      # one rod, or perch, is 5.5 yards
            ACRE_RODS = 40 * 4                   # one acre is 40 rods by 4 rods, so 160 square rods
            strips = [("north strip", 40, 4), ("south strip", 20, 3), ("river strip", 30, 5)]   # length and width in rods, illustrative
            total = 0
            for name, length, width in strips:
                square_rods = length * width
                total += square_rods
                print(name, square_rods, "square rods")
            print("total square rods:", total)
            print("square rods in one acre:", ACRE_RODS)
            print("acres:", total // ACRE_RODS, "remainder", total % ACRE_RODS, "square rods")
            print("square yards in one acre:", ACRE_RODS * ROD_YARDS * ROD_YARDS)
        """,
    ),

)
