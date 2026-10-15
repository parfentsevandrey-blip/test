package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1600 to 1610: Quechua counting, gear ratios, telescope power, a pantograph's scale and a land yacht's speed. */
val db44Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 44,
        kind = Kind.GUIDE,
        section = "Quechua",
        title = "How Quechua counts from one to ten",
        originalTitle = "Vocabulario de la lengua general de todo el Peru",
        author = "Diego Gonzalez Holguin",
        year = 1608,
        summary = "Quechua has its own words for the numbers from one to ten, and a short program prints them in order beside their numerals.",
        details = "Holguin's dictionary of 1608 recorded the words of the Inca language for the priests of the Andes. The example uses the words in the modern spelling of the Cuzco dialect rather than the spelling of 1608, and it prints the first ten numbers.",
        instructions = """
            1. Save the example as quechua_count.py.
            2. Run it: python3 quechua_count.py
        """,
        example = """
            WORDS = ["huk", "iskay", "kimsa", "tawa", "pichqa", "suqta", "qanchis", "pusaq", "isqun", "chunka"]

            for number, word in enumerate(WORDS, start=1):
                print(number, word)
        """,
    ),

    entry(
        databaseId = 44,
        kind = Kind.GUIDE,
        section = "Gears",
        title = "How a train of gears multiplies turns",
        originalTitle = "Novo teatro di machine et edificii",
        author = "Vittorio Zonca",
        year = 1607,
        summary = "Each pair of toothed wheels turns its driven wheel by the driving teeth over the driven teeth, and a train multiplies those ratios.",
        details = "Zonca's book of 1607 drew machines driven by gears, and the arithmetic of a gear train was part of the millwright's trade. The example works out three trains with exact fractions, using illustrative tooth counts that are not taken from any machine in the book.",
        instructions = """
            1. Save the example as gear_train.py.
            2. Run it: python3 gear_train.py
        """,
        example = """
            from fractions import Fraction as F

            def train_ratio(*pairs):
                # Each pair is (driving teeth, driven teeth); the ratios of the pairs multiply along the train.
                ratio = F(1)
                for driving, driven in pairs:
                    ratio *= F(driving, driven)
                return ratio

            trains = {"one pair": ((12, 36),), "two pairs": ((12, 36), (10, 40)), "two pairs, other teeth": ((20, 30), (15, 45))}
            for name, pairs in trains.items():
                print(f"{name}: output turns per input turn {train_ratio(*pairs)}")
        """,
    ),

    entry(
        databaseId = 44,
        kind = Kind.GUIDE,
        section = "Optics",
        title = "How a telescope's power follows from its focal lengths",
        originalTitle = "Kijker",
        author = "Hans Lippershey",
        year = 1608,
        summary = "A telescope's power is the focal length of its large lens divided by the focal length of its eyepiece.",
        details = "Lippershey's instrument of 1608 used two lenses, and the rule here is the usual one for such telescopes. The example applies it to three illustrative pairs of focal lengths, not to Lippershey's own instrument.",
        instructions = """
            1. Save the example as telescope_power.py.
            2. Run it: python3 telescope_power.py
        """,
        example = """
            from fractions import Fraction as F

            def magnifying_power(objective_mm, eyepiece_mm):
                # The power is the focal length of the large lens divided by the focal length of the eyepiece.
                return F(objective_mm, eyepiece_mm)

            for objective, eyepiece in ((1000, 50), (900, 100), (600, 60)):
                print(f"objective {objective} mm, eyepiece {eyepiece} mm: power {magnifying_power(objective, eyepiece)}")
        """,
    ),

    entry(
        databaseId = 44,
        kind = Kind.GUIDE,
        section = "Drawing instruments",
        title = "How a pantograph's arms set the scale of a copy",
        originalTitle = "Pantographice seu ars delineandi",
        author = "Christoph Scheiner",
        year = 1603,
        summary = "In a pantograph the ratio of the pencil arm to the tracing arm sets the scale, so each point of the copy lies at that ratio times the tracer's point from the pivot.",
        details = "Scheiner's frame of about 1603 copied figures at a fixed scale, and the example uses a simplified model in which the pencil and the tracer lie on the same side of the pivot. The arm lengths are illustrative and are not Scheiner's own.",
        instructions = """
            1. Save the example as pantograph_scale.py.
            2. Run it: python3 pantograph_scale.py
        """,
        example = """
            from fractions import Fraction as F

            TRACING_ARM = 10   # distance from the pivot to the tracing point, illustrative
            PENCIL_ARM = 25    # distance from the pivot to the pencil, illustrative

            def copy_point(x, y):
                # Each point of the copy is the tracer's point multiplied by the ratio of the arms.
                scale = F(PENCIL_ARM, TRACING_ARM)
                return scale * x, scale * y

            for x, y in ((3, 4), (6, 1)):
                cx, cy = copy_point(x, y)
                print(f"tracer ({x}, {y}) copies to pencil ({cx}, {cy})")
        """,
    ),

    entry(
        databaseId = 44,
        kind = Kind.GUIDE,
        section = "Vehicles",
        title = "How a land yacht's speed follows from its run",
        originalTitle = "Currus veliferi",
        author = "Simon Stevin",
        year = 1602,
        summary = "Speed is distance divided by time, and de Gheyn's engraving of the land yacht's run gives fourteen Dutch miles in two hours.",
        details = "Stevin's sail carriage ran from Scheveningen to Petten, and the engraving of 1603 records the run as fourteen Dutch miles in two hours, which is seven Dutch miles an hour. The example divides distances by times with exact fractions, using the engraving's figures and two illustrative runs.",
        instructions = """
            1. Save the example as yacht_speed.py.
            2. Run it: python3 yacht_speed.py
        """,
        example = """
            from fractions import Fraction as F

            def speed(miles, hours):
                # Speed is distance divided by time; exact fractions keep the figures clean.
                return F(miles) / hours

            for miles, hours in ((14, 2), (10, 4), (21, 3)):
                print(f"{miles} Dutch miles in {hours} hours: {speed(miles, hours)} Dutch miles per hour")
        """,
    ),

)
