package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One line each of arithmetic: from the battle of Maldon to Olaf Tryggvason's accession, and from Basil II's sole reign to the Venetian bull. */
val db105Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 105,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Years from the battle of Maldon to Olaf Tryggvason's accession in Norway",
        originalTitle = "Years from the battle of Maldon to Olaf Tryggvason's accession in Norway",
        author = "Byrhtnoth and Olaf Tryggvason",
        year = 995,
        summary = "One line of arithmetic counts the four years from the battle of Maldon in 991 to Olaf Tryggvason's accession in Norway in 995.",
        details = "Both dates are as commonly stated: the battle, where the ealdorman Byrhtnoth of Essex fell, in 991, and the accession in 995. The calculation tests the subtraction only, so the run tests the arithmetic rather than the events.",
        instructions = """
            1. Save the example as flex_maldon.py.
            2. Run it: python3 flex_maldon.py
        """,
        example = """
            maldon = 991
            olaf_accession = 995

            print("years from the battle of Maldon to Olaf Tryggvason's accession in Norway:", olaf_accession - maldon)
        """,
    ),

    entry(
        databaseId = 105,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Years from the start of Basil II's sole reign to the Venetian golden bull",
        originalTitle = "Years from the start of Basil II's sole reign to the Venetian golden bull",
        author = "Basil II",
        year = 992,
        summary = "One line of arithmetic counts the 16 years from the start of Basil II's sole reign in 976 to the golden bull for Venetian traders in 992.",
        details = "Both dates are as commonly stated: 976 for the start of Basil II's sole reign and 992 for the bull. The calculation tests the subtraction only, so the run tests the arithmetic rather than the events.",
        instructions = """
            1. Save the example as flex_basil.py.
            2. Run it: python3 flex_basil.py
        """,
        example = """
            basil_sole_reign = 976
            venice_bull = 992

            print("years from the start of Basil II's sole reign to the Venetian golden bull:", venice_bull - basil_sole_reign)
        """,
    ),

)
