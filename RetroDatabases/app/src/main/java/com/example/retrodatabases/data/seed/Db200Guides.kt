package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 40 to 50: how a Roman first name is spelled out from its sign, with five names of the period. */
val db200Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 200,
        kind = Kind.GUIDE,
        section = "Names",
        title = "How a Roman first name is spelled out from its abbreviation",
        originalTitle = "Praenomen abbreviations",
        author = "Roman scribes and stonecutters",
        year = 41,
        summary = "A Roman first name, or praenomen, was written as a short sign such as Ti. for Tiberius, and a short program spells out five names from a table of signs.",
        details = "The signs were in use for centuries before 41, and the example takes Ti. Claudius Caesar, the name of the emperor from 41, with four other Roman names. The year 41 is the year of the example name, not the date at which the table of signs was made.",
        instructions = """
            1. Save the example as praenomen.py.
            2. Run it: python3 praenomen.py
        """,
        example = """
            PRAENOMINA = {"Ti.": "Tiberius", "C.": "Gaius", "Cn.": "Gnaeus", "M.": "Marcus", "Q.": "Quintus"}
            NAMES = ["Ti. Claudius Caesar", "C. Iulius Caesar", "Cn. Pompeius", "M. Antonius", "Q. Hortensius"]
            for full in NAMES:
                sign, rest = full.split(" ", 1)
                print(PRAENOMINA.get(sign, "unknown"), rest)
            print("names expanded:", len(NAMES))
        """,
        rare = true,
    ),

)
