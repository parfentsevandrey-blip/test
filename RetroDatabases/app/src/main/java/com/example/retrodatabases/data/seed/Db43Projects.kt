package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1610 to 1620: written multiplication in Li Zhizao's manner, a count of languages, specific gravity from a balance and a headword count by letter. */
val db43Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 43,
        kind = Kind.PROJECT,
        section = "Arithmetic",
        title = "Written multiplication column by column, as in Li Zhizao's arithmetic",
        originalTitle = "Tongwen suanzhi",
        author = "Li Zhizao, with Matteo Ricci",
        year = 1614,
        summary = "Li Zhizao's arithmetic taught the European written method, in which a number is multiplied by each digit in turn and the partial products are added with their places.",
        details = "Ricci dictated the material and Li recorded it, and the book is dated to 1613 or 1614, since the sources differ on when it was completed and printed. It drew on Clavius's arithmetic, and the example multiplies 307 by 46 digit by digit, printing each partial product and the total.",
        instructions = """
            1. Save the example as written_multiply.py.
            2. Run it: python3 written_multiply.py
        """,
        example = """
            def written_multiply(a, b):
                # Each digit of b, from the units, multiplies a; each partial product is shifted by the place of its digit and added.
                total = 0
                for place, digit in enumerate(reversed(str(b))):
                    row = a * int(digit)
                    print(f"{a} x {digit} = {row}, in place {place}: {row * 10 ** place}")
                    total += row * 10 ** place
                return total

            print(f"total: {written_multiply(307, 46)}")
        """,
    ),

    entry(
        databaseId = 43,
        kind = Kind.PROJECT,
        section = "Languages",
        title = "Counting the languages of a list of regions, after Brerewood's Enquiries",
        originalTitle = "Enquiries touching the diversity of languages and religions",
        author = "Edward Brerewood",
        year = 1614,
        summary = "A program lists the languages of each region of an invented table and counts the distinct languages across the regions, in the spirit of Brerewood's inquiry into the diversity of languages.",
        details = "Brerewood's Enquiries of 1614, printed in London, took up the diversity of languages and religions across the world. The example uses an invented table of three regions, not Brerewood's own figures, and it prints the count of languages listed for each region and the total of distinct languages.",
        instructions = """
            1. Save the example as regions.py.
            2. Run it: python3 regions.py
        """,
        example = """
            # Invented table of regions and the languages listed for each, not Brerewood's own figures.
            REGIONS = {"north": ["English", "Welsh"], "south": ["French", "English"], "east": ["Dutch"]}

            languages = set()
            for region, spoken in REGIONS.items():
                languages.update(spoken)
                print(f"{region} region: {len(spoken)} listed")
            print(f"distinct languages across the regions: {len(languages)}")
        """,
    ),

    entry(
        databaseId = 43,
        kind = Kind.PROJECT,
        section = "Hydrostatics",
        title = "Weighing bodies in water to find their specific gravity",
        originalTitle = "Discorso intorno alle cose che stanno in su l'acqua",
        author = "Galileo Galilei",
        year = 1612,
        summary = "A program finds the specific gravity of a body from its weight in air and the weight it loses when weighed in water, following the principle of Archimedes.",
        details = "Galileo's discourse on floating bodies of 1612 took up why some bodies float and others sink, and it relied on the principle of Archimedes that a body loses as much weight as the water it displaces. The example uses three invented blocks, weighed in grams, and prints the specific gravity of each.",
        instructions = """
            1. Save the example as specific_gravity.py.
            2. Run it: python3 specific_gravity.py
        """,
        example = """
            # Invented blocks: name, weight in air, weight in water, in grams.
            BLOCKS = [("block A", 120, 90), ("block B", 50, 40), ("block C", 36, 30)]

            for name, in_air, in_water in BLOCKS:
                # The weight lost in water equals the weight of the water displaced, so the specific gravity is the weight in air over the loss.
                lost = in_air - in_water
                print(f"{name}: specific gravity {in_air / lost:.2f}")
        """,
    ),

    entry(
        databaseId = 43,
        kind = Kind.PROJECT,
        section = "Dictionaries",
        title = "Counting the Italian headwords of Florio's dictionary by first letter",
        originalTitle = "Queen Anna's New World of Words",
        author = "John Florio",
        year = 1611,
        summary = "A program counts how many headwords of an Italian word list begin with each letter, the kind of tally that an alphabetical dictionary makes easy to read.",
        details = "Florio's dictionary of 1611 gave the Italian words in alphabetical order with their English equivalents, so a reader could find a word quickly by its first letter. The example uses an invented list of five Italian words, not Florio's own entries, and prints the number of headwords under each letter.",
        instructions = """
            1. Save the example as florio_letters.py.
            2. Run it: python3 florio_letters.py
        """,
        example = """
            # Invented list of Italian headwords, not entries copied from Florio's dictionary.
            HEADWORDS = ["acqua", "casa", "cane", "pane", "padre"]

            counts = {}
            for word in HEADWORDS:
                # Each headword is counted under its first letter.
                counts[word[0]] = counts.get(word[0], 0) + 1

            for letter in sorted(counts):
                print(f"{letter}: {counts[letter]}")
            print(f"total headwords: {len(HEADWORDS)}")
        """,
    ),

)
