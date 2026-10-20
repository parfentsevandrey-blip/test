package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 290 to 300: an Egyptian multiplication by doubling, placed at 293 because the method is far older than the decade. */
val db175Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 175,
        kind = Kind.GUIDE,
        section = "Arithmetic",
        title = "How an Egyptian scribe multiplies by doubling, written as code",
        originalTitle = "Doubling method",
        author = "Egyptian scribes",
        year = 293,
        summary = "An Egyptian scribe multiplied two numbers by doubling one of them and keeping the rows whose powers of two add up to the other, and a short program does this for 13 times 21.",
        details = "The method is far older than the period, and the sources do not date it to the 290s, so 293 is the year of the anchor, the naming of the Caesars, not the year the method was devised. The example multiplies 13 by 21 through its doubling table, so the product 273 can be checked by hand.",
        instructions = """
            1. Save the example as doubling.py.
            2. Run it: python3 doubling.py
        """,
        example = """
            def egyptian_product(a, b):
                # the doubling table: powers of two up to b, each with the matching multiple of a
                rows = []
                power, multiple = 1, a
                while power <= b:
                    rows.append((power, multiple))
                    power *= 2
                    multiple *= 2
                # add the rows whose powers, taken from the largest down, fit into what is left of b
                chosen = []
                rest = b
                for power, multiple in reversed(rows):
                    if power <= rest:
                        chosen.append(multiple)
                        rest -= power
                return rows, chosen, sum(chosen)

            rows, chosen, total = egyptian_product(13, 21)
            for power, multiple in rows:
                print(power, multiple)
            print("rows added:", sorted(chosen))
            print("product:", total)
        """,
        rare = true,
    ),

)
