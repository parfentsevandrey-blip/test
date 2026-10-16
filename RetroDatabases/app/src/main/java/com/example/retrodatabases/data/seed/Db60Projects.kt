package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1440 to 1450: a code's cross-references, a canon's scroll numbers, the syllables of a Hangul verse and a binary search for a chapter. */
val db60Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 60,
        kind = Kind.PROJECT,
        section = "Law codes",
        title = "A cross-reference index for a five-book code",
        originalTitle = "Ordenacoes Afonsinas",
        author = "Afonso V of Portugal",
        year = 1446,
        summary = "A cross-reference index turns each article's references into a list of the articles that cite it, and a short program builds that index for an illustrative code.",
        details = "The Afonsine Ordinances of 1446 were arranged in five books, and their articles cite one another. The example builds the index for five illustrative articles and prints which articles cite each one.",
        instructions = """
            1. Save the example as code_index.py.
            2. Run it: python3 code_index.py
        """,
        example = """
            # Illustrative articles of a short code; each lists the articles it refers to.
            ARTICLES = {1: [2, 3], 2: [3], 3: [1], 4: [1, 2, 3], 5: []}

            index = {}
            for article, refs in ARTICLES.items():
                for ref in refs:
                    index.setdefault(ref, []).append(article)

            for ref in sorted(index):
                print(f"article {ref} is cited by {sorted(index[ref])}")
            print("articles that cite nothing:", [a for a in sorted(ARTICLES) if not ARTICLES[a]])
        """,
    ),

    entry(
        databaseId = 60,
        kind = Kind.PROJECT,
        section = "Canons",
        title = "Scroll numbers of a canon, from ranges to labels",
        originalTitle = "Zhengtong Daozang",
        author = "Ming court editors",
        year = 1445,
        summary = "A canon is numbered in blocks of scrolls, and a short program expands those blocks into zero-padded labels and counts them.",
        details = "The Zhengtong Daozang of 1445 was arranged in numbered scrolls, and the example expands three illustrative blocks of numbers into labels. The block numbers are an illustration, not the canon's own numbering.",
        instructions = """
            1. Save the example as scroll_labels.py.
            2. Run it: python3 scroll_labels.py
        """,
        example = """
            # Illustrative blocks of scroll numbers, each given by its first and last number.
            BLOCKS = [(1, 3), (10, 11), (20, 20)]

            labels = []
            for first, last in BLOCKS:
                labels.extend(f"scroll {n:03d}" for n in range(first, last + 1))
            print("\n".join(labels))
            print("total scrolls:", len(labels))
        """,
    ),

    entry(
        databaseId = 60,
        kind = Kind.PROJECT,
        section = "Verse",
        title = "The first sound of each syllable in a Hangul line",
        originalTitle = "Yongbi eocheonga",
        author = "King Sejong's scholars",
        year = 1447,
        summary = "A romanized Hangul line gives a short code when the first letter of each syllable is read in order, and a short program prints that code for three illustrative lines.",
        details = "The Yongbi eocheonga, printed in 1447, is a long Korean poem. The example reads the first letter of each romanized syllable in three illustrative lines, so the codes show the method and not the poem's own text.",
        instructions = """
            1. Save the example as first_sounds.py.
            2. Run it: python3 first_sounds.py
        """,
        example = """
            # Illustrative lines of verse in romanized form; each syllable is written as one word.
            LINES = ["ha nal i gop da", "gang chon e ne ri", "sa rang eu ro ha"]

            total = 0
            for number, line in enumerate(LINES, start=1):
                syllables = line.split()
                total += len(syllables)
                print(f"line {number}: " + "".join(s[0] for s in syllables))
            print("syllables in all lines:", total)
        """,
    ),

    entry(
        databaseId = 60,
        kind = Kind.PROJECT,
        section = "Buddhist texts",
        title = "Finding the chapter that holds a page, by binary search",
        originalTitle = "Seokbo sangjeol",
        author = "Joseon court compilers",
        year = 1447,
        summary = "A binary search finds the chapter that holds a page by halving a sorted list of chapter starts, and a short program applies it to five illustrative starting pages.",
        details = "The Seokbo sangjeol of 1447 is organised in chapters. The example uses five illustrative starting pages, not the book's pagination, and shows how bisect returns the chapter number for each page.",
        rare = true,
        instructions = """
            1. Save the example as chapter_search.py.
            2. Run it: python3 chapter_search.py
        """,
        example = """
            import bisect

            # Illustrative starting pages of five chapters; the numbers are an example, not the book's pagination.
            STARTS = [1, 12, 30, 47, 66]

            def chapter_of(page):
                # bisect_right counts the chapters that start at or before the page, and that count is its chapter number.
                return bisect.bisect_right(STARTS, page)

            for page in (1, 11, 31, 66, 70):
                print(f"page {page}: chapter {chapter_of(page)}")
        """,
    ),

)
