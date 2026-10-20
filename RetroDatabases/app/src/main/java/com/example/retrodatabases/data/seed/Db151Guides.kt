package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One guide of 530 to 540: the Falcidian quarter of Roman law, which kept a fourth of the estate for the heir, worked out by a program. */
val db151Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 151,
        kind = Kind.GUIDE,
        section = "Roman",
        title = "How the Falcidian quarter cuts the legacies of a will so the heir keeps a fourth of the estate, worked out by a program",
        originalTitle = "lex Falcidia",
        author = "Compilers of the Digest and the Institutes",
        year = 533,
        summary = "The Falcidian law kept at least a quarter of an estate for the heir, so legacies that claimed more than three quarters were cut back in the same proportion, as a short program shows.",
        details = "The rule comes from the Lex Falcidia of 40 BC, which the Institutes and the Digest of 533 record for later courts, so 533 is the year of the books and not of the law. The program uses whole numbers and one proportion to show the arithmetic of the rule, and it leaves out the exceptions and the way debts and gifts are counted.",
        rare = true,
        instructions = """
            1. Save the example as falcidian.py.
            2. Run it: python3 falcidian.py
        """,
        example = """
            from fractions import Fraction

            ESTATE = 800                # net estate after debts, in the example's units
            LEGACIES = [600, 300, 300]  # the legacies of the will, claiming 1200 in all

            limit = Fraction(ESTATE * 3, 4)  # three quarters of the estate: the most that legacies may take
            claimed = sum(LEGACIES)
            print("legacies claimed:", claimed)
            print("three quarters of the estate:", limit)
            if claimed > limit:
                factor = limit / claimed
                cut = [Fraction(value) * factor for value in LEGACIES]
                print("reduction factor:", factor)
                print("legacies after reduction:", " ".join(str(value) for value in cut))
            else:
                cut = LEGACIES
                print("no reduction needed")
            print("heir keeps:", ESTATE - sum(cut))
        """,
    ),

)
