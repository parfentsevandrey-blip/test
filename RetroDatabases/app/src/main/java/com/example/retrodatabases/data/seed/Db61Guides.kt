package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1430 to 1440: double hours of the day, thorn and yogh in modern letters, first differences in a table, paging a long text and regnal years. */
val db61Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 61,
        kind = Kind.GUIDE,
        section = "Time",
        title = "How a day is cut into twelve double hours",
        originalTitle = "Shichen",
        author = "The court reckoners of China and Korea",
        year = 1434,
        summary = "A day was cut into twelve double hours, each named by a sign of the zodiac, and a short program maps any hour of the day to its double hour.",
        details = "The twelve names used here are the ones of the Chinese and Korean court reckoning, beginning with the double hour that holds midnight. The example uses integer arithmetic on the hour, so the output is fixed and needs no clock.",
        instructions = """
            1. Save the example as double_hours.py.
            2. Run it: python3 double_hours.py
        """,
        example = """
            NAMES = ["ja", "chuk", "in", "myo", "jin", "sa", "o", "mi", "sin", "yu", "sul", "hae"]

            for hour in (0, 1, 2, 5, 11, 12, 17, 23):
                # Each double hour lasts two hours and starts one hour before its even mark, so hour h falls in slot (h + 1) // 2.
                index = ((hour + 1) // 2) % 12
                print(f"{hour:02d}:00 is the double hour {NAMES[index]}")
        """,
    ),

    entry(
        databaseId = 61,
        kind = Kind.GUIDE,
        section = "Spelling",
        title = "How thorn and yogh are written in modern letters",
        originalTitle = "Chancery English",
        author = "The clerks of the royal Chancery",
        year = 1430,
        summary = "The letters thorn and yogh were used in English writing of the 1430s, and a short program rewrites a few words in modern letters with a fixed table.",
        details = "Thorn and yogh were letters of the English written in the Chancery and elsewhere, and modern editions replace them with ordinary letters. The table in the example is one simple choice made for this exercise, and the words are invented to show the replacements.",
        instructions = """
            1. Save the example as thorn_words.py.
            2. Run it: python3 thorn_words.py
        """,
        example = """
            REPLACE = {chr(0xfe): "th", chr(0xf0): "th", chr(0x21d): "y", chr(0xe6): "ae"}
            WORDS = [chr(0xfe) + "e", chr(0xfe) + chr(0xe6) + "t", chr(0x21d) + "e", chr(0xf0) + "ere"]

            for word in WORDS:
                modern = word
                for old, new in REPLACE.items():
                    modern = modern.replace(old, new)
                print(modern)
        """,
    ),

    entry(
        databaseId = 61,
        kind = Kind.GUIDE,
        section = "Tables",
        title = "How first differences fill a table of values",
        originalTitle = "Zij-i Sultani",
        author = "The astronomers of the Timurid court",
        year = 1437,
        summary = "A table of values can be read between its entries by first and second differences, and a short program fills an invented table of squares this way.",
        details = "Astronomical tables of the period were read between their entries by differences, and the example applies that method to an invented table of values. The arithmetic uses exact fractions, so the output does not change from run to run.",
        instructions = """
            1. Save the example as first_differences.py.
            2. Run it: python3 first_differences.py
        """,
        example = """
            from fractions import Fraction as F

            table = [0, 3, 8, 15, 24, 35]   # the values n squared minus one for n from 1 to 6, an invented table

            first = [b - a for a, b in zip(table, table[1:])]
            second = [b - a for a, b in zip(first, first[1:])]
            print("first differences:", first)
            print("second differences:", second)

            # Newton's form, starting at n = 1 with t the fraction of a step past it: f = f0 + t*d1 + t*(t - 1)/2 * d2.
            t = F(1, 2)
            value = F(table[0]) + t * first[0] + t * (t - 1) / 2 * second[0]
            print("value at 1.5:", value)
        """,
    ),

    entry(
        databaseId = 61,
        kind = Kind.GUIDE,
        section = "Pages",
        title = "How a dictated book is paged, fifty lines to a page",
        originalTitle = "The Book of Margery Kempe",
        author = "Margery Kempe and her scribes",
        year = 1436,
        summary = "A long text is split into pages of equal length, and a short program finds the page and the line on that page for any numbered line.",
        details = "Scribes copied long texts in pages and lines, and the example shows the arithmetic for finding a line by its number. The length of the text is invented for the exercise, so the output is fixed.",
        instructions = """
            1. Save the example as page_lines.py.
            2. Run it: python3 page_lines.py
        """,
        example = """
            LINES_PER_PAGE = 50
            TOTAL_LINES = 1000   # an invented length for the exercise

            for number in (1, 50, 51, 777, 1000):
                page = (number - 1) // LINES_PER_PAGE + 1
                line = (number - 1) % LINES_PER_PAGE + 1
                print(f"line {number}: page {page}, line {line} on the page")
            print("pages in total:", TOTAL_LINES // LINES_PER_PAGE)
        """,
    ),

    entry(
        databaseId = 61,
        kind = Kind.GUIDE,
        section = "Regnal years",
        title = "How a regnal year becomes a calendar year",
        originalTitle = "Regnal years of Henry VI",
        author = "The English royal chancery",
        year = 1430,
        summary = "A regnal year counts from the year a king came to the throne, so a program can turn the regnal years of Henry VI into calendar years.",
        details = "Henry VI came to the English throne in 1422 as an infant, and the regnal years of his reign were counted from that year. The example treats each regnal year as beginning in the calendar year of its number, which simplifies the exact accession day.",
        instructions = """
            1. Save the example as regnal_years.py.
            2. Run it: python3 regnal_years.py
        """,
        example = """
            ACCESSION_YEAR = 1422   # the year Henry VI came to the throne, as commonly given

            for regnal in (1, 8, 9, 18):
                # Regnal year 1 began in the accession year, so regnal year n began in the accession year plus n minus one.
                calendar = ACCESSION_YEAR + regnal - 1
                print(f"regnal year {regnal} of Henry VI began in {calendar}")
        """,
    ),

)
