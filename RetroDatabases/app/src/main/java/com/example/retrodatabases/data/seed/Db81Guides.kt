package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1230 to 1235: a concordance's word places, a pointed arch, a five-part letter and a mason's mark counted by stone. */
val db81Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 81,
        kind = Kind.GUIDE,
        section = "Index",
        title = "How a concordance lists the places of each word",
        originalTitle = "Concordance",
        author = "The friars of St Jacques",
        year = 1230,
        summary = "A concordance lists each word of a text in alphabetical order with its places, and a short program builds the same kind of list for a Latin verse.",
        details = "Hugh of Saint-Cher's concordance of about 1230 gave each word with its book and chapter, sorted by letter, so that a reader could find the places where a word is used. The example counts places as word numbers from 1 and prints the list for one verse, so the output can be checked by hand.",
        instructions = """
            1. Save the example as concordance.py.
            2. Run it: python3 concordance.py
        """,
        example = """
            text = "In principio erat Verbum et Verbum erat apud Deum et Deus erat Verbum"
            places = {}
            for position, word in enumerate(text.lower().split(), start=1):
                places.setdefault(word, []).append(position)
            for word in sorted(places):
                print(word, " ".join(str(p) for p in places[word]))
            print("distinct words:", len(places))
            print("words in the verse:", len(text.split()))
        """,
    ),

    entry(
        databaseId = 81,
        kind = Kind.GUIDE,
        section = "Geometry",
        title = "How a pointed arch is struck from two centres",
        originalTitle = "Pointed arch setting-out",
        author = "Gothic masons",
        year = 1230,
        summary = "A pointed arch of two equal arcs is struck from two centres one span apart, and a short program prints the centres and the height of the crown for two spans.",
        details = "Each arc has the span as its radius, so the two arcs meet above the middle at a height equal to the span times the square root of three over two, the height of an equilateral triangle. The example prints the figures for spans of 10 and 6 units, and this form is one of the common pointed arches of the period.",
        instructions = """
            1. Save the example as arch.py.
            2. Run it: python3 arch.py
        """,
        example = """
            import math

            for span in (10, 6):
                left = (0.0, 0.0)
                right = (float(span), 0.0)
                crown_x = span / 2
                crown_y = span * math.sqrt(3) / 2
                print(f"span {span}: centres at {left[0]:.0f} and {right[0]:.0f}")
                print(f"span {span}: crown at {crown_x:.3f} {crown_y:.3f}")
        """,
    ),

    entry(
        databaseId = 81,
        kind = Kind.GUIDE,
        section = "Letters",
        title = "How a medieval letter is set out in five parts",
        originalTitle = "Ars dictaminis",
        author = "Letter-writing masters of Bologna",
        year = 1235,
        summary = "A medieval letter was set out in five parts, greeting, opening, narrative, request and closing, and a short program counts the words in each part of a sample letter.",
        details = "Boncompagno's Rhetorica novissima of 1235 is one of the manuals that teach this five-part form, and later letter-writers copied it. The example gives a short sample letter with one sentence for each part, and it prints the words in each part so the totals can be checked by hand.",
        instructions = """
            1. Save the example as letter.py.
            2. Run it: python3 letter.py
        """,
        example = """
            parts = [
                ("salutatio", "greetings to the lord abbot"),
                ("exordium", "since the matter is grave"),
                ("narratio", "the mill of the house was lost in the flood"),
                ("petitio", "we ask that the mill be rebuilt"),
                ("conclusio", "farewell"),
            ]
            total = 0
            for number, (part, text) in enumerate(parts, start=1):
                count = len(text.split())
                total += count
                print(number, part, count)
            print("words in the letter:", total)
        """,
    ),

    entry(
        databaseId = 81,
        kind = Kind.GUIDE,
        section = "Building",
        title = "How a mason's mark on each stone counts a cutter's work",
        originalTitle = "Mason's marks",
        author = "Masons of the cathedral yards",
        year = 1230,
        summary = "A mason cut a personal mark on each stone he finished, so that the stones of each cutter could be counted, and a short program tallies a sample of marked stones.",
        details = "Marks of this kind are found on the stones of medieval churches, where they are read as signs of who cut each stone. The example uses an illustrative sample of seven stones with the marks a, b and c, and it prints the count for each mark and the total.",
        instructions = """
            1. Save the example as marks.py.
            2. Run it: python3 marks.py
        """,
        example = """
            marks = ["a", "b", "a", "c", "a", "b", "a"]   # illustrative sample of seven stones
            counts = {}
            for mark in marks:
                counts[mark] = counts.get(mark, 0) + 1
            for mark in sorted(counts):
                print("mark", mark, counts[mark])
            print("stones:", len(marks))
        """,
    ),

)
