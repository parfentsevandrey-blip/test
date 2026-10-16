package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1490 to 1500: a printed letter's travel, Castilian line breaks, the chapters of the Pentateuch and a long romance read at a steady pace. */
val db55Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 55,
        kind = Kind.PROJECT,
        section = "News",
        title = "How long a printed letter takes to cross Europe",
        originalTitle = "Epistola de insulis nuper inventis",
        author = "Christopher Columbus",
        year = 1493,
        summary = "A letter printed in 1493 passed from one printing city to the next, and at an illustrative pace a short program counts the days for each leg of its journey.",
        details = "The distances and the pace are illustrative, not the real routes or speeds of any courier of the time. The program rounds each leg up to whole days and adds the legs, so the output follows from the figures in the example.",
        instructions = """
            1. Save the example as letter_travel.py.
            2. Run it: python3 letter_travel.py
        """,
        example = """
            import math

            # Distances and pace are illustrative, not the real routes or speeds of any courier.
            LEGS = [("Barcelona", "Rome", 1300), ("Rome", "Paris", 1600)]
            PACE = 40
            total = 0
            for start, end, miles in LEGS:
                days = math.ceil(miles / PACE)
                total += days
                print(f"{start} to {end}: {miles} miles at {PACE} a day takes {days} days")
            print(f"total for both legs: {total} days")
        """,
    ),

    entry(
        databaseId = 55,
        kind = Kind.PROJECT,
        section = "Line breaks",
        title = "Hyphenating Castilian words at a line's end",
        originalTitle = "Gramatica de la lengua castellana",
        author = "Elio Antonio de Nebrija",
        year = 1492,
        summary = "A printer who hyphenates a Castilian word at a line's end breaks it at a syllable, and this program applies a simple vowel rule to five words.",
        details = "The rule is a simplified one written for the example, not Nebrija's own, and the five words are chosen so that the rule gives the usual breaks. The output is fixed by the words, so the example can be checked by hand.",
        instructions = """
            1. Save the example as hyphenate_castilian.py.
            2. Run it: python3 hyphenate_castilian.py
        """,
        example = """
            VOWELS = "aeiou"
            TOGETHER = {"br", "bl", "cr", "cl", "dr", "fr", "gr", "gl", "pr", "pl", "tr", "kr"}

            def hyphenate(word):
                # A single consonant between vowels starts the next syllable; a pair does so only if it is a true cluster.
                cuts = []
                i = 0
                while i < len(word):
                    if word[i] in VOWELS:
                        j = i + 1
                        while j < len(word) and word[j] not in VOWELS:
                            j += 1
                        run = word[i + 1:j]
                        if j < len(word) and len(run) == 1:
                            cuts.append(i + 1)
                        elif j < len(word) and len(run) == 2:
                            cuts.append(i + 1 if run in TOGETHER else i + 2)
                        i = j
                    else:
                        i += 1
                pieces, last = [], 0
                for cut in cuts:
                    pieces.append(word[last:cut])
                    last = cut
                pieces.append(word[last:])
                return "-".join(pieces)

            for word in ("gramatica", "lengua", "imperio", "palabra", "reino"):
                print(f"{word}: {hyphenate(word)}")
        """,
    ),

    entry(
        databaseId = 55,
        kind = Kind.PROJECT,
        section = "Chapters",
        title = "Chapters of the five books of Moses in a Bible of 1490",
        originalTitle = "Biblia volgare",
        author = "Nicolo Malermi",
        year = 1490,
        summary = "The five books of Moses that open the Bible have 187 chapters in the modern division, and this program adds them book by book.",
        details = "The chapter counts are those of the modern division, and the example makes no claim about how Malermi's text divides its chapters. The output lists each book and the total for the five.",
        instructions = """
            1. Save the example as pentateuch_chapters.py.
            2. Run it: python3 pentateuch_chapters.py
        """,
        example = """
            # Chapters of the five books of Moses in the usual modern division.
            BOOKS = [("Genesis", 50), ("Exodus", 40), ("Leviticus", 27), ("Numbers", 36), ("Deuteronomy", 34)]
            for name, chapters in BOOKS:
                print(f"{name}: {chapters} chapters")
            print("chapters in the Pentateuch:", sum(chapters for _, chapters in BOOKS))
        """,
    ),

    entry(
        databaseId = 55,
        kind = Kind.PROJECT,
        section = "Reading",
        title = "Days needed to read a long romance at a steady pace",
        originalTitle = "Tirant lo Blanc",
        author = "Joanot Martorell",
        year = 1490,
        summary = "A reader who takes a long romance at a steady number of pages a day can plan the reading in whole days, and this program does the sum for three illustrative readings.",
        details = "The page counts and the daily pace are illustrative, not the length of any printed copy of the romance. The program rounds each reading up to whole days, so the output follows from the figures in the example.",
        instructions = """
            1. Save the example as reading_plan.py.
            2. Run it: python3 reading_plan.py
        """,
        example = """
            import math

            # Page counts and the daily pace are illustrative, not the length of any printed copy.
            READINGS = [("reading A", 480), ("reading B", 490), ("reading C", 300)]
            PAGES_PER_DAY = 25
            total = 0
            for name, pages in READINGS:
                days = math.ceil(pages / PAGES_PER_DAY)
                total += days
                print(f"{name}: {pages} pages at {PAGES_PER_DAY} a day takes {days} days")
            print("days for all three readings:", total)
        """,
    ),

)
