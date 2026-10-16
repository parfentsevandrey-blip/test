package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1470 to 1480: the cantos of Dante's Commedia, Copernicus's age at death and the days from a papal election to a Hungarian printing. */
val db57Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 57,
        kind = Kind.FLEX,
        section = "Canticles",
        title = "Cantos of the Commedia by canticle, summed",
        originalTitle = "La Commedia",
        author = "Dante Alighieri",
        year = 1472,
        summary = "The Commedia has thirty-four cantos in Inferno, counting its opening canto, and thirty-three in each of the other two canticles, so the total is one hundred.",
        details = "Dante's poem was first printed at Foligno in 1472, and the count follows its usual division into three parts. The example adds the canticle counts and prints the total.",
        instructions = """
            1. Save the example as cantos.py.
            2. Run it: python3 cantos.py
        """,
        example = """
            cantos = {"Inferno": 34, "Purgatorio": 33, "Paradiso": 33}
            for canticle, count in cantos.items():
                print(f"{canticle}: {count} cantos")
            print("total cantos:", sum(cantos.values()))
        """,
    ),

    entry(
        databaseId = 57,
        kind = Kind.FLEX,
        section = "Astronomy",
        title = "Copernicus's age at death in whole years",
        originalTitle = "De revolutionibus",
        author = "Nicolaus Copernicus",
        year = 1473,
        summary = "A whole-year age is the difference of the years, less one when the birthday has not yet come in the year of death.",
        details = "Copernicus was born at Torun in February 1473 and died in May 1543, dates that are widely given. The example uses Python's date library and prints the age in whole years.",
        instructions = """
            1. Save the example as copernicus_age.py.
            2. Run it: python3 copernicus_age.py
        """,
        example = """
            from datetime import date

            born = date(1473, 2, 19)
            died = date(1543, 5, 24)
            age = died.year - born.year - ((died.month, died.day) < (born.month, born.day))
            print("age at death:", age)
        """,
    ),

    entry(
        databaseId = 57,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from Sixtus IV's election to Hess's printing",
        originalTitle = "Chronica Hungarorum",
        author = "Andreas Hess",
        year = 1471,
        summary = "Python's date arithmetic counts the days between two fixed dates, here Sixtus IV's election in August 1471 and the end of Hess's printing in June 1473.",
        details = "The election is commonly dated to 9 August 1471, and the printing at Buda was finished on 5 June 1473. The example counts the days between the two dates with a fixed calendar, so the result never changes.",
        instructions = """
            1. Save the example as days_flex.py.
            2. Run it: python3 days_flex.py
        """,
        example = """
            from datetime import date

            elected = date(1471, 8, 9)
            printed = date(1473, 6, 5)
            print("days:", (printed - elected).days)
        """,
    ),

)
