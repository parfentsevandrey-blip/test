package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1230 to 1236: spans of a life's work, a metal-type gap, a stay on an island, a school's building years and a drawing rate. */
val db81Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 81,
        kind = Kind.EXAMPLE,
        section = "Dates",
        title = "Years from the start of the Shobogenzo to Dogen's death",
        originalTitle = "Shobogenzo",
        author = "Dogen",
        year = 1231,
        summary = "Counting from 1231, when Dogen began the Shobogenzo, to his death in 1253 gives a span of 22 years, or 23 if both years are counted.",
        details = "The start year is the usual date for the beginning of the work. The example prints the span as a difference and as a count of years touched, and the two differ by one.",
        instructions = """
            1. Save the example as dogen_span.py.
            2. Run it: python3 dogen_span.py
        """,
        example = """
            start = 1231
            death = 1253
            print("years from the start to Dogen's death:", death - start)
            print("years counted inclusively:", death - start + 1)
        """,
    ),

    entry(
        databaseId = 81,
        kind = Kind.EXAMPLE,
        section = "Printing",
        title = "The gap from the claimed 1234 Goryeo metal printing to Jikji of 1377",
        originalTitle = "Jikji",
        author = "No single author",
        year = 1234,
        summary = "The claimed 1234 printing with metal type in Goryeo lies 143 years before Jikji of 1377, the oldest surviving metal-type book, and a short program computes the gap.",
        details = "Both dates are the ones usually given, 1234 for the lost Goryeo book and 1377 for the surviving Jikji. The example prints the gap as a difference of two years, which is all the arithmetic needs.",
        instructions = """
            1. Save the example as type_gap.py.
            2. Run it: python3 type_gap.py
        """,
        example = """
            claimed = 1234
            jikji = 1377
            print("years from the claimed 1234 printing to Jikji:", jikji - claimed)
        """,
    ),

    entry(
        databaseId = 81,
        kind = Kind.EXAMPLE,
        section = "Mongol war",
        title = "Years the Goryeo court stayed on Ganghwa island",
        originalTitle = "Ganghwa",
        author = "King Gojong of Goryeo",
        year = 1232,
        summary = "The Goryeo court moved to Ganghwa island in 1232 and returned to the mainland in 1270, so the stay lasted 38 years by simple subtraction.",
        details = "Both years are the ones usually given for the move and the return, and the example counts the years as a difference. The stay covers the whole time the Tripitaka Koreana was carved, which began in 1236 and was finished in 1251.",
        instructions = """
            1. Save the example as ganghwa_years.py.
            2. Run it: python3 ganghwa_years.py
        """,
        example = """
            moved = 1232
            returned = 1270
            print("years on Ganghwa island:", returned - moved)
        """,
    ),

    entry(
        databaseId = 81,
        kind = Kind.EXAMPLE,
        section = "Iraq",
        title = "Years from the start of the Mustansiriya's building to its opening",
        originalTitle = "Al-Mustansiriya",
        author = "Caliph al-Mustansir",
        year = 1233,
        summary = "Building the Mustansiriya school in Baghdad began in 1227 and the school opened in 1233, so the work took six years by simple subtraction.",
        details = "The start and opening years are the usual dates, and accounts differ on when particular parts were finished, so the six years are a rough count. The example prints the difference so the arithmetic can be checked.",
        instructions = """
            1. Save the example as mustansiriya_years.py.
            2. Run it: python3 mustansiriya_years.py
        """,
        example = """
            begun = 1227
            opened = 1233
            print("years from the start of building to the opening:", opened - begun)
        """,
    ),

    entry(
        databaseId = 81,
        kind = Kind.EXAMPLE,
        section = "Drawing",
        title = "Drawings per leaf in Villard's album, from its usual counts",
        originalTitle = "Villard's album",
        author = "Villard de Honnecourt",
        year = 1230,
        summary = "Villard de Honnecourt's album of about 250 drawings on 33 leaves averages a little under eight drawings a leaf, and a short program divides the two counts.",
        details = "Both counts are the usual ones for the album, and both are approximate, since the number of drawings depends on how a figure with several parts is counted. The example prints the rate rounded to one decimal place.",
        instructions = """
            1. Save the example as leaves.py.
            2. Run it: python3 leaves.py
        """,
        example = """
            drawings = 250
            leaves = 33
            print("drawings:", drawings)
            print("leaves:", leaves)
            print("drawings per leaf:", round(drawings / leaves, 1))
        """,
    ),

)
