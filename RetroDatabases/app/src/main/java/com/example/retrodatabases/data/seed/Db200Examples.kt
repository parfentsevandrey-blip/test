package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 40 to 50: the donative that Claudius promised each praetorian at his accession, totalled for an illustrative group. */
val db200Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 200,
        kind = Kind.EXAMPLE,
        section = "Pay",
        title = "Totalling the donative that Claudius promised each praetorian in 41",
        originalTitle = "Donativum",
        author = "Claudius and the praetorian guard",
        year = 41,
        summary = "Claudius promised each praetorian guard 15,000 sesterces at his accession in 41, and a short program totals the donative for an illustrative group of guards.",
        details = "The sum usually given is 15,000 sesterces a guard, and accounts differ on whether it was paid at once or only promised, so the example counts it as promised. The group of 500 guards is illustrative and is not the size of the praetorian guard, and the example converts the total into denarii at four sesterces to the denarius.",
        instructions = """
            1. Save the example as donative.py.
            2. Run it: python3 donative.py
        """,
        example = """
            DONATIVE = 15000            # sesterces promised to each praetorian, as usually given
            SESTERCES_PER_DENARIUS = 4
            GUARDS = 500                # illustrative group of guards, not the real number
            total = DONATIVE * GUARDS
            print("donative per guard in sesterces:", DONATIVE)
            print("guards in the group:", GUARDS)
            print("total in sesterces:", total)
            print("total in denarii:", total // SESTERCES_PER_DENARIUS)
        """,
        rare = true,
    ),

)
