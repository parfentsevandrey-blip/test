package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1103: Chinese length units that nest in tens, converted in code from zhang and chi into fen. */
val db94Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 94,
        kind = Kind.EXAMPLE,
        section = "Measures",
        title = "Turning zhang, chi and cun into fen, the decimal lengths of Song building",
        originalTitle = "Zhang, chi, cun and fen",
        author = "No single author",
        year = 1103,
        summary = "Chinese length units nest in tens, so a short program turns an illustrative length of 3 zhang, 2 chi and 5 cun into 3250 fen.",
        details = "Ten fen made a cun, ten cun a chi and ten chi a zhang, the decimal nesting that Chinese length units had in the Song period. The example uses an illustrative length rather than a dimension from the Yingzao Fashi, and its output can be checked by hand.",
        rare = true,
        instructions = """
            1. Save the example as song_units.py.
            2. Run it: python3 song_units.py
        """,
        example = """
            FEN_PER_CUN = 10
            CUN_PER_CHI = 10
            CHI_PER_ZHANG = 10

            zhang, chi, cun = 3, 2, 5   # an illustrative length, not a figure from the manual
            cun_total = (zhang * CHI_PER_ZHANG + chi) * CUN_PER_CHI + cun
            fen_total = cun_total * FEN_PER_CUN
            print("cun:", cun_total)
            print("fen:", fen_total)
        """,
    ),

)
