package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1440 to 1450: a text dated by its newest word, a rain depth, pilgrim averages, a running total of invented sample counts, a tarot count and syllables grouped by final sound. */
val db60Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 60,
        kind = Kind.EXAMPLE,
        section = "Textual criticism",
        title = "A text dated by its newest word, after Valla's method",
        originalTitle = "De falso credita et ementita Constantini donatione declamatio",
        author = "Lorenzo Valla",
        year = 1440,
        summary = "A text cannot be older than the newest word it uses, and a short program finds that earliest possible date from illustrative word years.",
        details = "Valla's treatise of 1440 tested the Donation of Constantine by its language, and the same rule is used here. The word years in the example are illustrative numbers, not historical dates, and they show only the method.",
        instructions = """
            1. Save the example as newest_word.py.
            2. Run it: python3 newest_word.py
        """,
        example = """
            # Illustrative years for when each word first appears in writing; the numbers show the method only.
            FIRST_USE = {"alpha": 300, "beta": 800, "gamma": 1250, "delta": 1420}

            def latest_year(words):
                # A text cannot be older than the newest word it uses.
                return max(FIRST_USE[w] for w in words)

            text = ["alpha", "gamma", "beta"]
            print("words:", " ".join(text))
            print("earliest possible date:", latest_year(text))
            print("with delta:", latest_year(text + ["delta"]))
        """,
    ),

    entry(
        databaseId = 60,
        kind = Kind.EXAMPLE,
        section = "Measurement",
        title = "Depth of rain from a gauge's volume and mouth",
        originalTitle = "Cheugugi",
        author = "Sejong's court",
        year = 1441,
        summary = "Rain caught in an open vessel stands at a depth equal to its volume divided by the area of its mouth, and a short program prints that depth as an exact fraction.",
        details = "Korean rain measures of 1441 were read as depths, and the example uses exact fractions for three illustrative readings. The volumes and mouth areas are illustrative numbers, not the dimensions of any surviving gauge.",
        instructions = """
            1. Save the example as gauge_depth.py.
            2. Run it: python3 gauge_depth.py
        """,
        example = """
            from fractions import Fraction as F

            def depth(volume, mouth_area):
                # Rain caught in an open vessel stands at a depth equal to its volume divided by the area of the mouth.
                return F(volume, mouth_area)

            for volume, area in ((12, 400), (30, 400), (7, 50)):
                print(f"volume {volume} over area {area}: depth {depth(volume, area)}")
        """,
    ),

    entry(
        databaseId = 60,
        kind = Kind.EXAMPLE,
        section = "Pilgrimage",
        title = "Average pilgrims per day in a Holy Year",
        originalTitle = "Anno Santo 1450",
        author = "Pope Nicholas V",
        year = 1450,
        summary = "An average over a run of days is the total divided by the number of days, and a short program prints the total, the mean and the busiest and quietest days.",
        details = "The Holy Year of 1450 drew pilgrims to Rome. The example uses five illustrative daily counts, not the figures of any chronicle, and rounds the mean to one decimal place.",
        instructions = """
            1. Save the example as pilgrim_average.py.
            2. Run it: python3 pilgrim_average.py
        """,
        example = """
            # Illustrative daily counts for five days; these are an example and not the figures of any chronicle.
            daily = [120, 80, 100, 151, 50]
            total = sum(daily)
            print("days:", len(daily))
            print("total:", total)
            print("average per day:", round(total / len(daily), 1))
            print("busiest day:", max(daily), "quietest day:", min(daily))
        """,
    ),

    entry(
        databaseId = 60,
        kind = Kind.EXAMPLE,
        section = "Practice data",
        title = "Running totals of invented sample counts, for practice",
        originalTitle = "Running totals of invented sample counts, for practice",
        author = "Invented for practice",
        year = 1446,
        summary = "The sample counts in this program are invented for practice, not records of the Bursfelde reform, and the program prints a running total for four sample years.",
        details = "The year 1446 is the date of the Bursfelde reform, which gave Benedictine houses shared statutes, but it does not date any count here. The numbers are invented sample figures for practising a running total, and the program only adds them in order.",
        instructions = """
            1. Save the example as sample_totals.py.
            2. Run it: python3 sample_totals.py
        """,
        example = """
            # Invented sample counts for practice; these numbers are not records of the Bursfelde reform.
            SAMPLE_COUNTS = {1446: 3, 1447: 2, 1448: 1, 1449: 4}

            total = 0
            for year in sorted(SAMPLE_COUNTS):
                total += SAMPLE_COUNTS[year]
                print(f"{year}: sample count {SAMPLE_COUNTS[year]}, running total {total}")
            print("sample total:", total)
        """,
    ),

    entry(
        databaseId = 60,
        kind = Kind.EXAMPLE,
        section = "Cards",
        title = "Cards in a tarot deck, counted by suit",
        originalTitle = "Tarocchi",
        author = "Painters at the Milanese court",
        year = 1450,
        summary = "A standard tarot deck has 22 major cards and 56 minor cards in four suits of 14, and a short program totals the deck from those numbers.",
        details = "The Visconti-Sforza tarot cards of about 1450 are among the oldest surviving packs. The example uses the usual counts of a tarot deck and prints the major cards, the minor cards and the whole deck.",
        instructions = """
            1. Save the example as tarot_count.py.
            2. Run it: python3 tarot_count.py
        """,
        example = """
            MAJOR_CARDS = 22
            SUITS = ["cups", "coins", "swords", "batons"]
            CARDS_PER_SUIT = 14   # ten numbered cards and four court cards in each suit

            minor_cards = len(SUITS) * CARDS_PER_SUIT
            print("major cards:", MAJOR_CARDS)
            print("minor cards:", minor_cards)
            print("whole deck:", MAJOR_CARDS + minor_cards)
        """,
    ),

    entry(
        databaseId = 60,
        kind = Kind.EXAMPLE,
        section = "Rhymes",
        title = "Syllables grouped by their final sound",
        originalTitle = "Dongguk Jeongun",
        author = "Sejong's scholars",
        year = 1447,
        summary = "Syllables can be grouped by the sound that ends them, as a rhyme standard groups its entries, and a short program builds the groups from illustrative syllables.",
        details = "The Dongguk Jeongun of 1447 arranged its entries by rhyme and final sound. The example groups seven illustrative romanized syllables by their final sound, prints the groups in sorted order of that sound, and keeps each group's syllables in the order given.",
        instructions = """
            1. Save the example as final_groups.py.
            2. Run it: python3 final_groups.py
        """,
        example = """
            # Illustrative romanized syllables, each paired with its final sound; an empty final means the syllable ends in a vowel.
            SYLLABLES = [("gang", "ng"), ("nam", "m"), ("sil", "l"), ("bang", "ng"), ("dong", "ng"), ("im", "m"), ("su", "")]

            groups = {}
            for syllable, final in SYLLABLES:
                groups.setdefault(final, []).append(syllable)
            for final in sorted(groups):
                label = final if final else "none"
                print(f"final {label}: {' '.join(groups[final])}")
        """,
    ),

)
