package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1340 to 1349: the founders of the Garter, Villani's books, the chapters of the Philobiblon, the faculties of Prague, years between two works and a Serbian reign. */
val db70Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 70,
        kind = Kind.EXAMPLE,
        section = "Orders",
        title = "Founders of the Garter counted, the king with his companions",
        originalTitle = "Companions of the Garter",
        author = "Edward III and his first companions",
        year = 1348,
        summary = "The first fellowship of the Garter was the king with twenty-five companions, and a short program counts the companions and adds the sovereign.",
        details = "The traditional count gives the sovereign and twenty-five companions as the founders, and the example adds the king to the companions to reach the total. The count is the usual one in histories of the order, and the output is fixed.",
        instructions = """
            1. Save the example as garter.py.
            2. Run it: python3 garter.py
        """,
        example = """
            companions = 25                  # the first companions, as commonly given
            founders = companions + 1        # the sovereign joins the fellowship
            print("companions:", companions)
            print("founders with the sovereign:", founders)
        """,
    ),

    entry(
        databaseId = 70,
        kind = Kind.EXAMPLE,
        section = "Chronicles",
        title = "The twelve books of Villani's Nuova Cronica, counted in a list",
        originalTitle = "Nuova Cronica",
        author = "Giovanni Villani",
        year = 1348,
        summary = "Giovanni Villani's chronicle is divided into twelve books, and a short program lists them in order and counts them.",
        details = "Villani wrote the books over the years from about 1300 to his death in 1348, and his brother Matteo continued the history in a further set of books. The example lists the twelve books of Giovanni's chronicle, so the count can be checked by hand.",
        instructions = """
            1. Save the example as villani_books.py.
            2. Run it: python3 villani_books.py
        """,
        example = """
            BOOKS = 12   # books of Giovanni Villani's Nuova Cronica
            for book in range(1, BOOKS + 1):
                print("book", book)
            print("books in the chronicle:", BOOKS)
        """,
    ),

    entry(
        databaseId = 70,
        kind = Kind.EXAMPLE,
        section = "Books",
        title = "The twenty chapters of the Philobiblon, summed by number",
        originalTitle = "Philobiblon",
        author = "Richard de Bury",
        year = 1345,
        summary = "The Philobiblon has twenty chapters, and a short program counts them and adds their numbers to give 210.",
        details = "The chapters are numbered from one to twenty, and their numbers add up to the triangular total of 210. The example prints the count and the sum, so both can be checked by hand.",
        instructions = """
            1. Save the example as philobiblon.py.
            2. Run it: python3 philobiblon.py
        """,
        example = """
            CHAPTERS = 20   # the Philobiblon is divided into twenty chapters
            numbers = list(range(1, CHAPTERS + 1))
            print("chapters:", len(numbers))
            print("sum of the chapter numbers:", sum(numbers))
        """,
    ),

    entry(
        databaseId = 70,
        kind = Kind.EXAMPLE,
        section = "Universities",
        title = "The four faculties of Charles University, counted in a list",
        originalTitle = "Facultates",
        author = "Charles IV of Bohemia",
        year = 1348,
        summary = "Charles University was organised in four faculties, arts, law, medicine and theology, and a short program lists them in order and counts them.",
        details = "The four faculties followed the pattern of the older universities of Paris and Bologna, with arts as the foundation for the three higher faculties. The example lists the four in order and counts them, so the output can be checked by hand.",
        instructions = """
            1. Save the example as faculties.py.
            2. Run it: python3 faculties.py
        """,
        example = """
            FACULTIES = ["arts", "law", "medicine", "theology"]
            for number, faculty in enumerate(FACULTIES, start=1):
                print(number, faculty)
            print("faculties:", len(FACULTIES))
        """,
    ),

    entry(
        databaseId = 70,
        kind = Kind.EXAMPLE,
        section = "Years",
        title = "Years from the Ayenbite of Inwyt to the founding of the Garter",
        originalTitle = "Years between two dated works",
        author = "Dan Michel and Edward III",
        year = 1348,
        summary = "The Ayenbite of Inwyt was finished in 1340 and the Order of the Garter was founded in 1348, and a short program counts the years between them.",
        details = "Both dates are given as commonly stated, and the example counts whole years by subtraction. The span has no leap-year rule to check, so the answer is simply the difference of the two years.",
        instructions = """
            1. Save the example as years_between.py.
            2. Run it: python3 years_between.py
        """,
        example = """
            AYENBITE = 1340   # the Ayenbite of Inwyt is finished, as commonly given
            GARTER = 1348     # the Order of the Garter is founded, as commonly given
            print("years between:", GARTER - AYENBITE)
        """,
    ),

    entry(
        databaseId = 70,
        kind = Kind.EXAMPLE,
        section = "Rulers",
        title = "Years of Stefan Dusan's reign and the gap to his code's additions",
        originalTitle = "Reign years",
        author = "Stefan Dusan, king of Serbia",
        year = 1349,
        summary = "Stefan Dusan ruled Serbia from 1331 to his death in 1355, and a short program counts his years of reign and the gap between his code and its additions.",
        details = "The dates are given as commonly stated, and the example counts years by subtraction. The code of 1349 was extended in 1354, so the gap is five years, and the output can be checked by hand.",
        instructions = """
            1. Save the example as dusan.py.
            2. Run it: python3 dusan.py
        """,
        example = """
            REIGN_START = 1331   # Dusan's accession, as commonly given
            REIGN_END = 1355     # his death, as commonly given
            CODE = 1349          # the Zakonik is issued
            ADDITIONS = 1354     # the later additions
            print("years of reign:", REIGN_END - REIGN_START)
            print("years from the code to its additions:", ADDITIONS - CODE)
        """,
    ),

)
