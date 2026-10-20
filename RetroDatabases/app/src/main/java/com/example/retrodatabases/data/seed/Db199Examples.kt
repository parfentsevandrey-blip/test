package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 50 to 59: the count of seven weeks to Pentecost, the feast that 1 Corinthians 16:8 names. */
val db199Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 199,
        kind = Kind.EXAMPLE,
        section = "Feasts",
        title = "Counting the seven weeks to Pentecost, the feast 1 Corinthians 16:8 names",
        originalTitle = "Pentekoste",
        author = "The law of Leviticus 23 and the apostle Paul",
        year = 54,
        summary = "Leviticus counts seven full weeks from the day after the Passover sabbath to a fiftieth day, and a short program counts the weeks and names the day on which Pentecost falls.",
        details = "Paul writes that he will stay at Ephesus until Pentecost in 1 Corinthians 16:8, a letter dated about 54, so the example takes the feast from that text. The count starts with the second day of Passover as day one, so the seventh week ends on day 49 and Pentecost is the fiftieth day.",
        instructions = """
            1. Save the example as omer_count.py.
            2. Run it: python3 omer_count.py
        """,
        example = """
            # Leviticus 23 counts seven complete weeks from the second day of Passover, then the fiftieth day.
            weeks = 7
            days_in_weeks = weeks * 7
            pentecost_day = days_in_weeks + 1
            print("complete weeks counted:", weeks)
            print("days in those weeks:", days_in_weeks)
            print("Pentecost falls on day", pentecost_day)
        """,
        rare = true,
    ),

)
