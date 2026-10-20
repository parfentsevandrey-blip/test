package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 274: how a grain total is split into Roman modii and sextarii, written out as a short program. */
val db177Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 177,
        kind = Kind.GUIDE,
        section = "Measures",
        title = "How a grain total is split into modii and sextarii",
        originalTitle = "Modius and sextarius",
        author = "Roman officials and traders",
        year = 274,
        summary = "Roman dry measures counted grain in sextarii, with sixteen sextarii to the modius and forty-eight to the amphora, and a short program splits a total into modii and sextarii.",
        details = "The modius was the usual Roman dry measure for grain, and the amphora was one cubic foot, so the steps are 16 and 48 sextarii; local measures differed, and the example uses the usual figures only to show the arithmetic. The sources do not date this method, so 274 is the year of the anchor, the Ticinum issues with officina numbers, and not the year of the measure.",
        rare = true,
        instructions = """
            1. Save the example as roman_measures.py.
            2. Run it: python3 roman_measures.py
        """,
        example = """
            SEXTARII_PER_MODIUS = 16     # the usual Roman dry measure for grain
            SEXTARII_PER_AMPHORA = 48    # one cubic foot, the usual figure
            total = 120                  # an illustrative total of sextarii
            modii, rest = divmod(total, SEXTARII_PER_MODIUS)
            amphorae, rest_amphora = divmod(total, SEXTARII_PER_AMPHORA)
            print("total sextarii:", total)
            print("modii and sextarii:", modii, rest)
            print("amphorae and sextarii:", amphorae, rest_amphora)
        """,
    ),

)
