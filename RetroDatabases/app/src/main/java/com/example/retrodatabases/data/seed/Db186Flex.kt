package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 180 to 190: the years from the request for the stone classics in 175 to the Yellow Turban rising of 184. */
val db186Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 186,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Years from the request for the stone classics to the Yellow Turban rising",
        originalTitle = "Years between two dated events",
        author = "Cai Yong and Zhang Jue",
        year = 184,
        summary = "Cai Yong and his colleagues asked for the stone classics in 175, and the Yellow Turban rising began in 184, so a short program counts the nine years between them.",
        details = "The request and the rising are both dated in the sources, and the count is the plain difference between the two years. The example prints the nine years from one event to the other, so the result can be checked by hand.",
        instructions = """
            1. Save the example as stone_rising.py.
            2. Run it: python3 stone_rising.py
        """,
        example = """
            request = 175   # Cai Yong and his colleagues ask for the stone classics
            rising = 184    # the Yellow Turban rising begins
            print("years from the request to the rising:", rising - request)
        """,
        rare = true,
    ),

)
