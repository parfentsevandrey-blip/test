package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Two historical counts of 970 and 977, computed in code: the building time of al-Azhar and the spread of the dates given for the Taiping Guangji. */
val db107Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 107,
        kind = Kind.EXAMPLE,
        section = "Egypt",
        title = "The building time of al-Azhar, 27 months by one account and about 36 by another, counted in code",
        originalTitle = "Building time of al-Jami' al-Azhar",
        author = "Jawhar al-Siqilli and his builders",
        year = 970,
        summary = "Two accounts of how long al-Azhar took to build, 27 months and about 36 months, differ by nine months, a gap that the program counts.",
        details = "The mosque was begun on 4 April 970 under Jawhar al-Siqilli. The account that gives two years and three months agrees with the count of 27 months, while the account that gives about three years counts 36 months, and the run reports the gap between them.",
        instructions = """
            1. Save the example as azhar_months.py.
            2. Run it: python3 azhar_months.py
        """,
        example = """
            # Two accounts of how long al-Azhar took to build, from April 970
            two_years_three_months = 2 * 12 + 3
            about_three_years = 3 * 12

            print("two years and three months in months:", two_years_three_months)
            print("about three years in months:", about_three_years)
            gap = about_three_years - two_years_three_months
            print("gap between the two accounts in months:", gap)
            print("gap in years and months:", gap // 12, "years", gap % 12, "months")
        """,
    ),

    entry(
        databaseId = 107,
        kind = Kind.EXAMPLE,
        section = "Song China",
        title = "The dates given for the Taiping Guangji, 977, 978 and 981, counted in code",
        originalTitle = "Dates given for the Taiping Guangji",
        author = "Li Fang and a team of compilers",
        year = 977,
        summary = "Three dates given for the Taiping Guangji, 977, 978 and 981, are sorted by a short program that reports the spread of the dates in years.",
        details = "Accounts differ on when the collection was finished, with 977, 978 and 981 each given for it. The run finds the earliest and latest dates, the spread between them and the gaps between neighbours, so it tests the arithmetic rather than the compilation.",
        instructions = """
            1. Save the example as taiping_dates.py.
            2. Run it: python3 taiping_dates.py
        """,
        example = """
            # The dates given for the Taiping Guangji, in no fixed order
            dates = [981, 977, 978]
            dates.sort()
            gaps = [later - earlier for earlier, later in zip(dates, dates[1:])]
            print("dates:", len(dates))
            print("earliest:", dates[0])
            print("latest:", dates[-1])
            print("spread in years:", dates[-1] - dates[0])
            print("gaps:", gaps)
            print("largest gap:", max(gaps))
            print("smallest gap:", min(gaps))
        """,
    ),

)
