package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1720 to 1730: a marine surveyor's speed in knots, a harp's semitones, Bouguer's layers of air, a theodolite's angles, a spindle's twist and parallel forces. */
val db32Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 32,
        kind = Kind.EXAMPLE,
        section = "Harps",
        title = "Semitone ratios for a harp's pedals",
        originalTitle = "Single-action harp with five pedals",
        author = "Jacob Hochbrucker",
        year = 1720,
        summary = "A string shortened by k semitones sounds k semitones higher, so its length is two to the power minus k over twelve times the first length, and the program prints that ratio for one, two and five semitones.",
        details = "Hochbrucker's harp of about 1720 had pedals that each shortened the chosen strings by a semitone. The example uses the equal-tempered ratios that a modern piano uses, which is a modern convention and not a claim about the harp's own tuning, and it rounds each ratio to four places.",
        instructions = """
            1. Save the example as harp.py.
            2. Run it: python3 harp.py
        """,
        example = """
            # A string shortened by k semitones sounds k semitones higher; its length is 2 to the power -k/12 of the first length.
            for k in (1, 2, 5):
                print(f"{k} semitone{'s' if k > 1 else ''}: {2 ** (-k / 12):.4f}")
        """,
    ),

    entry(
        databaseId = 32,
        kind = Kind.EXAMPLE,
        section = "Optics",
        title = "Bouguer's layers of air that halve the light",
        originalTitle = "Essai d'optique sur la gradation de la lumiere",
        author = "Pierre Bouguer",
        year = 1729,
        summary = "Bouguer's rule says equal layers of air take equal fractions of the light, so the program prints the share left after each of five layers and counts the layers that halve it.",
        details = "Bouguer's Essai d'optique of 1729 argued that light passing through the air loses its strength in a geometric progression as the path thickens. The share kept per layer, four fifths here, is an illustrative value, and the program uses exact fractions to print the first five shares and count the layers until less than half the light is left.",
        instructions = """
            1. Save the example as bouguer.py.
            2. Run it: python3 bouguer.py
        """,
        example = """
            from fractions import Fraction as F

            # Bouguer's rule: each equal layer of air keeps the same share of the light.
            keep = F(4, 5)   # illustrative share kept by one layer
            share = F(1)
            for layer in range(1, 6):
                share *= keep
                print(f"after {layer} layer{'s' if layer > 1 else ''}: {share}")

            share, layers = F(1), 0
            while share > F(1, 2):
                share *= keep
                layers += 1
            print("layers until less than half the light is left:", layers)
        """,
    ),

    entry(
        databaseId = 32,
        kind = Kind.EXAMPLE,
        section = "Surveying",
        title = "Sisson's theodolite: angles of a five-sided traverse",
        originalTitle = "Theodolite",
        author = "Jonathan Sisson",
        year = 1720,
        summary = "A theodolite of 1720 reads angles in degrees, minutes and seconds, and the program adds five such readings to check that a closed five-sided figure gives 540 degrees.",
        details = "Sisson's theodolite is dated here to 1720. The example converts five illustrative readings to seconds, adds them exactly with integers, and prints the sum and the mean angle in degrees, minutes and seconds.",
        instructions = """
            1. Save the example as sisson.py.
            2. Run it: python3 sisson.py
        """,
        example = """
            # Interior angles of a closed five-sided traverse, read on a theodolite as (degrees, minutes, seconds).
            readings = [(108, 12, 30), (107, 44, 40), (109, 10, 0), (106, 30, 10), (108, 22, 40)]

            def to_seconds(deg, minutes, seconds):
                return deg * 3600 + minutes * 60 + seconds

            def as_dms(total):
                deg, rest = divmod(total, 3600)
                minutes, seconds = divmod(rest, 60)
                return f"{deg} degrees {minutes} minutes {seconds} seconds"

            total = sum(to_seconds(*r) for r in readings)
            print("sum of the five angles:", as_dms(total))
            print("a five-sided figure must give 540 degrees:", total == 540 * 3600)
            print("mean angle:", as_dms(total // 5))
        """,
    ),

    entry(
        databaseId = 32,
        kind = Kind.EXAMPLE,
        section = "Silk",
        title = "Lombe's spindle twist per metre",
        originalTitle = "Derby silk mill",
        author = "Thomas Lombe and John Lombe",
        year = 1721,
        summary = "The twist of a thread is the number of spindle turns for each metre delivered, which the program finds by dividing the spindle speed by the delivery speed for three examples.",
        details = "Throwing machines like those in Lombe's mill twisted silk by turning a spindle while the thread was drawn off at a set speed. The numbers in the example are illustrative, and each twist is an integer division of spindle turns by metres delivered.",
        instructions = """
            1. Save the example as lombe.py.
            2. Run it: python3 lombe.py
        """,
        example = """
            # Twist is the number of spindle turns for each metre of thread delivered: spindle speed divided by delivery speed.
            def turns_per_metre(spindle_turns_per_minute, metres_per_minute):
                return spindle_turns_per_minute // metres_per_minute

            for spindle, delivery in ((1000, 2), (1200, 3), (900, 4)):
                print(f"spindle {spindle} turns a minute, delivery {delivery} metres a minute: {turns_per_metre(spindle, delivery)} turns a metre")
        """,
    ),

    entry(
        databaseId = 32,
        kind = Kind.EXAMPLE,
        section = "Statics",
        title = "Varignon's moments of parallel forces",
        originalTitle = "Nouvelle mecanique ou statique",
        author = "Pierre Varignon",
        year = 1725,
        summary = "For parallel forces, the moment of their resultant about a point equals the sum of their moments, and the program checks this for two forces on a bar.",
        details = "Varignon's book of 1725 was published after his death and restates a moment method that goes back to his earlier papers. The example takes forces of 2 and 3 at positions 1 and 4 on a bar, finds the resultant and the point where it acts, and checks Varignon's rule with exact fractions.",
        instructions = """
            1. Save the example as varignon.py.
            2. Run it: python3 varignon.py
        """,
        example = """
            from fractions import Fraction as F

            # Parallel forces on a bar, each given as (size, position along the bar).
            forces = [(2, F(1)), (3, F(4))]
            total = sum(size for size, _ in forces)
            moment_sum = sum(size * pos for size, pos in forces)
            point = moment_sum / total
            print("resultant force:", total)
            print("sum of moments about 0:", moment_sum)
            print("point where the resultant acts:", point)
            print("moment of the resultant about 0:", total * point)
            print("Varignon's rule holds:", total * point == moment_sum)
        """,
    ),

    entry(
        databaseId = 32,
        kind = Kind.EXAMPLE,
        section = "Marine instruments",
        title = "Saumarez's speed in knots from timed runs",
        originalTitle = "Marine surveyor",
        author = "Henry de Saumarez",
        year = 1720,
        summary = "Saumarez's marine surveyor of 1720 gauged a vessel's speed, and the program turns timed runs in nautical miles into speeds in knots.",
        details = "Saumarez's marine surveyor is dated here to 1720, and it gauged a ship's speed. The example uses three illustrative runs, converts each to knots (nautical miles per hour), and prints the overall speed over all three runs as an exact fraction.",
        instructions = """
            1. Save the example as saumarez.py.
            2. Run it: python3 saumarez.py
        """,
        example = """
            from fractions import Fraction as F

            # Speed in knots is distance in nautical miles over time in hours.
            def knots(miles, minutes):
                return F(miles) / (F(minutes) / 60)

            runs = [(4, 30), (5, 20), (9, 45)]
            for miles, minutes in runs:
                print(f"{miles} nautical miles in {minutes} minutes: {knots(miles, minutes)} knots")
            total_miles = sum(m for m, _ in runs)
            total_minutes = sum(t for _, t in runs)
            print("overall speed over the three runs:", knots(total_miles, total_minutes), "knots")
        """,
    ),

)
