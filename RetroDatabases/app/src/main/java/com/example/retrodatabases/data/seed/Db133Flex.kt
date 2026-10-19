package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 710 to 720: the years from the Shitong of 710 to the Fudoki decree of 713, and from Tariq's landing in 711 to the Codex Amiatinus sent toward Rome in 716. */
val db133Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 133,
        kind = Kind.FLEX,
        section = "Years",
        title = "Years from the Shitong of 710 to the Fudoki decree of 713",
        originalTitle = "Years between two unrelated works",
        author = "Liu Zhiji",
        year = 710,
        summary = "The Shitong of 710 and the Fudoki decree of 713 are unrelated events, and a short program counts the three years between them.",
        details = "The two events had no connection, and the span only compares their usual dates, one in China and one in Japan. The program prints the difference, which a reader can check against the two dates.",
        instructions = """
            1. Save the example as shitong_fudoki.py.
            2. Run it: python3 shitong_fudoki.py
        """,
        example = """
            SHITONG = 710  # Liu Zhiji completes the Shitong, as commonly dated
            FUDOKI = 713   # the Fudoki decree of Empress Genmei, as commonly dated
            print("years from the Shitong to the Fudoki decree:", FUDOKI - SHITONG)
        """,
    ),

    entry(
        databaseId = 133,
        kind = Kind.FLEX,
        section = "Years",
        title = "Years from Tariq's landing in 711 to the Codex Amiatinus sent toward Rome in 716",
        originalTitle = "Years between two unrelated events",
        author = "Tariq ibn Ziyad",
        year = 711,
        summary = "Tariq ibn Ziyad landed in Spain in 711 and the Codex Amiatinus set out for Rome in 716, and a short program counts the five years between them.",
        details = "The two events had no connection, and the span only compares their usual dates, one in Spain and one in Northumbria. The count is an arithmetic comparison of two dates, not a claim about how the events were linked.",
        instructions = """
            1. Save the example as tariq_codex.py.
            2. Run it: python3 tariq_codex.py
        """,
        example = """
            LANDING = 711  # Tariq ibn Ziyad lands in Spain, as commonly dated
            CODEX = 716    # the Codex Amiatinus sets out for Rome, as commonly dated
            print("years from Tariq's landing to the Codex Amiatinus:", CODEX - LANDING)
        """,
    ),

)
