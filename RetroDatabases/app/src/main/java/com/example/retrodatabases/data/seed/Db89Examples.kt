package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1150 to 1160: counts of visions, distinctions and books, the years of a Genoese chronicle, two recorded weights of a silver plate and a dating window. */
val db89Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 89,
        kind = Kind.EXAMPLE,
        section = "Hildegard",
        title = "The visions of Scivias counted by book",
        originalTitle = "Scivias visions",
        author = "Hildegard of Bingen",
        year = 1151,
        summary = "The three books of Hildegard's Scivias hold six, seven and thirteen visions, and a short program adds them to give the total of twenty-six.",
        details = "The counts follow the usual division of the Scivias into three books, and the program adds them so that the total can be checked by hand. The visions are counted as the book presents them, one count for each book.",
        instructions = """
            1. Save the example as visions.py.
            2. Run it: python3 visions.py
        """,
        example = """
            books = {"book 1": 6, "book 2": 7, "book 3": 13}
            for name, count in books.items():
                print(name, count)
            print("visions in all:", sum(books.values()))
        """,
    ),

    entry(
        databaseId = 89,
        kind = Kind.EXAMPLE,
        section = "Theology",
        title = "The distinctions in the four books of Peter Lombard's Sentences",
        originalTitle = "Libri quattuor Sententiarum",
        author = "Peter Lombard",
        year = 1155,
        summary = "The four books of the Sentences hold 48, 44, 40 and 50 distinctions, and a short program adds them to give 182 in all.",
        details = "The distinctions are the numbered sections into which Lombard cut each book, and the counts are the usual ones for the printed text. The program prints each book's count and then the total, so the sum can be checked by hand.",
        instructions = """
            1. Save the example as distinctions.py.
            2. Run it: python3 distinctions.py
        """,
        example = """
            books = [48, 44, 40, 50]    # distinctions in books 1 to 4, as usually counted
            for number, count in enumerate(books, start=1):
                print("book", number, count)
            print("distinctions in all:", sum(books))
        """,
    ),

    entry(
        databaseId = 89,
        kind = Kind.EXAMPLE,
        section = "Chronicles",
        title = "The years that Caffaro's Annals of Genoa cover",
        originalTitle = "Annales Ianuenses",
        author = "Caffaro di Rustico",
        year = 1152,
        summary = "Caffaro's annals of Genoa cover the years 1099 to 1163, and a short program counts the years of that span and the years after his petition of 1152.",
        details = "The span is taken from the Paris manuscript of the annals, which lists his section as 1099 to 1163. Counting both ends gives 65 years, and the eleven years from the petition to the last year are counted the same way.",
        instructions = """
            1. Save the example as annals.py.
            2. Run it: python3 annals.py
        """,
        example = """
            first, last, petition = 1099, 1163, 1152
            print("span in years:", last - first + 1)
            print("years from the petition to the last year:", last - petition)
        """,
    ),

    entry(
        databaseId = 89,
        kind = Kind.EXAMPLE,
        section = "Political thought",
        title = "The books of John of Salisbury's Policraticus and Metalogicon",
        originalTitle = "Policraticus and Metalogicon",
        author = "John of Salisbury",
        year = 1159,
        summary = "John of Salisbury's two books of 1159 have eight and four books, and a short program adds the books of both to give twelve.",
        details = "Both books were completed in 1159, and the counts are the numbers of books in each work as usually given. The program prints the count for each book and the sum, so the total can be checked by hand.",
        instructions = """
            1. Save the example as books59.py.
            2. Run it: python3 books59.py
        """,
        example = """
            books = {"Policraticus": 8, "Metalogicon": 4}   # books in each work
            for name, count in books.items():
                print(name, count)
            print("books in both:", sum(books.values()))
        """,
    ),

    entry(
        databaseId = 89,
        kind = Kind.EXAMPLE,
        section = "Geography",
        title = "The two recorded weights of Roger II's silver plate",
        originalTitle = "Silver plate of Roger II",
        author = "Muhammad al-Idrisi",
        year = 1154,
        summary = "Two accounts give the weight of Roger II's silver plate as 300 pounds and 450 pounds, and a short program works out the gap and the ratio between them.",
        details = "The plate is the silver disc on which the map was said to be engraved, and the sources disagree on its weight, so the two figures are reports rather than a measurement. The program prints the gap and the ratio as exact fractions, so the result is the same on every run.",
        instructions = """
            1. Save the example as plate.py.
            2. Run it: python3 plate.py
        """,
        example = """
            from fractions import Fraction

            lower, higher = 300, 450    # pounds, from two accounts that disagree
            print("lower account:", lower, "pounds")
            print("higher account:", higher, "pounds")
            print("gap:", higher - lower, "pounds")
            print("ratio:", Fraction(higher, lower))
        """,
        rare = true,
    ),

    entry(
        databaseId = 89,
        kind = Kind.EXAMPLE,
        section = "Old Norse",
        title = "The dating window of the First Grammatical Treatise",
        originalTitle = "Fyrsta malfraediritgerdin",
        author = "Hreinn Benediktsson's dating",
        year = 1150,
        summary = "Hreinn Benediktsson's dating window for the First Grammatical Treatise runs from 1125 to 1175, and a short program finds its width and its midpoint.",
        details = "The treatise is anonymous, so the window is the only dating evidence used here, and its midpoint of about 1150 is the year given for the record. The program prints the width of the window and its midpoint, the two numbers a reader needs to compare the range with other dates.",
        instructions = """
            1. Save the example as window.py.
            2. Run it: python3 window.py
        """,
        example = """
            early, late = 1125, 1175    # dating window of the treatise
            print("window:", early, "to", late, "is", late - early, "years")
            print("midpoint:", (early + late) // 2)
        """,
        rare = true,
    ),

)
