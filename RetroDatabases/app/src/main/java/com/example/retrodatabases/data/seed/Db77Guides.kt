package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1274 to 1275: how an English charter's year changes at Lady Day, and how a scholastic article sets out its objections and replies. */
val db77Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 77,
        kind = Kind.GUIDE,
        section = "Dating",
        title = "How an English charter's year changes at Lady Day",
        originalTitle = "Lady Day style",
        author = "English clerks and chancery scribes",
        year = 1275,
        summary = "English records of the 1270s often began the year on 25 March, and a short program converts such Lady Day dates into modern reckoning.",
        details = "Under this style, dates from 1 January to 24 March carry the year number one lower than modern reckoning gives them, so a charter dated 10 February 1275 falls in February 1276 by modern reckoning. The example converts five illustrative dates, and each answer can be checked by hand against the rule.",
        instructions = """
            1. Save the example as lady_day.py.
            2. Run it: python3 lady_day.py
        """,
        example = """
            def modern_year(lady_day_year, month, day):
                # Lady Day style: the year number changes on 25 March, so a date from 1 January
                # to 24 March carries a year number one lower than its modern year.
                if month < 3 or (month == 3 and day < 25):
                    return lady_day_year + 1
                return lady_day_year

            dates = [(1275, 2, 10), (1275, 3, 25), (1275, 3, 24), (1275, 12, 31), (1275, 1, 1)]
            for year, month, day in dates:
                modern = modern_year(year, month, day)
                print(f"{year}-{month:02d}-{day:02d} in the Lady Day count is {modern}-{month:02d}-{day:02d} in modern reckoning")
        """,
        rare = true,
    ),

    entry(
        databaseId = 77,
        kind = Kind.GUIDE,
        section = "Disputation",
        title = "How a scholastic article sets out objections and replies",
        originalTitle = "Articulus",
        author = "Masters of the Paris schools",
        year = 1274,
        summary = "A scholastic article states a question, lists objections, gives a contrary authority and then a reply, and a short program prints an illustrative article in that order.",
        details = "Aquinas's Summa theologiae uses this order throughout, and the opening words of its parts, Videtur quod, Sed contra and Respondeo, mark the stages of the argument. The question in the example is invented for the purpose and is not taken from any text.",
        instructions = """
            1. Save the example as scholastic.py.
            2. Run it: python3 scholastic.py
        """,
        example = """
            # An illustrative question, not taken from any text
            QUESTION = "Whether a teacher may take a fee"
            OBJECTIONS = [
                "A gift of knowledge should be given freely.",
                "Whoever is paid for a thing seems to sell it, and teaching is not for sale.",
            ]
            CONTRARY = "The Apostle says that the labourer is worthy of his hire."
            REPLY = "A teacher may receive support, since teaching is a real labour."
            ANSWERS = [
                "The gift is freely given, but a teacher's living may still be provided for.",
                "A fee for labour is not the same as selling the truth.",
            ]

            print("Question:", QUESTION)
            for number, objection in enumerate(OBJECTIONS, start=1):
                print(f"Objection {number}: {objection}")
            print("On the contrary:", CONTRARY)
            print("I answer that:", REPLY)
            for number, answer in enumerate(ANSWERS, start=1):
                print(f"Reply to objection {number}: {answer}")
        """,
    ),

)
