package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One example of 780 to 790: the 146 years from Alopen's arrival in Chang'an in 635 to the Xi'an stele of 781, counted by a short program. */
val db126Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 126,
        kind = Kind.EXAMPLE,
        section = "Chinese",
        title = "The 146 years from Alopen's arrival in Chang'an in 635 to the Xi'an stele of 781, counted in code",
        originalTitle = "Years of the Church of the East in China",
        author = "The Church of the East in China",
        year = 781,
        summary = "The Xi'an stele of 781 dates the arrival of the Persian missionary Alopen in Chang'an to 635, and a short program counts the 146 years between the two dates.",
        details = "The stele's account of the mission begins with Alopen, a Persian missionary whose arrival is placed in 635, and the count uses the usual dates of his arrival and of the monument. The program only does arithmetic on two dates, so it measures how long the church had been in China when the stele was raised, not how many believers it had.",
        instructions = """
            1. Save the example as alopen_stele.py.
            2. Run it: python3 alopen_stele.py
        """,
        example = """
            ARRIVAL = 635  # Alopen reaches Chang'an, as the stele tells it
            STELE = 781    # the Xi'an stele is raised, as commonly dated
            span = STELE - ARRIVAL
            print("years from Alopen's arrival to the stele:", span)
            print("whole centuries:", span // 100)
            print("years left over:", span % 100)
        """,
    ),

)
