package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1530 to 1540: the palindromic years of the sixteenth century and the days between two treaties and two decrees. */
val db51Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 51,
        kind = Kind.FLEX,
        section = "Calendars",
        title = "Palindromic years from 1500 to 1600",
        originalTitle = "Palindromic years",
        author = "Printers of the period",
        year = 1530,
        summary = "A palindrome reads the same backwards, and a short program finds the four-digit years from 1500 to 1600 that are palindromes, which is 1551 alone.",
        details = "Palindromic years are a curiosity of the calendar rather than a date of any importance. The example scans the century from 1500 to 1600 and prints the one palindrome that it contains, so the output is the same on every run.",
        instructions = """
            1. Save the example as palindrome_years.py.
            2. Run it: python3 palindrome_years.py
        """,
        example = """
            def is_palindrome(n):
                text = str(n)
                return text == text[::-1]

            found = [year for year in range(1500, 1601) if is_palindrome(year)]
            print(f"palindromic years from 1500 to 1600: {found}")
        """,
    ),

    entry(
        databaseId = 51,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the Augsburg Confession to the Villers-Cotterets ordinance",
        originalTitle = "Confessio Augustana",
        author = "Philip Melanchthon",
        year = 1530,
        summary = "The Augsburg Confession was presented on 25 June 1530 and the Villers-Cotterets ordinance is dated 25 August 1539, and a short program counts the days between the two dates.",
        details = "Melanchthon drafted the Confession, which was presented to Charles V at Augsburg on 25 June 1530, and Francis I signed the ordinance in August 1539. Between 1530 and 1540 the Julian and Gregorian calendars count the same number of days, so the date arithmetic is exact for these dates.",
        instructions = """
            1. Save the example as confession_days.py.
            2. Run it: python3 confession_days.py
        """,
        example = """
            from datetime import date

            confession = date(1530, 6, 25)    # the Augsburg Confession was presented on this day
            ordinance = date(1539, 8, 25)     # the Villers-Cotterets ordinance is dated this day
            days = (ordinance - confession).days
            print(f"days from the Augsburg Confession to the Villers-Cotterets ordinance: {days}")
        """,
    ),

    entry(
        databaseId = 51,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the Truce of Nice to the Jesuit bull",
        originalTitle = "Truce of Nice",
        author = "Charles V and Francis I",
        year = 1538,
        summary = "The Truce of Nice was agreed on 18 June 1538 and the bull Regimini militantis Ecclesiae is dated 27 September 1540, and a short program counts the days between them.",
        details = "Pope Paul III brokered the ten-year truce between Charles V and Francis I at Nice, and the bull that approved the Society of Jesus is dated 27 September 1540. Between 1538 and 1540 the Julian and Gregorian calendars count the same number of days, so the count is exact for these dates.",
        instructions = """
            1. Save the example as nice_truce_days.py.
            2. Run it: python3 nice_truce_days.py
        """,
        example = """
            from datetime import date

            truce = date(1538, 6, 18)     # the Truce of Nice, as commonly dated
            bull = date(1540, 9, 27)      # the bull Regimini militantis Ecclesiae, as commonly dated
            print(f"days from the Truce of Nice to the Jesuit bull: {(bull - truce).days}")
        """,
    ),

)
