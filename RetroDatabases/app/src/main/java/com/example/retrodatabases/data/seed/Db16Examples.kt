package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1880 to 1890: a puzzle parity rule, line justification, a rotating field, a meter, a turbine stage and a thermal sum, each with a program that runs. */
val db16Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 16,
        kind = Kind.EXAMPLE,
        section = "Puzzles",
        title = "How the fifteen puzzle's parity decides solvability",
        originalTitle = "The Fifteen Puzzle",
        author = "Sam Loyd, with the parity proof of Johnson and Story",
        year = 1880,
        summary = "A board of sliding tiles can reach the goal only when the count of inversions plus the blank's row has the right parity, a test the program applies to two boards.",
        details = "The puzzle became a craze in 1880, and its origin is disputed, with Loyd credited for the version that spread. Johnson and Story published the parity rule in 1879, and this example applies it to a board with two tiles swapped and to a board one move from the goal. The program prints the inversion count and the verdict for each board.",
        instructions = """
            1. Save the example as puzzle.py.
            2. Run it: python3 puzzle.py
        """,
        example = """
            # Fifteen puzzle: tiles 1 to 15 and the blank (0) on a 4 by 4 board, read row by row from the top.
            def inversions(tiles):
                seq = [t for t in tiles if t != 0]
                return sum(1 for i in range(len(seq)) for j in range(i + 1, len(seq)) if seq[i] > seq[j])

            def solvable(tiles):
                # On a board of even width, solvable exactly when inversions plus the blank's row
                # counted from the bottom (bottom row = 1) is odd.
                blank_row_from_bottom = 4 - tiles.index(0) // 4
                return (inversions(tiles) + blank_row_from_bottom) % 2 == 1

            swapped = list(range(1, 14)) + [15, 14, 0]
            one_move = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 0, 13, 14, 15, 12]
            print("swap of 14 and 15 solvable:", solvable(swapped))
            print("one move from goal solvable:", solvable(one_move))
            print("inversions of the second board:", inversions(one_move))
        """,
    ),

    entry(
        databaseId = 16,
        kind = Kind.EXAMPLE,
        section = "Typesetting",
        title = "How a Linotype line is justified with spacebands",
        originalTitle = "Linotype",
        author = "Ottmar Mergenthaler",
        year = 1886,
        summary = "The line is filled out by spreading the spare room equally over the spaces between words, so every line ends flush at the same measure.",
        details = "The Linotype cast a whole line of type at once, and justification came from wedge-shaped spacebands that opened the spaces to the measure. The first newspaper use was in 1886, according to the usual account. The example spreads the spare room over three gaps with exact fractions and prints where each word starts.",
        instructions = """
            1. Save the example as justify.py.
            2. Run it: python3 justify.py
        """,
        example = """
            from fractions import Fraction

            measure = 30                  # line length in units
            words = [5, 7, 4, 6]          # word widths in units
            minimum_gap = 1               # natural space between words
            gaps = len(words) - 1
            extra = measure - sum(words) - minimum_gap * gaps   # room left for the spacebands
            gap = minimum_gap + Fraction(extra, gaps)           # the spacebands spread the room equally
            print("gap per space:", gap)
            position = Fraction(0)
            starts = []
            for i, w in enumerate(words):
                starts.append(position)
                position += w
                if i < gaps:
                    position += gap
            print("word starts:", " ".join(str(s) for s in starts))
            print("line ends at:", position)
        """,
    ),

    entry(
        databaseId = 16,
        kind = Kind.EXAMPLE,
        section = "Rotating fields",
        title = "How two perpendicular currents make a rotating field",
        originalTitle = "Rotating electrodynamic field",
        author = "Galileo Ferraris",
        year = 1888,
        summary = "Two windings at right angles, fed a quarter cycle apart, make a field whose strength stays fixed while its direction turns.",
        details = "Ferraris showed a rotating field from two alternating currents in 1885 and published the theory in 1888, the same year as Tesla's motor patent. The example uses rational points on the unit circle for the two currents at successive instants. The program prints that the squared strength of the field is 1 at every step.",
        instructions = """
            1. Save the example as ferraris.py.
            2. Run it: python3 ferraris.py
        """,
        example = """
            from fractions import Fraction

            # Two windings at right angles carry currents a and b, a quarter cycle apart.
            # Rational points on the unit circle stand for (cos, sin) at successive instants.
            steps = [(Fraction(1), Fraction(0)), (Fraction(3, 5), Fraction(4, 5)),
                     (Fraction(5, 13), Fraction(12, 13)), (Fraction(8, 17), Fraction(15, 17)),
                     (Fraction(0), Fraction(1))]
            for k, (a, b) in enumerate(steps):
                # The resultant of two equal, perpendicular fields has a fixed size, so its square is the same at every instant.
                print(f"instant {k}: a = {a}, b = {b}, field squared = {a * a + b * b}")
        """,
    ),

    entry(
        databaseId = 16,
        kind = Kind.EXAMPLE,
        section = "Metering",
        title = "How an electrolytic meter reads charge",
        originalTitle = "Electrolytic meter",
        author = "Thomas Alva Edison",
        year = 1881,
        summary = "A meter weighs the metal deposited by a current, and Faraday's law turns that mass into the charge that passed through the circuit.",
        details = "Edison's electrolytic meter for the central-station customers measured the current by the zinc deposited on a plate. The record gives the year of the meter's development, which sources place around 1881 and 1882. The example applies Faraday's law to one and two hours at one ampere of zinc.",
        instructions = """
            1. Save the example as meter.py.
            2. Run it: python3 meter.py
        """,
        example = """
            # Faraday's law for a zinc meter: mass deposited = current x time x molar mass / (electrons x F).
            F = 96485.0       # coulombs per mole of electrons
            M = 65.38         # molar mass of zinc, grams per mole
            n = 2             # electrons per zinc ion
            current = 1.0    # amperes
            for hours in (1, 2):
                t = 3600 * hours
                mass = current * t * M / (n * F)
                print(f"{hours} h at 1 A: {mass:.4f} g of zinc")
        """,
    ),

    entry(
        databaseId = 16,
        kind = Kind.EXAMPLE,
        section = "Steam turbines",
        title = "How steam stages share the pressure drop",
        originalTitle = "Compound steam turbine",
        author = "Charles Algernon Parsons",
        year = 1884,
        summary = "A turbine that lets the steam fall through several stages, each taking an equal share of the pressure ratio, so no single set of blades has to absorb the whole drop.",
        details = "Parsons patented the compound steam turbine in 1884, dividing the fall in pressure among many rows of blades. The example takes an overall pressure ratio of 16 over four stages and finds the ratio that each stage must give. The program prints the pressure after each stage.",
        instructions = """
            1. Save the example as stages.py.
            2. Run it: python3 stages.py
        """,
        example = """
            # Steam falls through several stages; each stage gives the same pressure ratio.
            total_ratio = 16     # inlet pressure divided by exhaust pressure
            stages = 4
            stage_ratio = next(r for r in range(2, 20) if r ** stages == total_ratio)
            print(f"stage ratio: {stage_ratio}")
            pressure = total_ratio
            for k in range(1, stages + 1):
                pressure //= stage_ratio
                print(f"after stage {k}: {pressure} units")
        """,
    ),

    entry(
        databaseId = 16,
        kind = Kind.EXAMPLE,
        section = "Thermal detection",
        title = "How series couples add a thermal signal",
        originalTitle = "Radiomicrometer",
        author = "Charles Vernon Boys",
        year = 1888,
        summary = "A detector of several thermocouple couples wired in series adds the voltage of every couple, so a small heat difference gives a usable signal.",
        details = "Boys's radiomicrometer of 1888 was a very sensitive detector of small heat differences, built around a thermocouple. The example shows only how the couples add in series, with illustrative numbers and not measured ones. The rest of the instrument, a galvanometer reading the sum, is outside this example.",
        instructions = """
            1. Save the example as couples.py.
            2. Run it: python3 couples.py
        """,
        example = """
            # Thermocouple couples in series: each couple gives a small voltage for each degree of
            # temperature difference, and the voltages add. The numbers are illustrative, not measured.
            couples = 4
            volts_per_degree = 50   # microvolts per degree
            for difference in (0.2, 0.5, 1.0):
                total = couples * volts_per_degree * difference
                print(f"difference {difference} K: total {total:.1f} microvolts")
        """,
    ),
)
