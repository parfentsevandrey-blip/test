package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One guide of 330 to 340: how a Roman amphora's capacity follows from its cubic foot, with the congius and sextarius, written out as code and placed at 332. */
val db171Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 171,
        kind = Kind.GUIDE,
        section = "Measures",
        title = "How a Roman amphora's capacity follows from the cubic foot, with the congius and sextarius",
        originalTitle = "amphora quadrantal",
        author = "Roman measurers of wine and oil",
        year = 332,
        summary = "A Roman amphora was one cubic foot, so a short program turns the foot into litres and divides the amphora into congii and sextarii.",
        details = "The method is much older than this year, and 332 is the placed year, the year of Constantine's campaign against the Goths, not the year of the measure. The Roman foot is taken as 296 millimetres, a round figure, and the program prints the capacities that follow from the cube of that foot, which can be checked by hand.",
        instructions = """
            1. Save the example as amphora.py.
            2. Run it: python3 amphora.py
        """,
        example = """
            FOOT_MM = 296                       # the Roman foot, taken as 296 millimetres
            CUBIC_FOOT_MM3 = FOOT_MM ** 3       # one cubic foot, which the amphora was defined to be
            AMPHORA = CUBIC_FOOT_MM3 / 1000000  # cubic millimetres to litres
            print("cubic foot in cubic millimetres:", CUBIC_FOOT_MM3)
            print("amphora: {:.2f} litres".format(AMPHORA))
            print("congius (one eighth of an amphora): {:.2f} litres".format(AMPHORA / 8))
            print("sextarius (one forty-eighth of an amphora): {:.2f} litres".format(AMPHORA / 48))
        """,
        rare = true,
    ),

)
