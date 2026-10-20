package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One flex piece of 520 to 530: the four years from Dionysius's Easter table of 525 to the Second Council of Orange of 529. */
val db152Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 152,
        kind = Kind.FLEX,
        section = "Years",
        title = "Years from Dionysius's Easter table of 525 to the Second Council of Orange of 529",
        originalTitle = "Years from Dionysius's Easter table of 525 to the Second Council of Orange of 529",
        author = "Dionysius Exiguus and the bishops of Orange",
        year = 525,
        summary = "Dionysius's Easter table of 525 and the Second Council of Orange of 529 are four years apart, and a short program prints the gap.",
        details = "Both dates are on record, but they belong to two separate acts of the church, the table and the council, so the four years compare dates and do not show a link between them. The program only subtracts the two years.",
        rare = true,
        instructions = """
            1. Save the example as table_orange.py.
            2. Run it: python3 table_orange.py
        """,
        example = """
            TABLE = 525   # Dionysius's Easter table, which counts years from the Incarnation
            ORANGE = 529  # the Second Council of Orange
            print("years from the Easter table of 525 to the council of Orange of 529:", ORANGE - TABLE)
        """,
    ),

)
