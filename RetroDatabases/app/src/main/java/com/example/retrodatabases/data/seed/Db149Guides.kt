package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One guide of 550 to 560: the old rule of three-fold increase and decrease that gives the pitch pipes from the yellow bell, placed at 550. */
val db149Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 149,
        kind = Kind.GUIDE,
        section = "Chinese",
        title = "How the first pitch pipes follow from the yellow bell by thirds, worked out by a short program",
        originalTitle = "sun yi (three-fold increase and decrease)",
        author = "Early Chinese music theorists",
        year = 550,
        summary = "The old Chinese rule of three-fold increase and decrease gives each pitch pipe from the yellow bell, 81 units, by multiplying by two thirds or four thirds in turn.",
        details = "The method is older than the Northern Qi by many centuries, so 550 is the year of the anchor, the founding of the Northern Qi, and not the date of the method. The program applies the two steps in turn and prints the first five lengths.",
        rare = true,
        instructions = """
            1. Save the example as pitch_pipes.py.
            2. Run it: python3 pitch_pipes.py
        """,
        example = """
            # Three-fold increase and decrease: each pipe is two thirds (sun) or four thirds (yi) of the one before.
            STEPS = [("Linzhong", "sun"), ("Taicu", "yi"), ("Nanlu", "sun"), ("Guxi", "yi")]

            length = 81
            print("Huangzhong:", length)
            for name, step in STEPS:
                if step == "sun":
                    length = length * 2 // 3
                else:
                    length = length * 4 // 3
                print(name + ":", length)
        """,
    ),

)
