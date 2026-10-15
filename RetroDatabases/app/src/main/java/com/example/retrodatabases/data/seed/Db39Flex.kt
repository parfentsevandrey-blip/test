package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1650 to 1660: a Down Survey field in acres, long division of one by seven and the seats of the 1654 Parliament in one line. */
val db39Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 39,
        kind = Kind.FLEX,
        section = "Surveying",
        title = "A 220-yard square field in Down Survey acres",
        originalTitle = "Down Survey of Ireland",
        author = "William Petty",
        year = 1655,
        summary = "Petty's Down Survey of Ireland, begun in 1655, measured forfeited land in acres, and one line of arithmetic reckons a square field of 220 yards a side in statute acres.",
        details = "The survey was carried out in 1655 and 1656 to map land for allotment to soldiers and adventurers. The example uses the statute acre of 4840 square yards, which is not necessarily the unit the survey itself used, and the field is illustrative rather than a surveyed parcel.",
        instructions = """
            1. Save the example as down_survey.py.
            2. Run it: python3 down_survey.py
        """,
        example = """
            print(f"a 220-yard square field is {220 * 220 // 4840} statute acres")
        """,
    ),

    entry(
        databaseId = 39,
        kind = Kind.FLEX,
        section = "Algebra",
        title = "Long division of one by seven in ten places",
        originalTitle = "Teutsche Algebra",
        author = "Johann Heinrich Rahn",
        year = 1659,
        summary = "Long division of one by seven repeats the six digits 142857, and the program prints the first ten places after the point.",
        details = "Rahn's algebra of 1659 is the book that printed the division sign, and long division turns a quotient into digits one at a time. The example keeps the remainder at each step, so that each digit is the next step of the division.",
        instructions = """
            1. Save the example as one_seventh.py.
            2. Run it: python3 one_seventh.py
        """,
        example = """
            remainder, digits = 1, ""
            for _ in range(10):
                remainder *= 10
                digits += str(remainder // 7)
                remainder %= 7
            print("1/7 = 0." + digits)
        """,
    ),

    entry(
        databaseId = 39,
        kind = Kind.FLEX,
        section = "Parliaments",
        title = "The 1654 Parliament's seats in one line",
        originalTitle = "Instrument of Government",
        author = "Oliver Cromwell's Protectorate",
        year = 1654,
        summary = "The first Protectorate Parliament, which met in 1654, had 400 seats for England and Wales, 30 for Scotland and 30 for Ireland, and one line of arithmetic gives the total.",
        details = "The seat counts come from the Instrument of Government of 1653, which set the membership of the Parliament. The example adds the three counts with a single print statement.",
        instructions = """
            1. Save the example as seats.py.
            2. Run it: python3 seats.py
        """,
        example = """
            print("England and Wales 400, Scotland 30, Ireland 30, total", 400 + 30 + 30)
        """,
    ),

)
