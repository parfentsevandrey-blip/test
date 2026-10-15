package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1770 to 1780: silver values of Continental paper dollars, the arch of the Iron Bridge and the water a hydraulic ram delivers. */
val db27Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 27,
        kind = Kind.FLEX,
        section = "Paper money",
        title = "Silver value of Continental paper dollars",
        originalTitle = "Continental dollar",
        author = "Continental Congress",
        year = 1775,
        summary = "Paper dollars are worth their silver equivalent at a given rate, which the program works out for two sums at an illustrative rate of forty to one.",
        details = "Congress first issued paper money in 1775, and its value fell sharply as more was printed. The example uses an illustrative rate of forty paper dollars to one silver dollar, which is not a quotation for any particular day, and prints the silver value of 1000 and 400 paper dollars. Those sums come to 25 and 10 silver dollars.",
        instructions = """
            1. Save the example as paper_dollars.py.
            2. Run it: python3 paper_dollars.py
        """,
        example = """
            from fractions import Fraction as F

            RATE = 40    # illustrative paper dollars for one silver dollar
            for paper in (1000, 400):
                print(f"{paper} paper dollars at {RATE} for one: {F(paper, RATE)} silver dollars")
        """,
    ),

    entry(
        databaseId = 27,
        kind = Kind.FLEX,
        section = "Bridges",
        title = "Radius of the arch of the Iron Bridge",
        originalTitle = "The Iron Bridge at Coalbrookdale",
        author = "Thomas Farnolls Pritchard",
        year = 1779,
        summary = "The arch of the Iron Bridge is a circle through its springing points, so its radius follows from the span and the rise, which the program finds in exact fractions.",
        details = "The cast-iron Iron Bridge at Coalbrookdale was erected from 1779, with a span of about 100 feet, which is the figure used here. The example takes an illustrative rise of 30 feet, not the bridge's recorded rise, and finds the radius of the circle from the relation that its square equals the half-span squared plus the square of the radius less the rise. It prints the radius as an exact fraction in feet and checks the relation.",
        instructions = """
            1. Save the example as arch.py.
            2. Run it: python3 arch.py
        """,
        example = """
            from fractions import Fraction as F

            span = 100                 # feet, the bridge's span, as usually given
            half = F(span, 2)
            rise = 30                  # feet, an illustrative rise, not the recorded figure

            radius = (half ** 2 + rise ** 2) / (2 * rise)    # from R squared = half squared + (R minus rise) squared
            print(f"radius of the arch: {radius} feet")
            print("check: R squared equals half-span squared plus (R minus rise) squared:",
                  radius ** 2 == half ** 2 + (radius - rise) ** 2)
        """,
    ),

    entry(
        databaseId = 27,
        kind = Kind.FLEX,
        section = "Hydraulics",
        title = "Water delivered by a hydraulic ram",
        originalTitle = "Belier hydraulique",
        author = "Joseph-Michel Montgolfier",
        year = 1772,
        summary = "A hydraulic ram delivers a fraction of its drive water equal to its efficiency times the drive fall over the delivery height, which the program works out in exact fractions.",
        details = "Joseph-Michel Montgolfier devised the ram in 1772, and he patented it in 1796, so the year given is that of the invention. The example uses an illustrative efficiency of three fifths, a drive fall of 2 metres and a delivery height of 12 metres, not recorded figures. It prints the fraction of the drive water that is delivered, which comes to one tenth, and the litres delivered a day for an illustrative drive flow of 2000 litres.",
        instructions = """
            1. Save the example as ram.py.
            2. Run it: python3 ram.py
        """,
        example = """
            from fractions import Fraction as F

            efficiency = F(3, 5)      # illustrative efficiency, not a recorded figure
            drive_fall = 2            # metres of fall for the drive water
            delivery = 12             # metres the delivered water is raised

            fraction = efficiency * F(drive_fall, delivery)
            print(f"fraction of the drive water delivered: {fraction}")
            drive_flow = 2000         # illustrative litres of drive water a day
            print(f"litres delivered a day: {fraction * drive_flow}")
        """,
    ),

)
