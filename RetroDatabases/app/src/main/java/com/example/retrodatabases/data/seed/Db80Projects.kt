package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1241 to 1250: a ledger of fines under the Jutland law and a ransom paid in bezants. */
val db80Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 80,
        kind = Kind.PROJECT,
        section = "Fines",
        title = "A ledger of fines under the Jutland law",
        originalTitle = "Jydske Lov",
        author = "The Jutland assembly",
        year = 1241,
        summary = "The Jutland law set fines for offences, and a short program totals an illustrative ledger of fines by offence.",
        details = "The amounts in the example are invented to show the bookkeeping, and they do not reproduce the fines of the law. The program adds the fines for each offence, sorts the offences by name and prints the grand total.",
        instructions = """
            1. Save the example as fines.py.
            2. Run it: python3 fines.py
        """,
        example = """
            offences = [("wounding", 6), ("theft", 12), ("trespass", 3), ("wounding", 6)]   # illustrative fines
            totals = {}
            for offence, fine in offences:
                totals[offence] = totals.get(offence, 0) + fine
            for offence in sorted(totals):
                print(offence, totals[offence])
            print("total fines", sum(totals.values()))
        """,
    ),

    entry(
        databaseId = 80,
        kind = Kind.PROJECT,
        section = "Ransoms",
        title = "A ransom of 400,000 bezants paid in instalments",
        originalTitle = "Bezant",
        author = "Louis IX of France and his captors",
        year = 1250,
        summary = "The chronicles give the ransom of King Louis IX in 1250 as 400,000 bezants, and a short program divides the sum into instalments.",
        details = "Louis was captured after the army's retreat from Mansurah in April 1250, and accounts differ on whether the sum was paid at once or in instalments. The instalments in the example are illustrative, and the program prints the balance still due after each payment.",
        instructions = """
            1. Save the example as ransom.py.
            2. Run it: python3 ransom.py
        """,
        example = """
            ransom = 400000                       # bezants, the sum given in the chronicles
            payments = [150000, 150000, 100000]   # illustrative instalments
            balance = ransom
            for number, paid in enumerate(payments, start=1):
                balance -= paid
                print("instalment", number, "of", paid, "leaves", balance, "bezants due")
            print("total paid", sum(payments))
        """,
    ),

)
