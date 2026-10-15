package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1610 to 1620: three dice, Kepler's consonances in cents and Galileo's anagram on Saturn. */
val db43Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 43,
        kind = Kind.FLEX,
        section = "Probability",
        title = "Three dice: why a total of 10 beats a total of 9",
        originalTitle = "Sopra le scoperte dei dadi",
        author = "Galileo Galilei",
        year = 1620,
        summary = "Counting the ordered outcomes of three dice shows that a total of 10 can be made in 27 ways and a total of 9 in 25, so 10 is the likelier total.",
        details = "Galileo's treatise on discoveries about dice was printed only in 1718, and its writing is dated variously from about 1613 to about 1623, so the date is disputed. The example counts the 216 ordered throws of three dice, which give 25 throws totalling 9 and 27 totalling 10, the difference that gamblers had long noticed.",
        instructions = """
            1. Save the example as dice_totals.py.
            2. Run it: python3 dice_totals.py
        """,
        example = """
            from itertools import product

            # Every ordered throw of three dice, each face from 1 to 6, is one of 216 outcomes.
            for total in (9, 10):
                count = sum(1 for throw in product(range(1, 7), repeat=3) if sum(throw) == total)
                print(f"throws totalling {total}: {count}")
        """,
    ),

    entry(
        databaseId = 43,
        kind = Kind.FLEX,
        section = "Harmony",
        title = "Kepler's consonances as ratios, in cents",
        originalTitle = "Harmonices mundi",
        author = "Johannes Kepler",
        year = 1619,
        summary = "Kepler set out consonances as ratios of whole numbers, and a ratio's size in cents is 1200 times the base-two logarithm of the ratio, which the example computes for a fifth and a fourth.",
        details = "Kepler's Harmonices mundi of 1619 treated the consonances of music as ratios of whole numbers, such as 3 to 2 for the fifth and 4 to 3 for the fourth. The example gives both sizes in cents, rounded to whole cents, from the base-two logarithm of each ratio.",
        instructions = """
            1. Save the example as consonance.py.
            2. Run it: python3 consonance.py
        """,
        example = """
            from math import log2

            # A ratio of two frequencies gives an interval whose size in cents is 1200 times the base-two logarithm of the ratio.
            print("fifth, 3 to 2:", round(1200 * log2(3 / 2)), "cents")
            print("fourth, 4 to 3:", round(1200 * log2(4 / 3)), "cents")
        """,
    ),

    entry(
        databaseId = 43,
        kind = Kind.FLEX,
        section = "Anagrams",
        title = "Galileo's anagram on Saturn, checked by sorting letters",
        originalTitle = "smaismrmilmepoetaleumibunenugttauiras",
        author = "Galileo Galilei",
        year = 1610,
        summary = "Galileo sent Kepler an anagram in 1610 to claim his observation of Saturn, and a one-line check shows that its letters are those of the Latin sentence he meant.",
        details = "The anagram was printed with u for both u and v, as Latin of the period often was, so the check reads v as u. The sentence it encodes is Galileo's own claim that he had observed the highest planet to be three-formed, and the example prints the letter count and whether the letters match.",
        instructions = """
            1. Save the example as saturn_anagram.py.
            2. Run it: python3 saturn_anagram.py
        """,
        example = """
            from collections import Counter

            ANAGRAM = "smaismrmilmepoetaleumibunenugttauiras"
            # The intended sentence with its spaces removed; in the old orthography v was printed as u.
            SENTENCE = "altissimumplanetamtergeminumobservavi".replace("v", "u")

            print(f"letters in the anagram: {len(ANAGRAM)}")
            print("letters match the sentence:", Counter(ANAGRAM) == Counter(SENTENCE))
        """,
    ),

)
