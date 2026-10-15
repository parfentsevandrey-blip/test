package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1810 to 1820: a table generator, a signal book, a sign reader and a pulse count, each a small program. */
val db23Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 23,
        kind = Kind.PROJECT,
        section = "Tables",
        title = "A table generator for squares, cubes and reciprocals",
        originalTitle = "Tables of Squares, Cubes, Square Roots, Cube Roots, and Reciprocals (1814)",
        author = "Peter Barlow",
        year = 1814,
        summary = "A short program prints a table with columns for the square, cube and reciprocal of each whole number, of the kind Barlow printed.",
        details = "Barlow's tables gave columns of this kind for many numbers, and the project shows how such a table is produced by a loop. The example prints the first six rows, with reciprocals to four decimal places.",
        instructions = """
            1. Save the example as table_generator.py.
            2. Run it: python3 table_generator.py
        """,
        example = """
            print("n  square  cube  1/n")
            for n in range(1, 7):
                print(f"{n}  {n * n}  {n ** 3}  {1 / n:.4f}")
        """,
    ),

    entry(
        databaseId = 23,
        kind = Kind.PROJECT,
        section = "Signal codes",
        title = "A signal-book lookup in the style of Marryat's code",
        originalTitle = "Code of signals for merchant shipping (1817)",
        author = "Frederick Marryat",
        year = 1817,
        summary = "A signal book maps a three-digit group to a phrase, and the program looks up groups as a ship's officer would.",
        details = "A code of this kind lets a short group of digits stand for a whole sentence. The phrases in the example are invented for the demonstration and are not taken from Marryat's book, and the program prints each lookup and any group missing from the book.",
        instructions = """
            1. Save the example as signal_book.py.
            2. Run it: python3 signal_book.py
        """,
        example = """
            signal_book = {
                "001": "all well",
                "002": "send the boat",
                "003": "change course to the north",
                "004": "request a pilot",
            }
            for code in ["003", "001", "004", "009"]:
                print(code, "->", signal_book.get(code, "not in the book"))
        """,
    ),

    entry(
        databaseId = 23,
        kind = Kind.PROJECT,
        section = "Decipherment",
        title = "A substitution reader for demotic sign values",
        originalTitle = "Egypt, article in the Supplement to the Encyclopaedia Britannica (1819)",
        author = "Thomas Young",
        year = 1819,
        summary = "A reader turns a list of sign codes into sounds with a table of values, and counts the signs it cannot read.",
        details = "Young's method was to fix a sound value for each sign and read a text through the table, leaving a gap wherever a sign had no value. The sign values in this example are invented for the demonstration, and the program prints the reading and the number of unknown signs.",
        instructions = """
            1. Save the example as sign_reader.py.
            2. Run it: python3 sign_reader.py
        """,
        example = """
            values = {"s1": "p", "s2": "t", "s3": "k", "s4": "a"}
            message = ["s1", "s4", "s2", "s4", "s3", "s9"]
            reading = "".join(values.get(sign, "?") for sign in message)
            print("reading:", reading)
            print("unknown signs:", sum(1 for sign in message if sign not in values))
        """,
    ),

    entry(
        databaseId = 23,
        kind = Kind.PROJECT,
        section = "Telegraphs",
        title = "A pulse count for a word on a dial telegraph",
        originalTitle = "Descriptions of an Electrical Telegraph (1823)",
        author = "Francis Ronalds",
        year = 1816,
        summary = "A word is sent letter by letter, each letter as a number of pulses equal to its place in the alphabet, and the program totals the pulses.",
        details = "Ronalds's line was an electrical telegraph, and this example uses a simpler counting code to show how a word becomes a run of signals. The code is invented for the demonstration and is not Ronalds's own, and the program prints the pulses for each letter and the total for the word.",
        instructions = """
            1. Save the example as pulse_count.py.
            2. Run it: python3 pulse_count.py
        """,
        example = """
            word = "CAB"
            for ch in word:
                print(ch, ord(ch) - ord("A") + 1)
            print("total pulses:", sum(ord(ch) - ord("A") + 1 for ch in word))
        """,
    ),
)
