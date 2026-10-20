package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One guide of 660 to 670: how one Tang catty nests into taels, mace and fen, a placed record with 660 as its anchor year. */
val db138Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 138,
        kind = Kind.GUIDE,
        section = "Tang",
        title = "How one Tang catty nests into taels, mace and fen, written out as code, placed at 660",
        originalTitle = "jin liang qian fen",
        author = "The Tang weights and measures",
        year = 660,
        summary = "One Tang catty is nested into sixteen taels, 160 qian and 1600 fen by a short program that prints the nesting for three weights.",
        details = "The nesting is a Tang weight rule: one jin is sixteen liang, a liang is ten qian, and a qian is ten fen. The record's year is an anchor and not the date of the weights: 660 is the year Empress Wu began to share the government while Gaozong was ill.",
        rare = true,
        instructions = """
            1. Save the example as tang_catty.py.
            2. Run it: python3 tang_catty.py
        """,
        example = """
            FEN_PER_QIAN = 10
            QIAN_PER_LIANG = 10
            LIANG_PER_JIN = 16

            def nest(jin):
                liang = jin * LIANG_PER_JIN
                qian = liang * QIAN_PER_LIANG
                fen = qian * FEN_PER_QIAN
                return liang, qian, fen

            for jin in (1, 3, 10):
                liang, qian, fen = nest(jin)
                print(jin, "jin =", liang, "liang =", qian, "qian =", fen, "fen")
        """,
    ),

)
