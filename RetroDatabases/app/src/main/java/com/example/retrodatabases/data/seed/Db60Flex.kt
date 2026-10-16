package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1440 to 1450: the years of a pontificate, the digit sums of three years and the revision years of a machine book. */
val db60Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 60,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Years of Nicholas V's pontificate, from election to death",
        originalTitle = "Bibliotheca Apostolica Vaticana",
        author = "Pope Nicholas V",
        year = 1447,
        summary = "Nicholas V was elected in 1447 and died in 1455, and a short program counts the years of his pontificate and the years to the Holy Year of 1450.",
        details = "Nicholas V's pontificate began with his election in 1447 and ended with his death in 1455. The example counts the years between those dates and the years from his election to the jubilee of 1450, using only the years themselves.",
        instructions = """
            1. Save the example as pontificate_years.py.
            2. Run it: python3 pontificate_years.py
        """,
        example = """
            elected = 1447
            died = 1455
            jubilee = 1450
            print("years of the pontificate:", died - elected)
            print("years from the election to the Holy Year of 1450:", jubilee - elected)
        """,
    ),

    entry(
        databaseId = 60,
        kind = Kind.FLEX,
        section = "Numerals",
        title = "Digit sums of the years 1440, 1499 and 1517",
        originalTitle = "Cologne Chronicle of 1499",
        author = "Laurens Janszoon Coster",
        year = 1440,
        summary = "A digit sum adds the digits of a number, and a short program lists the digits of the years 1440, 1499 and 1517 and totals each one.",
        details = "Zell's account of Coster's invention appeared in the Cologne Chronicle of 1499, and the Hutten edition of Valla's treatise is dated 1517. The example adds the digits of those years and of the Coster year of 1440.",
        instructions = """
            1. Save the example as digit_sums.py.
            2. Run it: python3 digit_sums.py
        """,
        example = """
            for year in (1440, 1499, 1517):
                digits = [int(d) for d in str(year)]
                print(f"{year}: digits {digits}, digit sum {sum(digits)}")
        """,
    ),

    entry(
        databaseId = 60,
        kind = Kind.FLEX,
        section = "Machine drawings",
        title = "Years between Taccola's finish and his last revisions",
        originalTitle = "De ingeneis",
        author = "Mariano di Jacopo, called Taccola",
        year = 1449,
        summary = "Taccola's De ingeneis was finished in 1433 and revised until about 1449, and a short program counts the years from the first work in 1419 and between the finish and the last revisions.",
        details = "One catalogue dates the Munich volume of the book to the range 1419 to about 1450, and the record uses 1433 for the finish and 1449 for the last revisions. The example counts the years between these dates with plain subtraction.",
        instructions = """
            1. Save the example as taccola_years.py.
            2. Run it: python3 taccola_years.py
        """,
        example = """
            first_work = 1419
            finished = 1433
            last_revisions = 1449
            print("years from the first work in 1419 to the finish:", finished - first_work)
            print("years between the finish and the last revisions:", last_revisions - finished)
        """,
    ),

)
