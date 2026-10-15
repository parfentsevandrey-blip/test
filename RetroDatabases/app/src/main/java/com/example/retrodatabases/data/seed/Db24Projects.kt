package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1800 to 1810: a type case, a survey triangle, a parish tally and a monitors' rota, each a small program. */
val db24Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 24,
        kind = Kind.PROJECT,
        section = "Printing",
        title = "Counting the sorts a printer needs for one line",
        originalTitle = "Practice of the type case in hand printing (c. 1800)",
        author = "Printing practice of the period",
        year = 1800,
        summary = "A printer checks a line of type against the sorts in the case, counting how many of each letter the line needs and how many are short.",
        details = "A type case held a fixed stock of each letter, and a compositor who ran short had to borrow or wait. The example counts the letters of one invented line against a small case, and reports the shortage of each sort.",
        instructions = """
            1. Save the example as type_case.py.
            2. Run it: python3 type_case.py
        """,
        example = """
            line = "THE PRINTER SETS THE LINE"
            case = {"E": 6, "T": 4, "H": 2, "R": 2, "S": 2, "N": 1, "P": 1, "I": 1, "L": 1}
            needed = {}
            for ch in line:
                if ch != " ":
                    needed[ch] = needed.get(ch, 0) + 1
            print("letters in the line:", sum(needed.values()))
            for ch in sorted(needed):
                short = max(0, needed[ch] - case.get(ch, 0))
                print(f"{ch}: needs {needed[ch]}, has {case.get(ch, 0)}, short {short}")
            print("short sorts in total:", sum(max(0, needed[ch] - case.get(ch, 0)) for ch in needed))
        """,
    ),

    entry(
        databaseId = 24,
        kind = Kind.PROJECT,
        section = "Triangulation",
        title = "Closing error of a survey triangle, spread equally",
        originalTitle = "Plan of triangulation for the survey of the coasts (1807)",
        author = "Ferdinand Rudolph Hassler",
        year = 1807,
        summary = "A survey triangle's measured angles should sum to 180 degrees, and the program spreads the closing error equally over the three angles.",
        details = "The triangulation planned for the Coast Survey in 1807 depended on triangles like this one, whose angles were measured and then checked against their sum. The example takes one measured triangle with an error of 0.03 degrees, spreads the correction equally, and prints the adjusted angles.",
        instructions = """
            1. Save the example as triangle_closing.py.
            2. Run it: python3 triangle_closing.py
        """,
        example = """
            from fractions import Fraction

            # Measured angles of one triangle of a survey net, in degrees; in a plane triangle they must sum to 180.
            measured = [Fraction("60.01"), Fraction("59.97"), Fraction("60.05")]
            total = sum(measured)
            error = total - 180
            correction = -error / 3
            adjusted = [a + correction for a in measured]
            print(f"measured sum: {float(total):.2f}")
            print(f"closing error: {float(error):.2f}")
            print(f"correction each: {float(correction):.2f}")
            print("adjusted:", " ".join(f"{float(a):.2f}" for a in adjusted))
            print(f"adjusted sum: {float(sum(adjusted)):.2f}")
        """,
    ),

    entry(
        databaseId = 24,
        kind = Kind.PROJECT,
        section = "Population counts",
        title = "Tabulating a parish list by age band",
        originalTitle = "Parish list tallied by age, made-up sample (1801)",
        author = "Parish enumerators of the period (made-up sample list)",
        year = 1801,
        summary = "The program tallies a parish list into three age bands and prints the totals and the average age.",
        details = "Counting people from a list into bands is the basic work of a census tally. The example uses a made-up list of eight residents, not real data, to show how each person falls into a band and how the totals come out.",
        instructions = """
            1. Save the example as parish_tally.py.
            2. Run it: python3 parish_tally.py
        """,
        example = """
            # A made-up list of residents in one parish, with ages; the names and ages are invented for the example.
            residents = [("Ann", 3), ("Tom", 34), ("Mary", 71), ("John", 12), ("Eliza", 46), ("Peter", 59), ("Sarah", 8), ("Jane", 60)]

            def band(age):
                if age < 15:
                    return "under 15"
                if age < 60:
                    return "15 to 59"
                return "60 and over"

            counts = {}
            for name, age in residents:
                counts[band(age)] = counts.get(band(age), 0) + 1
            for label in ["under 15", "15 to 59", "60 and over"]:
                print(f"{label}: {counts.get(label, 0)}")
            print("total residents:", len(residents))
            print("average age:", sum(age for _, age in residents) / len(residents))
        """,
    ),

    entry(
        databaseId = 24,
        kind = Kind.PROJECT,
        section = "Monitorial teaching",
        title = "A monitor's rota in a Lancaster school class",
        originalTitle = "Monitorial method, rota of monitors in Improvements in Education (1803)",
        author = "Joseph Lancaster",
        year = 1803,
        summary = "Under the monitorial method the older pupils rotate through the groups, and the program prints a rota in which each monitor covers every group in four days.",
        details = "Lancaster's monitors were pupils who taught small groups, and rotating them through the groups kept every group taught. The example uses three monitors and four groups with invented names, prints six days of duties, and checks that each monitor covers all four groups in four days.",
        instructions = """
            1. Save the example as monitor_rota.py.
            2. Run it: python3 monitor_rota.py
        """,
        example = """
            # Monitors rotate through the groups, so each monitor takes a different group every day.
            monitors = ["Ann", "Ben", "Cal"]
            groups = ["A", "B", "C", "D"]

            def group_for(m, day):
                return groups[(m + day) % len(groups)]

            for day in range(6):
                duties = [f"{name} {group_for(m, day)}" for m, name in enumerate(monitors)]
                print(f"day {day + 1}: " + ", ".join(duties))
            full_cycle = all(len({group_for(m, d) for d in range(4)}) == 4 for m in range(len(monitors)))
            print("every monitor covers all groups in four days:", full_cycle)
        """,
    ),
)
