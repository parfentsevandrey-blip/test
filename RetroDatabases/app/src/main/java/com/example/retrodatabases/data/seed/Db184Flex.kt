package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 200 to 210: one line of arithmetic from the Severan marble plan of 203 to Cao Cao's order of 210. */
val db184Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 184,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Years from the Severan marble plan to Cao Cao's order",
        originalTitle = "Years between two dated events",
        author = "No single author",
        year = 203,
        summary = "The usual dates of the Severan marble plan and of Cao Cao's order give seven years between them, and a short program counts them.",
        details = "The plan's date of 203 is an inference from its setting, and 210 is the usual date of the order, so the count checks the arithmetic of two usual dates and is not an exact interval. The example uses plain years and makes no use of the calendar.",
        instructions = """
            1. Save the example as plan_order.py.
            2. Run it: python3 plan_order.py
        """,
        example = """
            plan = 203     # the Severan marble plan, usual dating
            order = 210    # Cao Cao's order on choosing men of talent, usual dating
            print("years from the marble plan to Cao Cao's order:", order - plan)
        """,
    ),

)
