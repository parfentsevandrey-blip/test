package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One flex piece of 590 to 600: the years from Gregory the Great's election in 590 to Augustine's landing in Kent in 597. */
val db145Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 145,
        kind = Kind.FLEX,
        section = "Years",
        title = "Years from Gregory the Great's election in 590 to Augustine's landing in Kent in 597",
        originalTitle = "Years between two dated events",
        author = "Bede and the papal biographies",
        year = 590,
        summary = "Gregory the Great became pope in 590 and Augustine landed in Kent in 597, so the two dates are seven years apart, and a short program prints the gap.",
        details = "Both dates are the usual ones: Gregory was consecrated pope in September 590, and Bede dates Augustine's landing to 597. Gregory sent the mission in 596, so the seven years measure the gap between the election and the landing, not the time it took to send the mission.",
        instructions = """
            1. Save the example as gregory_augustine.py.
            2. Run it: python3 gregory_augustine.py
        """,
        example = """
            GREGORY_POPE = 590     # Gregory the Great becomes pope, as usually dated
            AUGUSTINE_LANDS = 597  # Augustine lands in Kent, as Bede dates it
            print("years from Gregory's election to Augustine's landing:", AUGUSTINE_LANDS - GREGORY_POPE)
        """,
    ),

)
