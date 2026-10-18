package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1210 to 1220: the floor of Chomei's ten-foot hut in shaku and metres, and the acres of a field forty by twenty-five rods. */
val db83Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 83,
        kind = Kind.EXAMPLE,
        section = "Architecture",
        title = "The ten-foot square hut of the Hojoki in square shaku and metres",
        originalTitle = "Hojo, a square of one jo",
        author = "Kamo no Chomei",
        year = 1212,
        summary = "A hut one jo square has 100 square shaku of floor, and a short program turns its side and floor into metres and counts the mats that fit.",
        details = "The example takes one shaku as 0.3 metre, a round figure, and it uses a mat of six by three shaku as an illustration rather than a fixed standard. The name of the hut, hojo, means a square of one jo, and one jo is ten shaku.",
        instructions = """
            1. Save the example as hut.py.
            2. Run it: python3 hut.py
        """,
        example = """
            SHAKU_IN_METRES = 0.3    # a round figure for the example
            side_shaku = 10          # one jo is ten shaku, so the hut is one jo square
            area_shaku = side_shaku * side_shaku
            print("hut area in square shaku:", area_shaku)
            side_m = side_shaku * SHAKU_IN_METRES
            print("side in metres:", round(side_m, 1))
            print("area in square metres:", round(side_m * side_m, 1))
            mat_area = 6 * 3                 # an illustrative mat of six by three shaku
            print("full mats of 6 by 3 shaku:", area_shaku // mat_area)
            print("square shaku left over:", area_shaku % mat_area)
        """,
    ),

    entry(
        databaseId = 83,
        kind = Kind.EXAMPLE,
        section = "Land",
        title = "Acres in a field of forty by twenty-five rods",
        originalTitle = "Rod and acre",
        author = "Surveyors of the period",
        year = 1220,
        summary = "A field forty rods long and twenty-five rods wide holds 1000 square rods, which is six and a quarter acres, and a short program makes the sum.",
        details = "One acre is 160 square rods, the standard English measure, and the example works the area out by multiplying the two sides. Fibonacci's Practica geometriae of about 1220 belongs to the same surveying tradition, though the field is only an illustration.",
        instructions = """
            1. Save the example as acres.py.
            2. Run it: python3 acres.py
        """,
        example = """
            length_rods = 40
            width_rods = 25
            ROD_SQUARES_PER_ACRE = 160
            area = length_rods * width_rods
            print("area in square rods:", area)
            print("acres:", area / ROD_SQUARES_PER_ACRE)
        """,
    ),

)
