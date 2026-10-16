package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1480 to 1490: vowels in Dante's first tercet, a Hebrew line read backwards, a pile of shot, a proof check, a lunar year and a search for a year. */
val db56Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 56,
        kind = Kind.EXAMPLE,
        section = "Verse",
        title = "Counting the vowels of Dante's first tercet",
        originalTitle = "La Comedia",
        author = "Dante Alighieri",
        year = 1481,
        summary = "Dante's first tercet has thirty-six vowels, and a short program counts each vowel letter in the three lines of the Italian text.",
        details = "The three lines open the Inferno, and the example writes them in plain letters, with the accent of che left out so that the text stays in ASCII. The count ignores case, and the program prints the total for each vowel and the sum of all of them.",
        instructions = """
            1. Save the example as tercet_vowels.py.
            2. Run it: python3 tercet_vowels.py
        """,
        example = """
            lines = [
                "Nel mezzo del cammin di nostra vita",
                "mi ritrovai per una selva oscura,",
                "che la diritta via era smarrita.",
            ]
            text = " ".join(lines).lower()
            for vowel in "aeiou":
                print(f"{vowel}: {text.count(vowel)}")
            print(f"total: {sum(text.count(v) for v in 'aeiou')}")
        """,
    ),

    entry(
        databaseId = 56,
        kind = Kind.EXAMPLE,
        section = "Scripts",
        title = "Reading a Hebrew line from right to left",
        originalTitle = "Bereshit bara elohim",
        author = "Printers of the Soncino Hebrew Bible",
        year = 1488,
        summary = "A Hebrew line runs from right to left, so its first word stands at the right end, and a short program prints the words of a line in both orders.",
        details = "The example takes the first three words of Genesis in transliteration and reverses their order to show how the line reads on the page. Hebrew books of the Soncino press of 1488 were printed in this direction, and the example prints no Hebrew letters, only the transliterated words.",
        instructions = """
            1. Save the example as hebrew_line.py.
            2. Run it: python3 hebrew_line.py
        """,
        example = """
            words = ["bereshit", "bara", "elohim"]   # the first three words of Genesis, transliterated
            print("order of the words in the Hebrew line:", " ".join(words))
            print("order on the page, left to right:", " ".join(reversed(words)))
        """,
    ),

    entry(
        databaseId = 56,
        kind = Kind.EXAMPLE,
        section = "Artillery",
        title = "A square pile of round shot",
        originalTitle = "Round shot in a square pile",
        author = "Gunners of the 1480s",
        year = 1482,
        summary = "A square pile of round shot has a square of balls on each layer, one above the next, and a short program counts the balls for piles of three, four and five layers.",
        details = "Each layer of a square pile is a square of balls, so the total for n layers adds the squares of the first n numbers, one, four, nine and so on. The example prints the totals for three, four and five layers, and it is an illustration of stacking rather than a record of any stores of the period.",
        instructions = """
            1. Save the example as shot_pile.py.
            2. Run it: python3 shot_pile.py
        """,
        example = """
            for layers in (3, 4, 5):
                balls = sum(i * i for i in range(1, layers + 1))
                print(f"{layers} layers: {balls} balls")
        """,
    ),

    entry(
        databaseId = 56,
        kind = Kind.EXAMPLE,
        section = "Proofs",
        title = "Finding the letter that differs between a copy and its proof",
        originalTitle = "Copy and proof sheet",
        author = "Correctors and compositors",
        year = 1484,
        summary = "A corrector sets a proof line beside the copy and finds the letter that differs, and a short program prints the column where the two disagree.",
        details = "The example compares a line of the copy with a proof line of the same length, column by column, and reports each mismatch with its column counted from one. The two lines are fixed, so the single changed letter is known in advance.",
        instructions = """
            1. Save the example as proof_check.py.
            2. Run it: python3 proof_check.py
        """,
        example = """
            copy = "the printer set the type"
            proof = "the printer set the tupe"
            mismatches = 0
            for column, (a, b) in enumerate(zip(copy, proof), start=1):
                if a != b:
                    mismatches += 1
                    print(f"column {column}: copy {a}, proof {b}")
            print(f"mismatches: {mismatches}")
        """,
    ),

    entry(
        databaseId = 56,
        kind = Kind.EXAMPLE,
        section = "Calendar",
        title = "A lunar year of twelve months against the solar year",
        originalTitle = "Lunar months",
        author = "Astronomers of the Alfonsine tables",
        year = 1483,
        summary = "Twelve lunar months of alternating 30 and 29 days make 354 days, eleven days short of the solar year, and a short program adds them up.",
        details = "The Alfonsine tables of 1483 reckon the motion of the moon, and the example uses the simple alternation of full and hollow months rather than the exact motions in the tables. The difference from 365 days is the amount by which twelve lunar months fall short of a year of that length.",
        instructions = """
            1. Save the example as lunar_year.py.
            2. Run it: python3 lunar_year.py
        """,
        example = """
            months = [30, 29] * 6   # alternating full and hollow months, twelve in all
            lunar_year = sum(months)
            print(f"twelve lunar months: {lunar_year} days")
            print(f"difference from 365 days: {365 - lunar_year}")
        """,
    ),

    entry(
        databaseId = 56,
        kind = Kind.EXAMPLE,
        section = "Searching",
        title = "Searching the years of the decade by halves",
        originalTitle = "Binary search",
        author = "Arithmetic and computing practitioners",
        year = 1480,
        summary = "A binary search halves a sorted list at each step, and a short program finds the year 1486 in the list of years from 1480 to 1490 and prints each probe.",
        details = "The list holds the eleven years of the decade in order, and the search looks at the middle entry first and then keeps the half that can still hold the target. The example prints each probe and the position where the year is found.",
        instructions = """
            1. Save the example as year_search.py.
            2. Run it: python3 year_search.py
        """,
        example = """
            years = list(range(1480, 1491))
            target = 1486
            lo, hi, probes = 0, len(years) - 1, 0
            while lo <= hi:
                mid = (lo + hi) // 2
                probes += 1
                print(f"probe {years[mid]}")
                if years[mid] == target:
                    print(f"found {target} at position {mid} after {probes} probes")
                    break
                if years[mid] < target:
                    lo = mid + 1
                else:
                    hi = mid - 1
        """,
    ),

)
