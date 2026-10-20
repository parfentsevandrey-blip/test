package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 340 to 343: the three years from the death of Constantine II to the Council of Serdica. */
val db170Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 170,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Years from the death of Constantine II to the Council of Serdica",
        originalTitle = "Years between two events",
        author = "No single author",
        year = 343,
        summary = "Constantine II died in 340 and the Council of Serdica is dated 343 here, and a short program counts the three years between them.",
        details = "Constantine II died near Aquileia in 340, and the council is dated 343 here, although accounts give 342 to 344 for it. The span is computed from the years alone, so the answer depends on the year chosen for Serdica.",
        instructions = """
            1. Save the example as serdica_gap.py.
            2. Run it: python3 serdica_gap.py
        """,
        example = """
            constantine_ii_death = 340   # death near Aquileia, as commonly given
            serdica = 343                # the Council of Serdica, dated 343 here
            print("years from the death of Constantine II to Serdica:", serdica - constantine_ii_death)
        """,
    ),

)
