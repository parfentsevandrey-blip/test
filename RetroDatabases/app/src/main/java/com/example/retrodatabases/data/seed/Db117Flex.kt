package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 870 to 880: the seven years from the Treaty of Meerssen to the Capitulary of Quierzy. */
val db117Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 117,
        kind = Kind.FLEX,
        section = "Years",
        title = "Years from the Treaty of Meerssen to the Capitulary of Quierzy",
        originalTitle = "Years between two Frankish acts",
        author = "Louis the German and Charles the Bald",
        year = 870,
        summary = "The Treaty of Meerssen was agreed in 870 and the Capitulary of Quierzy was issued in 877, and a short program counts the 7 years between the two Frankish acts.",
        details = "Both dates are the usual ones, and the example prints the difference so the result can be checked by hand. Louis the German and Charles the Bald made the treaty, and Charles alone issued the capitulary, so the span falls within his reign.",
        instructions = """
            1. Save the example as meerssen.py.
            2. Run it: python3 meerssen.py
        """,
        example = """
            MEERSSEN = 870   # the treaty dividing Lotharingia, as commonly dated
            QUIERZY = 877    # the capitulary issued by Charles the Bald, as commonly dated
            print("years from Meerssen to Quierzy:", QUIERZY - MEERSSEN)
        """,
    ),

)
