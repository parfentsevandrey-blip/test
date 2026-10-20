package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One guide of 630 to 640: the camel zakat of the Abu Bakr letter, worked out band by band by a program, placed at 632. */
val db141Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 141,
        kind = Kind.GUIDE,
        section = "Arab",
        title = "How the camel zakat of the Abu Bakr letter is worked out for a herd, written as a program, placed at 632",
        originalTitle = "Kitab Abi Bakr",
        author = "Abu Bakr, carrying out a letter attributed to the Prophet",
        year = 632,
        summary = "A short program works out the camel zakat of the Abu Bakr letter band by band for herds of up to ninety camels.",
        details = "The letter sets a sheep for each five camels up to 24, a bint makhad (a she-camel in her second year) from 25 camels, a bint labun (third year) from 36, a hiqqah (fourth year) from 46, a jadha'ah (fifth year) from 61, and two bint labun from 76. The sources do not date the letter; 632, the year Abu Bakr became caliph, is the anchor used here, not the year the letter was written, and the schedule is recorded in the hadith collections of Bukhari and Tirmidhi.",
        rare = true,
        instructions = """
            1. Save the example as camel_zakat.py.
            2. Run it: python3 camel_zakat.py
        """,
        example = """
            BANDS = [
                (5, 9, "one sheep"),
                (10, 14, "two sheep"),
                (15, 19, "three sheep"),
                (20, 24, "four sheep"),
                (25, 35, "one bint makhad"),
                (36, 45, "one bint labun"),
                (46, 60, "one hiqqah"),
                (61, 75, "one jadha'ah"),
                (76, 90, "two bint labun"),
            ]

            def due(camels):
                if camels < 5:
                    return "nothing due"
                for low, high, animals in BANDS:
                    if low <= camels <= high:
                        return animals
                return "outside this table"

            for camels in (4, 5, 12, 24, 25, 35, 36, 45, 46, 60, 61, 75, 76, 90, 91):
                print(camels, "camels:", due(camels))
        """,
    ),

)
