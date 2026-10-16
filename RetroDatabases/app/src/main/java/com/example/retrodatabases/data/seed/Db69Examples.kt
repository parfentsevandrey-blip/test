package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1350 to 1360: the tales of Boccaccio's Decameron counted by teller and by day, and spans of the decade counted in code. */
val db69Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 69,
        kind = Kind.EXAMPLE,
        section = "Counting",
        title = "Counting the tales of the Decameron, ten tellers over ten days",
        originalTitle = "Counting the tales of the Decameron, ten tellers over ten days",
        author = "Giovanni Boccaccio",
        year = 1353,
        summary = "A short program counts the tales of Boccaccio's Decameron, one tale from each of ten tellers on each of ten days, and gets a hundred tales.",
        details = "The frame has ten young people, and on each of the ten days every one of them tells one tale, which makes a hundred tales in all. The example counts the days and the tellers and prints the total, so the count can be followed by hand.",
        instructions = """
            1. Save the example as decameron_tales.py.
            2. Run it: python3 decameron_tales.py
        """,
        example = """
            tellers = 10
            days = 10
            per_day = tellers
            total = days * per_day
            print("tellers", tellers)
            print("days", days)
            print("tales in all", total)
        """,
    ),

    entry(
        databaseId = 69,
        kind = Kind.EXAMPLE,
        section = "Counting",
        title = "Years from the Wienhausen spectacles to their discovery in 1953",
        originalTitle = "Years from the Wienhausen spectacles to their discovery in 1953",
        author = "No single author",
        year = 1350,
        summary = "A short program counts the years between the Wienhausen spectacles, dated to about 1350, and their discovery during renovation work in 1953.",
        details = "The date of about 1350 is approximate, so the count is a rough figure of about six hundred years. The example starts from 1350 and prints the difference, so the result can be checked by hand.",
        instructions = """
            1. Save the example as wienhausen_years.py.
            2. Run it: python3 wienhausen_years.py
        """,
        example = """
            made = 1350
            found = 1953
            print("years from about", made, "to", found, ":", found - made)
        """,
    ),

    entry(
        databaseId = 69,
        kind = Kind.EXAMPLE,
        section = "Counting",
        title = "Years from the first Strasbourg clock to the clock of 1574",
        originalTitle = "Years from the first Strasbourg clock to the clock of 1574",
        author = "No single author",
        year = 1352,
        summary = "A short program counts the years from the first Strasbourg Cathedral clock, built about 1352, to the completion of its successor in 1574.",
        details = "The start year is approximate, since the first clock is dated to about 1352 to 1354, and the example uses 1352 so that the count is exact for that year. The completion date of the later clock is 1574.",
        instructions = """
            1. Save the example as strasbourg_years.py.
            2. Run it: python3 strasbourg_years.py
        """,
        example = """
            first = 1352
            second = 1574
            print("years from", first, "to", second, ":", second - first)
        """,
    ),

    entry(
        databaseId = 69,
        kind = Kind.EXAMPLE,
        section = "Counting",
        title = "Years of the fifty-year Holy Year cycle from 1350 to 1500",
        originalTitle = "Years of the fifty-year Holy Year cycle from 1350 to 1500",
        author = "Pope Clement VI",
        year = 1350,
        summary = "A short program lists the years of the fifty-year Holy Year cycle that Clement VI set from 1350 to 1500 and counts them.",
        details = "Clement VI set the interval at fifty years in 1343, and the cycle from 1350 falls in 1350, 1400, 1450 and 1500. Later rules changed the interval, so the program computes the cycle rather than listing actual celebrations.",
        instructions = """
            1. Save the example as holy_year_cycle.py.
            2. Run it: python3 holy_year_cycle.py
        """,
        example = """
            years = list(range(1350, 1501, 50))
            for year in years:
                print(year)
            print("count", len(years))
        """,
    ),

    entry(
        databaseId = 69,
        kind = Kind.EXAMPLE,
        section = "Counting",
        title = "The span of dates given for Mandeville's French text, 1357 to 1371",
        originalTitle = "The span of dates given for Mandeville's French text, 1357 to 1371",
        author = "No single author",
        year = 1357,
        summary = "A short program counts the years in the span that reference works most often give for the French text of Mandeville's Travels.",
        details = "The span runs from 1357 to 1371, the window most often given for the French text, and the example prints both ends and the length of the span, which can be checked by hand.",
        instructions = """
            1. Save the example as mandeville_span.py.
            2. Run it: python3 mandeville_span.py
        """,
        example = """
            earliest = 1357
            latest = 1371
            print("earliest", earliest)
            print("latest", latest)
            print("span", latest - earliest, "years")
        """,
    ),

    entry(
        databaseId = 69,
        kind = Kind.EXAMPLE,
        section = "Counting",
        title = "Years of the Yuan dynasty, and how many fell after the 1350 reform",
        originalTitle = "Years of the Yuan dynasty, and how many fell after the 1350 reform",
        author = "No single author",
        year = 1350,
        summary = "A short program counts the years of the Yuan dynasty from 1271 to 1368 and the years that fell after the Zhizheng reform of 1350.",
        details = "The Yuan dynasty is dated from 1271 to 1368, and the reform is dated to 1350, the tenth year of the Zhizheng era. The example prints the length of the dynasty and the number of years from the reform to its end.",
        instructions = """
            1. Save the example as yuan_years.py.
            2. Run it: python3 yuan_years.py
        """,
        example = """
            start = 1271
            end = 1368
            reform = 1350
            print("dynasty", end - start, "years")
            print("after", reform, ":", end - reform, "years")
        """,
    ),

)
