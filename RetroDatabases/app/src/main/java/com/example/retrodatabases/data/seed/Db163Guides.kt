package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 410 to 418: the Roman names of the twelfths of an as, from the uncia to the as, written out as code. */
val db163Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 163,
        kind = Kind.GUIDE,
        section = "Roman measures",
        title = "How the Roman names of the twelfths of an as are read off, from the uncia to the as",
        originalTitle = "Partes unciae",
        author = "Roman surveyors and bookkeepers",
        year = 410,
        summary = "The Roman as was divided into twelve unciae, and each number of twelfths had its own name, which a short program prints from one twelfth to the whole.",
        details = "Roman accounts and inheritance shares were written in twelfths of the as, and each fraction had a name such as semis for six twelfths and dodrans for nine. The sources do not date these names to 410, so 410, the year of the sack of Rome, is only the placed year of this record and not the year of the names.",
        instructions = """
            1. Save the example as twelfths.py.
            2. Run it: python3 twelfths.py
        """,
        example = """
            NAMES = {
                1: "uncia",
                2: "sextans",
                3: "quadrans",
                4: "triens",
                5: "quincunx",
                6: "semis",
                7: "septunx",
                8: "bes",
                9: "dodrans",
                10: "dextans",
                11: "deunx",
                12: "as",
            }
            for twelfths in range(1, 13):
                print(twelfths, "twelfths is", NAMES[twelfths])
            print("9 twelfths plus 3 twelfths is", 9 + 3, "twelfths, the", NAMES[9 + 3])
        """,
        rare = true,
    ),

)
