package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One guide of 810 to 820, a placed record: the awl rule of Islamic inheritance, placed at 820, the year al-Shafi'i died. */
val db123Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 123,
        kind = Kind.GUIDE,
        section = "Law",
        title = "How the awl rule raises the shares of an Islamic inheritance when they add up to more than one",
        originalTitle = "How the awl rule raises the shares of an Islamic inheritance when they add up to more than one",
        author = "Early Islamic jurists",
        year = 820,
        summary = "The awl rule raises the common base of a set of inheritance shares when the shares add up to more than one, and a short program works one such case with exact fractions.",
        details = "This is a placed record: the sources do not date the awl rule to one year, so 820, the year al-Shafi'i died, is the anchor used for placement and not the date of the rule. The worked case is a husband with a half share and two full sisters with two thirds, whose shares add up to seven sixths, so the base rises to seven and the husband takes three sevenths while the sisters take four sevenths.",
        rare = true,
        instructions = """
            1. Save the example as awl_shares.py.
            2. Run it: python3 awl_shares.py
        """,
        example = """
            from fractions import Fraction

            base = 6
            husband = 3   # a half of 6
            sisters = 4   # two thirds of 6
            total = husband + sisters
            print("shares out of", base, ":", husband, "and", sisters)
            print("total:", total, "is more than", base)
            if total > base:
                base = total
                print("awl: the base rises to", base)
            print("husband:", Fraction(husband, base))
            print("sisters:", Fraction(sisters, base))
            print("each sister:", Fraction(sisters, 2 * base))
            print("sum:", Fraction(husband, base) + Fraction(sisters, base))
        """,
    ),

)
