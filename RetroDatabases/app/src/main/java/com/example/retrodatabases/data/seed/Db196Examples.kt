package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 80: the animals counted over the hundred days of the opening games, on two accounts that differ. */
val db196Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 196,
        kind = Kind.EXAMPLE,
        section = "Games",
        title = "Animals per day in Titus's hundred days of games, on two counts",
        originalTitle = "Animals killed at the opening games",
        author = "Ancient accounts of the opening games",
        year = 80,
        summary = "The opening games of the Flavian amphitheatre ran for about a hundred days, and a short program divides two counts of animals killed by that number of days.",
        details = "Accounts differ on the number of animals killed, one count being about 5,000 and another about 9,000, so the program shows both and does not choose between them. Each count is divided by one hundred days to give whole animals per day, and the difference between the two daily figures is printed too.",
        instructions = """
            1. Save the example as animals_per_day.py.
            2. Run it: python3 animals_per_day.py
        """,
        example = """
            DAYS = 100                                                  # the opening games, usually given as about a hundred days
            counts = [("one count", 5000), ("the other count", 9000)]   # animals killed, as the two accounts give them
            for label, animals in counts:
                print(label, animals, "animals,", animals // DAYS, "a day")
            print("difference per day:", (9000 - 5000) // DAYS)
        """,
        rare = true,
    ),

)
