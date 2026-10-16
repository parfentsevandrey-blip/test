package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1370 to 1380: the ages of three men at their deaths, a bolt of cloth in ells, a guan of cash, a dynasty of three generations, a coronation age and the Ethiopian year of two Julian dates. */
val db67Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 67,
        kind = Kind.EXAMPLE,
        section = "Lives",
        title = "How old Petrarch, Edward III and Charles V were at their deaths",
        originalTitle = "Ages at death",
        author = "No single author",
        year = 1380,
        summary = "Petrarch, Edward III and Charles V died between 1374 and 1380, and a short program counts each man's age in whole years from his birth and death dates.",
        details = "The dates are the ones commonly given, and an age counts whole years, so a birthday that has not come by the date of death is not counted. Petrarch died just before his birthday in July 1374, so his age is one year less than the difference of the two years.",
        instructions = """
            1. Save the example as ages.py.
            2. Run it: python3 ages.py
        """,
        example = """
            from datetime import date

            def age_at(born, died):
                # whole years; one less when the birthday has not come by the date of death
                years = died.year - born.year
                if (died.month, died.day) < (born.month, born.day):
                    years -= 1
                return years

            people = [
                ("Petrarch", date(1304, 7, 20), date(1374, 7, 18)),    # as commonly given
                ("Edward III", date(1312, 11, 13), date(1377, 6, 21)),
                ("Charles V", date(1338, 1, 21), date(1380, 9, 16)),
            ]
            for name, born, died in people:
                print(name, "was", age_at(born, died), "at his death")
        """,
    ),

    entry(
        databaseId = 67,
        kind = Kind.EXAMPLE,
        section = "Measures",
        title = "How many yards a bolt of seventeen English cloth ells measures",
        originalTitle = "Cloth ell",
        author = "English cloth merchants",
        year = 1375,
        summary = "An English cloth ell was 45 inches, and a short program converts a bolt of seventeen ells into inches and into yards with the inches left over.",
        details = "The ell was the measure for cloth in the English trade and was a quarter longer than the yard. The example uses a bolt of seventeen ells as an illustrative length, and the program prints the total in inches and in yards with the remainder in inches.",
        instructions = """
            1. Save the example as ells.py.
            2. Run it: python3 ells.py
        """,
        example = """
            INCHES_PER_ELL = 45            # English cloth ell
            INCHES_PER_YARD = 36
            ells = 17                      # illustrative bolt of cloth
            inches = ells * INCHES_PER_ELL
            print("inches:", inches)
            print("yards:", inches // INCHES_PER_YARD, "remainder inches", inches % INCHES_PER_YARD)
        """,
    ),

    entry(
        databaseId = 67,
        kind = Kind.EXAMPLE,
        section = "Money",
        title = "Converting a five-guan Ming note into strings of cash",
        originalTitle = "Guan",
        author = "The Hongwu court",
        year = 1375,
        summary = "A guan was a string of one thousand cash, and a short program converts an illustrative five-guan note of the 1375 issue into cash and into strings of a hundred.",
        details = "The notes of 1375 were valued in guan, and the example uses a note of five guan, an illustrative value and not a surviving denomination. The rule it uses is stated in the program: one guan is one thousand cash, counted in strings of a hundred.",
        instructions = """
            1. Save the example as guan.py.
            2. Run it: python3 guan.py
        """,
        example = """
            GUAN_CASH = 1000                  # one guan is a string of one thousand cash
            note_guan = 5                     # illustrative note value
            cash = note_guan * GUAN_CASH
            print("cash:", cash)
            print("strings of 100 cash:", cash // 100)
        """,
    ),

    entry(
        databaseId = 67,
        kind = Kind.EXAMPLE,
        section = "Dynasties",
        title = "How long a dynasty lasts by three generations of forty years",
        originalTitle = "Dynastic cycle",
        author = "Ibn Khaldun",
        year = 1377,
        summary = "Ibn Khaldun's Muqaddimah reckons a dynasty at about three generations, and a short program adds forty years for each generation to a founding year of 1260.",
        details = "The reckoning gives a span of 120 years, and the example prints the span and the start of each generation for an illustrative founding year. Ibn Khaldun offered it as a general rule drawn from observation, so the program shows the arithmetic of the rule and not a claim about any one dynasty.",
        instructions = """
            1. Save the example as dynasty.py.
            2. Run it: python3 dynasty.py
        """,
        example = """
            GENERATION_YEARS = 40        # Ibn Khaldun reckons a generation at forty years
            GENERATIONS = 3              # about three generations to a dynasty
            FOUNDED = 1260               # illustrative founding year
            print("span of the dynasty:", GENERATION_YEARS * GENERATIONS, "years")
            for generation in range(GENERATIONS + 1):
                print("generation mark:", FOUNDED + generation * GENERATION_YEARS)
        """,
    ),

    entry(
        databaseId = 67,
        kind = Kind.EXAMPLE,
        section = "Coronations",
        title = "How old Richard II was at his coronation in 1377",
        originalTitle = "Age at coronation",
        author = "Chroniclers of the English court",
        year = 1377,
        summary = "Richard II was born in January 1367 and was crowned in July 1377, so a short program counts his age in whole years on the day of the coronation.",
        details = "Richard II was crowned at Westminster on 16 July 1377, after the death of his grandfather Edward III, and the example takes his birthday as 6 January 1367. The program subtracts the birth year and then checks whether the birthday has come, so the age is counted in whole years.",
        instructions = """
            1. Save the example as age.py.
            2. Run it: python3 age.py
        """,
        example = """
            from datetime import date

            born = date(1367, 1, 6)            # Richard II's birth, as commonly given
            crowned = date(1377, 7, 16)        # his coronation at Westminster
            years = crowned.year - born.year
            if (crowned.month, crowned.day) < (born.month, born.day):
                years -= 1
            print("age at coronation:", years, "years")
        """,
    ),

    entry(
        databaseId = 67,
        kind = Kind.EXAMPLE,
        section = "Calendars",
        title = "The Ethiopian year of two Julian dates, 22 June 1377 and 16 September 1380",
        originalTitle = "Ethiopian era",
        author = "Ethiopian chronologers",
        year = 1377,
        summary = "The Ethiopian year runs eight years behind the Julian year until its new year in late August and seven years behind after it, and a short program converts two dates into Ethiopian years.",
        details = "Richard II succeeded on 22 June 1377, and Charles V of France died on 16 September 1380, both as commonly given. The program also checks the rule against the new year of 2000, which began on 11 September 2007 in the Gregorian calendar, a day that is 29 August in the Julian count.",
        instructions = """
            1. Save the example as ethiopian.py.
            2. Run it: python3 ethiopian.py
        """,
        example = """
            # The Ethiopian year is 8 years behind the Julian year before the new year, and 7 years behind from it.
            def ethiopian_year(year, month, day):
                if (month, day) < (8, 29):
                    return year - 8
                return year - 7

            print("22 June 1377 is Ethiopian year", ethiopian_year(1377, 6, 22))
            print("16 September 1380 is Ethiopian year", ethiopian_year(1380, 9, 16))
            print("29 August 2007 is Ethiopian year", ethiopian_year(2007, 8, 29))
            print("22 June 2007 is Ethiopian year", ethiopian_year(2007, 6, 22))
        """,
    ),

)
