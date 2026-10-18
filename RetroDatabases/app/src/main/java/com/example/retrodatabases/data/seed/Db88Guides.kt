package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1160 to 1170: the jury counts of the Assize of Clarendon. */
val db88Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 88,
        kind = Kind.GUIDE,
        section = "Juries",
        title = "How the Assize of Clarendon sets the size of its presenting juries",
        originalTitle = "Assisa de Clarendon",
        author = "Henry II of England",
        year = 1166,
        summary = "The Assize of Clarendon names twelve men from each hundred and four from each township, and a short program adds these numbers for two counties.",
        details = "The example uses two made-up counties, one with three hundreds and five townships and one with two hundreds and nine townships, so the figures show the rule without claiming real county data. The numbers twelve and four are the ones the assize gives for its presenting juries.",
        instructions = """
            1. Save the example as assize_jurors.py.
            2. Run it: python3 assize_jurors.py
        """,
        example = """
            HUNDRED_MEN = 12   # men sworn from each hundred
            VILL_MEN = 4       # men sworn from each township
            counties = {"county A": (3, 5), "county B": (2, 9)}   # (hundreds, townships), made up for the example
            total = 0
            for name, (hundreds, vills) in counties.items():
                jurors = HUNDRED_MEN * hundreds + VILL_MEN * vills
                total += jurors
                print(name + ":", jurors, "jurors")
            print("total:", total)
        """,
    ),

)
