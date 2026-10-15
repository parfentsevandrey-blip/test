package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1550 to 1560: a press schedule, a verse index, a keyed-shift checker and a grille checker. */
val db49Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 49,
        kind = Kind.PROJECT,
        section = "Printing",
        title = "A printing-house job schedule on two presses",
        originalTitle = "Officina Plantiniana",
        author = "Christophe Plantin",
        year = 1555,
        summary = "A small scheduler gives each print job to the press that becomes free first, and it reports the hour at which every job starts and ends.",
        details = "Plantin's printing house at Antwerp, begun in 1555, had several presses working on different jobs at once. The example schedules four illustrative jobs on two presses, with the hours chosen for the example and not taken from the firm's records.",
        instructions = """
            1. Save the example as job_schedule.py.
            2. Run it: python3 job_schedule.py
        """,
        example = """
            jobs = [("Psalter", 5), ("Missal", 3), ("Almanac", 2), ("Grammar", 4)]
            free = {"A": 0, "B": 0}   # the hour at which each press becomes free
            for name, hours in jobs:
                press = min(sorted(free), key=lambda p: free[p])
                start = free[press]
                free[press] = start + hours
                print(f"{name}: press {press}, hours {start} to {free[press]}")
            print("finished at hour", max(free.values()))
        """,
    ),

    entry(
        databaseId = 49,
        kind = Kind.PROJECT,
        section = "Citation",
        title = "A verse index that sorts references by chapter and verse",
        originalTitle = "Novum Testamentum graece",
        author = "Robert Estienne",
        year = 1551,
        summary = "A verse index sorts references by chapter and then by verse, so that the numbers read in their proper order rather than as text.",
        details = "Estienne's numbered verses of 1551 made it possible to index a text by chapter and verse. The example sorts four illustrative references as pairs of numbers, which keeps 1:10 after 1:2 where a plain text sort would put it first.",
        instructions = """
            1. Save the example as verse_index.py.
            2. Run it: python3 verse_index.py
        """,
        example = """
            refs = [(2, 3), (1, 10), (1, 2), (3, 1)]   # (chapter, verse) pairs in the order they were noted
            for chapter, verse in sorted(refs):
                print(f"{chapter}:{verse}")
        """,
    ),

    entry(
        databaseId = 49,
        kind = Kind.PROJECT,
        section = "Ciphers",
        title = "A keyed-shift checker that round-trips a list of words",
        originalTitle = "La cifra del Sig. Giovan Battista Bellaso",
        author = "Giovan Battista Bellaso",
        year = 1553,
        summary = "A keyed-shift checker enciphers each word of a list with the key CIFRA, deciphers it again, and confirms that every word comes back unchanged.",
        details = "Bellaso's keyed shifts of 1553 can be checked by running them in both directions. The project uses the two words BELLASO and TARTAGLIA, and the output shows each word, its ciphertext and its decipherment.",
        instructions = """
            1. Save the example as keyed_roundtrip.py.
            2. Run it: python3 keyed_roundtrip.py
        """,
        example = """
            KEY = "CIFRA"

            def shift(text, sign):
                out = ""
                for i, ch in enumerate(text):
                    k = ord(KEY[i % len(KEY)]) - ord("A")
                    out += chr((ord(ch) - ord("A") + sign * k) % 26 + ord("A"))
                return out

            words = ["BELLASO", "TARTAGLIA"]
            for word in words:
                cipher = shift(word, 1)
                print(f"{word} -> {cipher} -> {shift(cipher, -1)}")
            print("all words round-trip:", all(shift(shift(w, 1), -1) == w for w in words))
        """,
    ),

    entry(
        databaseId = 49,
        kind = Kind.PROJECT,
        section = "Catechisms",
        title = "Numbering a catechism section by section",
        originalTitle = "Summa doctrinae christianae",
        author = "Peter Canisius",
        year = 1555,
        summary = "A catechism of numbered questions can be totalled section by section, and a short program numbers the questions of an illustrative outline.",
        details = "Canisius's Summa doctrinae christianae of 1555 is a catechism of questions and answers. The example uses an invented outline of four sections, not the book's own numbering, so the totals are illustrative only.",
        instructions = """
            1. Save the example as catechism_questions.py.
            2. Run it: python3 catechism_questions.py
        """,
        example = """
            sections = {"God": 5, "Faith": 7, "Sacraments": 4, "Prayer": 3}   # illustrative counts of numbered questions
            total = 0
            for name, count in sections.items():
                numbers = range(total + 1, total + count + 1)
                print(f"{name}: questions {numbers[0]} to {numbers[-1]}, {count} in all")
                total += count
            print("numbered questions in all:", total)
        """,
    ),

)
