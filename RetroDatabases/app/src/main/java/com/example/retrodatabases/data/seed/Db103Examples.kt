package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1012: the Danegeld of 1012 counted in pennies, with pounds of silver turned into pence in code. */
val db103Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 103,
        kind = Kind.EXAMPLE,
        section = "Money",
        title = "The Danegeld of 1012 counted in pennies, 48000 pounds at 240 pennies to the pound",
        originalTitle = "Danegeld of 1012",
        author = "Aethelred II's government",
        year = 1012,
        summary = "The Danegeld paid in 1012 is recorded as 48000 pounds, and a short program turns that sum into pennies at 240 to the pound.",
        details = "An Anglo-Saxon pound of silver was reckoned at 240 pennies, so the sum comes to 11520000 pennies. The figure is the one commonly given for the payment, and the example tests the conversion rather than the payment itself.",
        instructions = """
            1. Save the example as danegeld_pennies.py.
            2. Run it: python3 danegeld_pennies.py
        """,
        example = """
            PENCE_PER_POUND = 240
            pounds = 48000                       # the Danegeld of 1012, as commonly given
            pennies = pounds * PENCE_PER_POUND
            print("pounds:", pounds)
            print("pennies:", pennies)
        """,
    ),

)
