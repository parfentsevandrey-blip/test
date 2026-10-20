package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 84: a legionary's yearly pay worked out in paydays of 75 denarii, and the rise of a third when a fourth payday is added. */
val db196Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 196,
        kind = Kind.GUIDE,
        section = "Pay",
        title = "How a legionary's yearly pay rises by a third with a fourth payday",
        originalTitle = "Stipendium",
        author = "Roman army paymasters",
        year = 84,
        summary = "A legionary's yearly pay of 225 denarii, paid on three paydays, rose by a third to 300 when a fourth payday was added, and a short program works out the sums.",
        details = "The change is usually dated to 84, and accounts differ on who set the 225-denarius rate, with Augustus and Marius both named. The program uses the 75-denarius payday, so every sum can be checked by hand.",
        instructions = """
            1. Save the example as stipend.py.
            2. Run it: python3 stipend.py
        """,
        example = """
            from fractions import Fraction

            PAYDAY = 75        # denarii on each payday, as the sums are usually given
            OLD_PAYDAYS = 3    # three paydays made up the old yearly pay
            NEW_PAYDAYS = 4    # the fourth payday added under Domitian

            old_year = PAYDAY * OLD_PAYDAYS
            new_year = PAYDAY * NEW_PAYDAYS
            print("old yearly pay in denarii:", old_year)
            print("new yearly pay in denarii:", new_year)
            print("rise in denarii:", new_year - old_year)
            print("rise as a fraction of the old pay:", Fraction(new_year - old_year, old_year))
        """,
        rare = true,
    ),

)
