package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1730 to 1740: a mirror's double reading, a temperature scale and a roller draft. */
val db31Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 31,
        kind = Kind.FLEX,
        section = "Navigation",
        title = "Hadley's double reflection reading",
        originalTitle = "Hadley's reflecting quadrant",
        author = "John Hadley",
        year = 1731,
        summary = "When the index arm of a reflecting quadrant turns, the reflected image moves twice as far, so the program doubles each turn to get the reading.",
        details = "Hadley's instrument of 1731 reads the angle between two sights, and its index mirror turns through half that angle. The example prints the reading for three turns of the index arm, each reading twice the turn.",
        instructions = """
            1. Save the example as hadley.py.
            2. Run it: python3 hadley.py
        """,
        example = """
            # Hadley's quadrant: turning the index mirror by an angle turns the reflected image by twice that angle.
            for turn in (15, 30, 22):
                print(f"index arm turned {turn} degrees: reads {2 * turn} degrees")
        """,
    ),

    entry(
        databaseId = 31,
        kind = Kind.FLEX,
        section = "Measurement",
        title = "Reaumur's scale in Celsius and Fahrenheit",
        originalTitle = "Regles pour construire les thermometres",
        author = "Rene-Antoine Ferchault de Reaumur",
        year = 1730,
        summary = "Reaumur's scale runs from 0 at freezing to 80 at boiling, so each degree Reaumur is five quarters of a Celsius degree, which the program converts exactly.",
        details = "Reaumur set out his scale in 1730, and it was used for spirit thermometers in France. The example converts three readings to Celsius and Fahrenheit with exact fractions, so that 80 Reaumur is 100 Celsius and 212 Fahrenheit.",
        instructions = """
            1. Save the example as reaumur.py.
            2. Run it: python3 reaumur.py
        """,
        example = """
            from fractions import Fraction as F

            for r in (0, 16, 80):
                c = F(5, 4) * r          # Reaumur to Celsius: 80 degrees Reaumur is 100 Celsius
                f = F(9, 4) * r + 32     # Reaumur to Fahrenheit
                print(f"{r} R = {c} C = {f} F")
        """,
    ),

    entry(
        databaseId = 31,
        kind = Kind.FLEX,
        section = "Textiles",
        title = "Lewis Paul's roller draft",
        originalTitle = "Machine for spinning by rollers",
        author = "Lewis Paul and John Wyatt",
        year = 1738,
        summary = "Two pairs of rollers turning at different speeds stretch a sliver by the ratio of their speeds, which the program computes as an exact draft.",
        details = "Paul and Wyatt's patent of 1738 used pairs of rollers to draw out the fibre, and the stretch between the pairs is set by their surface speeds. The example takes a draft of 9 to 2 and applies it to two slivers.",
        instructions = """
            1. Save the example as draft.py.
            2. Run it: python3 draft.py
        """,
        example = """
            from fractions import Fraction as F

            feed_speed = 2           # speed of the feed rollers, in the example's units
            delivery_speed = 9       # speed of the delivery rollers, in the same units
            draft = F(delivery_speed, feed_speed)
            for length in (40, 60):
                print(f"sliver of {length} units becomes {length * draft} units")
            print("draft: " + str(draft))
        """,
    ),
)
