package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One example of 730 to 740: the twenty grades of the five punishments in the Kaiyuan Code of 737, counted by a short program. */
val db131Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 131,
        kind = Kind.EXAMPLE,
        section = "Penal",
        title = "The twenty grades of the five punishments in the Kaiyuan Code of 737, counted in code",
        originalTitle = "Wu xing ershi deng",
        author = "Tang government",
        year = 737,
        summary = "The five punishments of the Kaiyuan Code of 737 come in twenty grades, and a short program counts the grades, the strokes, the years of servitude, the li of exile and the death grades.",
        details = "The light beating runs from 10 to 50 strokes and the heavy beating from 60 to 100 strokes, five grades each, and penal servitude runs from one to three years in five grades. Exile to 2000, 2500 or 3000 li makes three grades, and death in two forms, strangling and beheading, makes two more, and the scale is the one the Tang codes of the seventh century set.",
        rare = true,
        instructions = """
            1. Save the example as five_punishments.py.
            2. Run it: python3 five_punishments.py
        """,
        example = """
            BEATING = [10, 20, 30, 40, 50]          # light beating, in strokes
            HEAVY_BEATING = [60, 70, 80, 90, 100]   # heavy beating, in strokes
            SERVITUDE_YEARS = [1, 1.5, 2, 2.5, 3]   # penal servitude, in years
            EXILE_LI = [2000, 2500, 3000]           # exile, in li
            DEATH = ["strangling", "beheading"]     # the two death grades

            grades = len(BEATING) + len(HEAVY_BEATING) + len(SERVITUDE_YEARS) + len(EXILE_LI) + len(DEATH)
            print("grades in all:", grades)
            print("strokes over the ten beating grades:", sum(BEATING) + sum(HEAVY_BEATING))
            print("years of servitude over its five grades:", sum(SERVITUDE_YEARS))
            print("li of exile over its three grades:", sum(EXILE_LI))
            print("death grades:", len(DEATH))
        """,
    ),

)
