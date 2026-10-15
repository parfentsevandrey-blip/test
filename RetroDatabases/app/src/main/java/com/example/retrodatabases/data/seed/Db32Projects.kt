package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1720 to 1730: a cross-reference index, a line of type justified, the points of an analemma and the emptying of a vessel. */
val db32Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 32,
        kind = Kind.PROJECT,
        section = "Reference",
        title = "Chambers's cross-reference index",
        originalTitle = "Cyclopaedia, or an Universal Dictionary of Arts and Sciences",
        author = "Ephraim Chambers",
        year = 1728,
        summary = "A short program reads three sample articles, finds the see references in them, and builds a reverse index that lists which articles point to each target.",
        details = "Chambers's Cyclopaedia linked articles with references to other entries, and a reader could look up the references in reverse. The example is a small project of about a dozen lines that parses three sample articles written for the example, collects the references and prints the index with a count.",
        instructions = """
            1. Save the example as index.py.
            2. Run it: python3 index.py
        """,
        example = """
            import re

            # Three sample articles written for this example; "See" introduces the cross-references.
            articles = {
                "Barometer": "An instrument for the weight of the air. See Pneumatics.",
                "Pneumatics": "The study of air and its weight. See Barometer and Pump.",
                "Pump": "A machine that raises water or air. See Pneumatics.",
            }
            index = {}
            for title, text in articles.items():
                if "See" in text:
                    for target in re.findall(r"[A-Z][a-z]+", text.split("See", 1)[1]):
                        index.setdefault(target, []).append(title)
            for target in sorted(index):
                print(f"{target}: referred to from {', '.join(sorted(index[target]))}")
            print("cross-references found:", sum(len(v) for v in index.values()))
        """,
    ),

    entry(
        databaseId = 32,
        kind = Kind.PROJECT,
        section = "Printing",
        title = "Caslon's line justification",
        originalTitle = "Caslon's types",
        author = "William Caslon",
        year = 1720,
        summary = "A line of words is spread to a fixed measure by sharing the spare width among the word spaces, and the program prints the widths for one line.",
        details = "Justifying a line means adding space between words until the line fills its measure, which the compositor did by hand at the case. The widths in the example are illustrative units for a sample fount, not measurements of Caslon's own types, and the program prints the natural width, the spare width and the new width of each space.",
        instructions = """
            1. Save the example as justify.py.
            2. Run it: python3 justify.py
        """,
        example = """
            # Advance widths in arbitrary units for a sample fount; they are illustrative and not measured from any real type.
            widths = {"a": 4, "b": 5, "c": 3, "d": 5, "e": 4, " ": 2}
            words = ["bad", "bed", "cab"]
            line_width = 50
            natural = sum(widths[ch] for ch in " ".join(words))
            gaps = len(words) - 1
            extra = line_width - natural
            per_gap = extra // gaps
            print("natural width:", natural)
            print("extra space to share:", extra)
            print("width of each gap after justifying:", widths[" "] + per_gap)
            print("line width after justifying:", natural + gaps * per_gap)
        """,
    ),

    entry(
        databaseId = 32,
        kind = Kind.PROJECT,
        section = "Astronomy",
        title = "Grandjean de Fouchy's analemma points",
        originalTitle = "Analemma",
        author = "Grandjean de Fouchy",
        year = 1730,
        summary = "The analemma plots the sun's declination against the equation of time over a year, and the program evaluates both at four quarter-turn angles.",
        details = "The analemma of 1730 is credited to Grandjean de Fouchy, and it records the sun's position at one clock time through the year. The equation of time uses the common approximation 9.87 sin 2B minus 7.53 cos B minus 1.5 sin B minutes, and the declination uses an obliquity of 23.44 degrees, both evaluated at four illustrative angles B.",
        instructions = """
            1. Save the example as analemma.py.
            2. Run it: python3 analemma.py
        """,
        example = """
            import math

            # Equation of time in minutes, with B an angle that runs once round the year (a common approximation).
            def equation_of_time(b_deg):
                b = math.radians(b_deg)
                return 9.87 * math.sin(2 * b) - 7.53 * math.cos(b) - 1.5 * math.sin(b)

            # Declination of the sun in degrees, with an obliquity of 23.44 degrees.
            def declination(b_deg):
                return 23.44 * math.sin(math.radians(b_deg))

            for b in (0, 90, 180, 270):
                print(f"B = {b}: equation of time {equation_of_time(b):.2f} min, declination {declination(b):.2f} deg")
        """,
    ),

    entry(
        databaseId = 32,
        kind = Kind.PROJECT,
        section = "Hydraulics",
        title = "Switzer's time to empty a vessel through a hole",
        originalTitle = "An Introduction to a General System of Hydrostaticks and Hydraulicks",
        author = "Stephen Switzer",
        year = 1729,
        summary = "A vessel empties through a small hole in a time that depends on the ratio of the areas and on the square root of the head, and the program prints the time for three cases.",
        details = "Switzer's two-volume book of 1729 is an early English treatment of hydrostatics and hydraulics, and the question here, how long a vessel takes to empty through a small hole, is the kind of problem such treatises set. The example uses the outflow rule, time equal to the area ratio times the square root of twice the head over gravity, with illustrative values of 10 for gravity and round area ratios, and prints the three times in seconds.",
        instructions = """
            1. Save the example as switzer.py.
            2. Run it: python3 switzer.py
        """,
        example = """
            import math

            G = 10    # gravity in illustrative units

            def emptying_time(area_ratio, head):
                # Time for a vessel to empty through a small hole: area ratio times the square root of 2 head over g.
                return area_ratio * math.sqrt(2 * head / G)

            for head in (5, 20):
                print(f"head {head}, area ratio 100: {emptying_time(100, head):.0f} s")
            print(f"head 5, hole half the area (ratio 200): {emptying_time(200, 5):.0f} s")
        """,
    ),

)
