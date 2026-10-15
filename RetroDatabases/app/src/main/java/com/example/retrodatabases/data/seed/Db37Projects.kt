package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1670 to 1680: a sorted star list, a headword index, a salt mixture and a word-length count. */
val db37Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 37,
        kind = Kind.PROJECT,
        section = "Astronomy",
        title = "Halley's star list sorted by magnitude",
        originalTitle = "Catalogus stellarum australium",
        author = "Edmond Halley",
        year = 1679,
        summary = "A star list is sorted by magnitude, with the brightest stars, which have the smallest magnitude numbers, placed first.",
        details = "Halley's southern catalogue of 1679 gave stars with their magnitudes, and sorting by magnitude is the first step in working with any such list. The example uses five stars labelled A to E with invented magnitudes for the exercise, and sorts them by magnitude and then by label.",
        instructions = """
            1. Save the example as halley_star_list.py.
            2. Run it: python3 halley_star_list.py
        """,
        example = """
            # Illustrative list: the labels are made up and the magnitudes are invented for the exercise, not real stars.
            STARS = [("star A", 3), ("star B", 1), ("star C", 4), ("star D", 2), ("star E", 1)]

            for name, magnitude in sorted(STARS, key=lambda item: (item[1], item[0])):
                print(f"{magnitude} {name}")
        """,
    ),

    entry(
        databaseId = 37,
        kind = Kind.PROJECT,
        section = "Dictionaries",
        title = "Richelet's headword index by first letter",
        originalTitle = "Dictionnaire francois",
        author = "Pierre Richelet",
        year = 1680,
        summary = "A dictionary's headwords are grouped by first letter, and the program prints each letter with its count and its words.",
        details = "Richelet's dictionary of 1680 arranged its entries in alphabetical order, and grouping by first letter is how a reader finds a word. The example groups ten French headwords chosen for the exercise and prints the letters in order with their counts.",
        instructions = """
            1. Save the example as richelet_index.py.
            2. Run it: python3 richelet_index.py
        """,
        example = """
            WORDS = ["arc", "astre", "air", "ballon", "bassin", "cadran", "cercle", "lune", "lentille", "ligne"]

            index = {}
            for word in WORDS:
                index.setdefault(word[0], []).append(word)

            for letter in sorted(index):
                print(letter, len(index[letter]), " ".join(index[letter]))
        """,
    ),

    entry(
        databaseId = 37,
        kind = Kind.PROJECT,
        section = "Chemistry",
        title = "Lemery's salt mixtures by weight",
        originalTitle = "Cours de chymie",
        author = "Nicolas Lemery",
        year = 1675,
        summary = "Two salts mixed in given parts by weight each take their share of the total mass, so the two masses follow from the ratio.",
        details = "Lemery's course of 1675 gave preparations and mixtures in weights, and the example uses illustrative parts and a total of 80 grams, not Lemery's own figures. It prints the two masses for three mixing ratios, to one decimal place.",
        instructions = """
            1. Save the example as lemery_mixtures.py.
            2. Run it: python3 lemery_mixtures.py
        """,
        example = """
            def mix_masses(total_grams, parts_a, parts_b):
                # The two salts share the total mass in the given parts by weight, so each mass is its part of the total.
                share = total_grams / (parts_a + parts_b)
                return share * parts_a, share * parts_b

            for parts in ((1, 1), (2, 3), (3, 5)):
                a, b = mix_masses(80, *parts)
                print(f"parts {parts[0]} to {parts[1]}: {a:.1f} g and {b:.1f} g")
        """,
    ),

    entry(
        databaseId = 37,
        kind = Kind.PROJECT,
        section = "Glossaries",
        title = "Word lengths in Ray's list",
        originalTitle = "A Collection of English Words not generally used",
        author = "John Ray",
        year = 1674,
        summary = "A word list is counted by length, which is a simple first step in describing a glossary such as Ray's.",
        details = "Ray's collection of 1674 listed English words that were falling out of use, and counting words by length is a simple way to describe such a list. The example uses an illustrative set of eight words, not Ray's own, and prints how many words have each length.",
        instructions = """
            1. Save the example as ray_lengths.py.
            2. Run it: python3 ray_lengths.py
        """,
        example = """
            from collections import Counter

            # Illustrative words for the exercise; they are not taken from Ray's collection.
            WORDS = ["yeld", "thrave", "fell", "wold", "lea", "garth", "dell", "ken"]

            lengths = Counter(len(word) for word in WORDS)
            for length in sorted(lengths):
                print(f"length {length}: {lengths[length]} word(s)")
            print("total words:", len(WORDS))
        """,
        rare = true,
    ),

)
