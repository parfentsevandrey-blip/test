package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1720 to 1730: an eclipse's apparent sizes, spectacles balanced on the nose and ears, and a Pinchbeck alloy by mass. */
val db32Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 32,
        kind = Kind.FLEX,
        section = "Eclipses",
        title = "Maraldi's eclipse: when the moon covers the sun",
        originalTitle = "Eclipse observations of 1724",
        author = "Giacomo Filippo Maraldi",
        year = 1724,
        summary = "Maraldi's 1724 eclipse observations placed the corona with the sun, and the program compares the apparent widths of the sun and moon at the nearest and farthest points of their orbits.",
        details = "Maraldi observed the eclipse of 1724 and is recorded as taking the corona to belong to the sun. The example uses round values for the diameters and distances, computes the moon's apparent width against the sun's at perigee with perihelion and at apogee with aphelion, and tests whether the moon can cover the sun completely.",
        instructions = """
            1. Save the example as maraldi.py.
            2. Run it: python3 maraldi.py
        """,
        example = """
            # Round values: diameters and distances in kilometres.
            SUN_DIAMETER = 1392000
            MOON_DIAMETER = 3474
            SUN_PERIHELION = 147100000
            SUN_APHELION = 152100000
            MOON_PERIGEE = 363300
            MOON_APOGEE = 405500

            def share(moon_d, moon_dist, sun_d, sun_dist):
                # Ratio of the moon's apparent width to the sun's, from diameter over distance (small angles).
                return (moon_d / moon_dist) / (sun_d / sun_dist)

            close = share(MOON_DIAMETER, MOON_PERIGEE, SUN_DIAMETER, SUN_PERIHELION)
            far = share(MOON_DIAMETER, MOON_APOGEE, SUN_DIAMETER, SUN_APHELION)
            print(f"moon against sun at perigee and perihelion: {close:.2f}")
            print(f"moon against sun at apogee and aphelion: {far:.2f}")
            print("total eclipse possible at perigee and perihelion:", close >= 1)
            print("total eclipse possible at apogee and aphelion:", far >= 1)
        """,
    ),

    entry(
        databaseId = 32,
        kind = Kind.FLEX,
        section = "Alloys",
        title = "Pinchbeck's alloy by mass",
        originalTitle = "Pinchbeck alloy",
        author = "Christopher Pinchbeck",
        year = 1720,
        summary = "Pinchbeck's alloy of 83 parts copper to 17 parts zinc is worked out for a batch of 2500 grams, giving the mass of each metal in exact fractions.",
        details = "Pinchbeck, a London watchmaker, is credited with the alloy in 1720, and it was used as a cheaper substitute for gold in clock and jewellery work. The example uses exact fractions to find the copper and zinc in a 2500 gram batch and checks that the two shares add up to one.",
        instructions = """
            1. Save the example as pinchbeck.py.
            2. Run it: python3 pinchbeck.py
        """,
        example = """
            from fractions import Fraction as F

            # Pinchbeck's alloy as recorded: 83 parts copper and 17 parts zinc in every 100 by mass.
            copper_share = F(83, 100)
            zinc_share = F(17, 100)
            batch = 2500   # grams
            print("copper in the batch in grams:", batch * copper_share)
            print("zinc in the batch in grams:", batch * zinc_share)
            print("zinc in 100 grams of alloy:", 100 * zinc_share)
            print("the two shares add up to one:", copper_share + zinc_share == 1)
        """,
    ),

    entry(
        databaseId = 32,
        kind = Kind.FLEX,
        section = "Spectacles",
        title = "Scarlett's eyeglasses with side arms",
        originalTitle = "Eyeglasses with side arms",
        author = "Edward Scarlett",
        year = 1727,
        summary = "Scarlett's side arms of 1727 let the ears carry part of a pair of spectacles, and the lever rule splits the frame's weight between nose and ears for three positions of the centre of mass.",
        details = "Edward Scarlett's side arms let the ears take part of the weight, so the nose pads carry less. The example applies the lever rule to a frame of 20 grams with an illustrative ear-to-nose distance of 10 centimetres, prints the nose and ear loads for three positions of the centre of mass, and checks that the two loads add up to the weight.",
        instructions = """
            1. Save the example as scarlett.py.
            2. Run it: python3 scarlett.py
        """,
        example = """
            from fractions import Fraction as F

            # A frame of weight W rests on the nose and on the ear, which are L apart; its centre of mass is a from the ear.
            WEIGHT = 20     # grams, illustrative
            LENGTH = 10     # centimetres from ear to nose, illustrative

            def loads(a):
                # Lever rule: the nose carries the share a/L of the weight, and the ear carries the rest.
                nose = WEIGHT * F(a, LENGTH)
                ear = WEIGHT - nose
                return nose, ear

            for a in (2, 5, 8):
                nose, ear = loads(a)
                print(f"centre {a} cm from the ear: nose {nose} g, ear {ear} g")
            print("nose plus ear equals the weight:", all(sum(loads(a)) == WEIGHT for a in (2, 5, 8)))
        """,
    ),

)
