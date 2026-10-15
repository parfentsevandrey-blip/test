package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1690 to 1700: alphabetical headwords, a fire-hose jet, a lighthouse horizon and the apparent width of a planet. */
val db35Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 35,
        kind = Kind.PROJECT,
        section = "Dictionaries",
        title = "Sorting headwords for Furetiere's 1690 word list",
        originalTitle = "Dictionnaire universel",
        author = "Antoine Furetiere",
        year = 1690,
        summary = "A short program puts a list of French headwords in alphabetical order without regard to capital letters, as the first step in building a dictionary.",
        details = "Furetiere's dictionary of 1690 was arranged alphabetically by headword, so sorting a word list is the first job in building a book of this kind. The example sorts seven words, keeps their original capital letters, and compares them without regard to case.",
        instructions = """
            1. Save the example as headwords.py.
            2. Run it: python3 headwords.py
        """,
        example = """
            words = ["cendre", "Abbe", "aigle", "bal", "ami", "amour", "zele"]
            ordered = sorted(words, key=lambda w: w.lower())
            for word in ordered:
                print(word)
            print(f"{len(ordered)} headwords sorted")
        """,
    ),

    entry(
        databaseId = 35,
        kind = Kind.PROJECT,
        section = "Fire engines",
        title = "How high a hose jet rises from its nozzle speed",
        originalTitle = "Beschryving der nieuwlyks uitgevonden en geoctrojeerde slang-brand-spuiten",
        author = "Hose-engine makers of the 1690s",
        year = 1690,
        summary = "A jet leaving its nozzle at a given speed rises to a height set by that speed, so a speed of 20 metres per second lifts the water about 20 metres.",
        details = "Ignoring air resistance, the rise of an ideal jet is the square of the speed divided by twice the gravity constant. That is the same relation as Torricelli's law of efflux, read the other way round, and the three nozzle speeds are chosen for illustration.",
        instructions = """
            1. Save the example as hose_jet.py.
            2. Run it: python3 hose_jet.py
        """,
        example = """
            g = 9.81
            for speed in (10.0, 20.0, 30.0):
                height = speed * speed / (2 * g)
                print(f"nozzle speed {speed:.0f} m/s: jet rises {height:.2f} m")
        """,
    ),


    entry(
        databaseId = 35,
        kind = Kind.PROJECT,
        section = "Lighthouses",
        title = "How far a light 30 metres high can be seen over the sea",
        originalTitle = "The first Eddystone lighthouse",
        author = "Henry Winstanley",
        year = 1698,
        summary = "A light 30 metres above the water can be seen about 19.55 kilometres away, a distance limited by the curve of the Earth.",
        details = "The distance to the horizon is the square root of twice the Earth's radius times the height of the light, ignoring refraction. The project uses an Earth radius of 6,371 kilometres, and a height of 30 metres chosen for illustration rather than as the height of any lighthouse of the 1690s.",
        instructions = """
            1. Save the example as horizon.py.
            2. Run it: python3 horizon.py
        """,
        example = """
            import math

            radius = 6371000.0
            height = 30.0
            distance = math.sqrt(2 * radius * height)
            print(f"light at {height:.0f} m: seen to about {distance / 1000:.2f} km")
            print(f"doubling the height multiplies the distance by {math.sqrt(2):.3f}")
        """,
    ),

    entry(
        databaseId = 35,
        kind = Kind.PROJECT,
        section = "Astronomy",
        title = "How large an illustrative planet looks from 100 million kilometres",
        originalTitle = "Cosmotheoros, sive de terris coelestibus",
        author = "Christiaan Huygens",
        year = 1698,
        summary = "A planet of a given width looks smaller the farther away it is, and its apparent width can be given in arcseconds.",
        details = "Huygens's Cosmotheoros argued that the planets are worlds like the Earth, and an observer on Earth sees each planet only as a small disc. The diameter and distances here are chosen for illustration, not taken from any planet's data, and halving the distance roughly doubles the apparent width.",
        instructions = """
            1. Save the example as planet_size.py.
            2. Run it: python3 planet_size.py
        """,
        example = """
            import math

            diameter_km = 12000.0
            distance_km = 100000000.0
            arcsec_per_radian = 206264.806
            width_far = 2 * math.atan(diameter_km / (2 * distance_km)) * arcsec_per_radian
            width_near = 2 * math.atan(diameter_km / (2 * (distance_km / 2))) * arcsec_per_radian
            print(f"apparent diameter at 100 million km: {width_far:.2f} arcseconds")
            print(f"at half that distance: {width_near:.2f} arcseconds")
        """,
    ),

)
