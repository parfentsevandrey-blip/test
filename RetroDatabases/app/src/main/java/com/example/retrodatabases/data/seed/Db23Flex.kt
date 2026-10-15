package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1810 to 1820: whole-number ratios of combining masses, a verb table and metronome intervals. */
val db23Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 23,
        kind = Kind.FLEX,
        section = "Combining weights",
        title = "Whole-number ratios of combining masses",
        originalTitle = "Essay on chemical proportions, five parts in the Annals of Philosophy (1813 to 1814)",
        author = "Jons Jacob Berzelius",
        year = 1813,
        summary = "Masses of one element that combine with a fixed mass of another are compared as whole-number ratios, which the program reduces to lowest terms.",
        details = "Berzelius's essay concerns the proportions in which elements combine. The example uses invented oxygen masses for three compounds of one metal, expresses each as a multiple of the smallest, and prints the results in lowest terms.",
        instructions = """
            1. Save the example as combining_masses.py.
            2. Run it: python3 combining_masses.py
        """,
        example = """
            from fractions import Fraction

            oxygen = {"oxide A": 8, "oxide B": 16, "oxide C": 24}
            base = min(oxygen.values())
            for name, mass in oxygen.items():
                print(f"{name}: {Fraction(mass, base)} times the smallest oxygen mass")
        """,
        rare = true,
    ),

    entry(
        databaseId = 23,
        kind = Kind.FLEX,
        section = "Comparative grammar",
        title = "Endings of a Latin present tense as a table",
        originalTitle = "Uber das Conjugationssystem der Sanskritsprache in Vergleichung mit jenem der griechischen, lateinischen, persischen und germanischen Sprache (1816)",
        author = "Franz Bopp",
        year = 1816,
        summary = "A stem and a list of person endings give the present tense of a verb, the kind of table Bopp set side by side across languages.",
        details = "Bopp's comparison matched the endings of verb forms across languages. This example builds the present tense of the Latin verb amare from the stem am and six endings. It is a demonstration of the table, not a general conjugation engine, and it prints each person with its form.",
        instructions = """
            1. Save the example as latin_present.py.
            2. Run it: python3 latin_present.py
        """,
        example = """
            stem = "am"
            persons = ["1 singular", "2 singular", "3 singular", "1 plural", "2 plural", "3 plural"]
            endings = ["o", "as", "at", "amus", "atis", "ant"]
            for person, ending in zip(persons, endings):
                print(f"{person}: {stem + ending}")
        """,
    ),

    entry(
        databaseId = 23,
        kind = Kind.FLEX,
        section = "Music",
        title = "Beat intervals of a pendulum metronome",
        originalTitle = "Metronome patent (1815)",
        author = "Johann Nepomuk Maelzel",
        year = 1815,
        summary = "A tempo in beats per minute gives an exact interval between beats as a fraction of a second, which the program prints in lowest terms.",
        details = "Maelzel's metronome let composers state a tempo as a number of beats per minute. The example converts three tempos into the exact interval between beats, using fractions so that no rounding appears in the output.",
        instructions = """
            1. Save the example as beat_intervals.py.
            2. Run it: python3 beat_intervals.py
        """,
        example = """
            from fractions import Fraction

            for bpm in (60, 120, 90):
                print(f"{bpm} beats per minute: beat interval {Fraction(60, bpm)} second(s)")
        """,
    ),
)
