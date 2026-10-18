package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1250 to 1260: the characters of the Tripitaka blocks, the span of dates of a Norwegian book, the Coptic years, two Persian chapter counts, the pipes of a silver tree and a shelf count. */
val db79Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 79,
        kind = Kind.EXAMPLE,
        section = "Woodblocks",
        title = "Counting every character on the Tripitaka woodblocks",
        originalTitle = "Total characters",
        author = "The carvers of the Tripitaka Koreana",
        year = 1251,
        summary = "Multiplying the usual block count by the characters per block gives the total number of characters carved on the Tripitaka Koreana woodblocks.",
        details = "Accounts give 81,258 blocks as the usual total, and the example multiplies it by 644 characters per block, which is two sides of 23 lines of 14. The product 52,330,152 is a derived figure, so the example shows the arithmetic rather than a count made from the blocks.",
        instructions = """
            1. Save the example as tripitaka_total.py.
            2. Run it: python3 tripitaka_total.py
        """,
        example = """
            BLOCKS = 81258     # usual count of blocks
            PER_BLOCK = 644    # two sides of 23 lines of 14 characters
            print("total characters:", BLOCKS * PER_BLOCK)
        """,
    ),

    entry(
        databaseId = 79,
        kind = Kind.EXAMPLE,
        section = "Dating",
        title = "The span of dates given for the King's Mirror, counted in years",
        originalTitle = "Span of a dating range",
        author = "Scholars of Old Norse texts",
        year = 1250,
        summary = "Accounts give the King's Mirror a range of dates from about 1250 to 1263, and a short program counts the years between the two ends and the years in the range.",
        details = "The range is the one named in accounts of the book, and the example counts the years between its ends and the years it includes. The count is arithmetic on the range only and says nothing about when the book was finished.",
        instructions = """
            1. Save the example as dating_span.py.
            2. Run it: python3 dating_span.py
        """,
        example = """
            FIRST = 1250   # earliest date given for the King's Mirror
            LAST = 1263    # latest date given for the King's Mirror
            print("years between the ends:", LAST - FIRST)
            print("years in the range:", LAST - FIRST + 1)
        """,
    ),

    entry(
        databaseId = 79,
        kind = Kind.EXAMPLE,
        section = "Calendars",
        title = "The Coptic years of 1250 to 1254 counted from the Era of Martyrs",
        originalTitle = "Era of Martyrs",
        author = "The Coptic church of Egypt",
        year = 1250,
        summary = "The Coptic calendar counts its years from the Era of Martyrs, which begins in 284 of the Christian era, so a short program subtracts 284 from each year.",
        details = "The example prints the Coptic year for each Christian year from 1250 to 1254. It leaves aside the Coptic new year, which falls in September, and gives the plain subtraction only.",
        rare = true,
        instructions = """
            1. Save the example as coptic_years.py.
            2. Run it: python3 coptic_years.py
        """,
        example = """
            OFFSET = 284   # the Era of Martyrs begins in 284 of the Christian era
            for year in range(1250, 1255):
                print(year, "is Coptic year", year - OFFSET)
        """,
    ),

    entry(
        databaseId = 79,
        kind = Kind.EXAMPLE,
        section = "Persian",
        title = "The chapters of Saadi's Gulistan and Bustan, added up",
        originalTitle = "Chapters of two books",
        author = "Saadi of Shiraz",
        year = 1258,
        summary = "Saadi's Gulistan has eight chapters and his Bustan has ten, and a short program adds the two counts to a total of eighteen.",
        details = "The chapter counts are the usual figures for the two books, and the example prints each count and their sum. The total shows only the arithmetic, not any feature of the texts themselves.",
        instructions = """
            1. Save the example as persian_chapters.py.
            2. Run it: python3 persian_chapters.py
        """,
        example = """
            gulistan = 8   # chapters of the Gulistan
            bustan = 10    # chapters of the Bustan
            print("Gulistan chapters:", gulistan)
            print("Bustan chapters:", bustan)
            print("total:", gulistan + bustan)
        """,
    ),

    entry(
        databaseId = 79,
        kind = Kind.EXAMPLE,
        section = "Mongol court",
        title = "Ways to match four liquors to the four pipes of the silver tree",
        originalTitle = "Permutations of four",
        author = "Guillaume Boucher, a goldsmith of Paris",
        year = 1254,
        summary = "Four liquors can be matched to four pipes in 24 different orders, which a short program gets from the factorial of four.",
        details = "The count is the number of ways to order four items, and the example uses the standard factorial function. The silver tree of 1254 is the setting, but the count is pure arithmetic and says nothing about how the tree was actually fitted.",
        instructions = """
            1. Save the example as tree_orders.py.
            2. Run it: python3 tree_orders.py
        """,
        example = """
            from math import factorial

            print("ways to match four liquors to four pipes:", factorial(4))
        """,
    ),

    entry(
        databaseId = 79,
        kind = Kind.EXAMPLE,
        section = "Woodblocks",
        title = "Shelving the Tripitaka blocks in cases of one thousand",
        originalTitle = "Cases of blocks",
        author = "The keepers of the Tripitaka Koreana",
        year = 1251,
        summary = "At one thousand blocks to a case, the usual count of the Tripitaka Koreana blocks fills eighty-one full cases with 258 blocks left over.",
        details = "The count of 81,258 blocks is the usual figure, and the case size is illustrative, since the real storage at Haeinsa is spread over several halls. The example shows the division with its remainder.",
        instructions = """
            1. Save the example as block_cases.py.
            2. Run it: python3 block_cases.py
        """,
        example = """
            BLOCKS = 81258   # usual count of Tripitaka blocks
            CASE = 1000      # illustrative number of blocks in one case
            print("full cases:", BLOCKS // CASE)
            print("blocks left over:", BLOCKS % CASE)
        """,
    ),

)
