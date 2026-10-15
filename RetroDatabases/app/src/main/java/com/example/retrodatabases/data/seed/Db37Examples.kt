package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1670 to 1680: a degree turned into a radius, a longitude from noon, a focal length, a light delay, a bill and a right ascension. */
val db37Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 37,
        kind = Kind.EXAMPLE,
        section = "Geodesy",
        title = "Picard's degree of arc turned into the Earth's radius",
        originalTitle = "La Mesure de la Terre",
        author = "Jean Picard",
        year = 1670,
        summary = "A measured degree of arc gives the whole circumference of the Earth, and the circumference divided by two pi gives its radius.",
        details = "Picard's degree of about 57,060 toises was measured in 1669 and 1670, and the example turns it into a radius with the value of pi from the standard math module. The result is printed rounded to a whole toise, so it depends only on the stated degree and on the rule.",
        instructions = """
            1. Save the example as picard_radius.py.
            2. Run it: python3 picard_radius.py
        """,
        example = """
            from math import pi

            TOISES_PER_DEGREE = 57060   # Picard's measured length of one degree of meridian

            def radius_in_toises(per_degree):
                # A full circle is 360 degrees, so the circumference is 360 times one degree; the radius is the circumference over two pi.
                circumference = 360 * per_degree
                return round(circumference / (2 * pi))

            print("radius in toises:", radius_in_toises(TOISES_PER_DEGREE))
        """,
    ),

    entry(
        databaseId = 37,
        kind = Kind.EXAMPLE,
        section = "Navigation",
        title = "A Greenwich longitude from a noon difference",
        originalTitle = "Royal Observatory at Greenwich",
        author = "John Flamsteed",
        year = 1675,
        summary = "The sun crosses fifteen degrees of longitude an hour, so a local noon that falls four minutes after Greenwich noon lies one degree west.",
        details = "The rule of four minutes to one degree follows from the sun's motion across the sky. The example converts three noon differences into degrees of longitude, to two decimal places.",
        instructions = """
            1. Save the example as greenwich_longitude.py.
            2. Run it: python3 greenwich_longitude.py
        """,
        example = """
            def longitude_west(minutes_late):
                # The sun crosses 15 degrees of longitude in an hour, so 4 minutes of delay is 1 degree.
                return minutes_late / 4

            for minutes in (20, 45, 50):
                print(f"noon {minutes} minutes after Greenwich noon: {longitude_west(minutes):.2f} degrees west")
        """,
    ),

    entry(
        databaseId = 37,
        kind = Kind.EXAMPLE,
        section = "Optics",
        title = "Cassegrain's equivalent focal length",
        originalTitle = "Journal des scavans",
        author = "Laurent Cassegrain",
        year = 1672,
        summary = "The equivalent focal length of a Cassegrain telescope is the primary's focal length multiplied by the secondary's magnification.",
        details = "The Journal des scavans printed the design in 1672, and the rule follows from the two-mirror layout that the design uses. The example prints the equivalent focal length for three magnifications of a 1000 mm primary.",
        instructions = """
            1. Save the example as cassegrain_focal.py.
            2. Run it: python3 cassegrain_focal.py
        """,
        example = """
            def equivalent_focal_length(primary_mm, magnification):
                # The convex secondary multiplies the focal length of the concave primary by its magnification.
                return primary_mm * magnification

            for magnification in (3, 4, 6):
                length = equivalent_focal_length(1000, magnification)
                print(f"primary 1000 mm, magnification {magnification}: equivalent focal length {length} mm")
        """,
    ),

    entry(
        databaseId = 37,
        kind = Kind.EXAMPLE,
        section = "Astronomy",
        title = "Roemer's light-time across the orbit",
        originalTitle = "Demonstration touchant le mouvement de la lumiere",
        author = "Ole Roemer",
        year = 1676,
        summary = "Roemer's figure of about 22 minutes for light to cross the diameter of the Earth's orbit gives the time for any part of that distance by proportion.",
        details = "Roemer's estimate was printed in 1676, and light takes time in proportion to the distance it covers. The example scales the 22-minute figure to the whole diameter, to half of it and to a quarter, so each time is an exact multiple of the same value.",
        instructions = """
            1. Save the example as roemer_light_time.py.
            2. Run it: python3 roemer_light_time.py
        """,
        example = """
            ORBIT_DIAMETER_MINUTES = 22   # Roemer's figure of 1676 for light crossing the diameter of the Earth's orbit

            def light_time(fraction_of_diameter):
                # Light takes time in proportion to the distance it crosses.
                return ORBIT_DIAMETER_MINUTES * fraction_of_diameter

            for part in (1, 0.5, 0.25):
                print(f"across {part} of the orbit diameter: {light_time(part):.2f} minutes")
        """,
    ),

    entry(
        databaseId = 37,
        kind = Kind.EXAMPLE,
        section = "Commerce",
        title = "Savary's bill of exchange in pounds",
        originalTitle = "Le Parfait negociant",
        author = "Jacques Savary",
        year = 1675,
        summary = "A bill drawn in livres and paid in pounds is converted by dividing the sum by the agreed rate of exchange.",
        details = "Savary's manual explained how merchants worked out bills across currencies, and the example uses an illustrative rate of 30 livres to the pound, not a historical rate. It prints the pounds for three bills, rounded to two decimal places.",
        instructions = """
            1. Save the example as savary_bill.py.
            2. Run it: python3 savary_bill.py
        """,
        example = """
            def pounds_for_bill(livres, livres_per_pound):
                # A bill drawn in livres is paid in pounds at the agreed rate, so the pounds are the sum divided by the rate.
                return livres / livres_per_pound

            for livres in (600, 450, 1000):
                pounds = pounds_for_bill(livres, 30)
                print(f"bill of {livres} livres at 30 livres to the pound: {pounds:.2f} pounds")
        """,
    ),

    entry(
        databaseId = 37,
        kind = Kind.EXAMPLE,
        section = "Astronomy",
        title = "Hevelius's right ascension in degrees",
        originalTitle = "Machina coelestis",
        author = "Johannes Hevelius",
        year = 1673,
        summary = "Right ascension is counted in hours and minutes, and because the sky turns fifteen degrees an hour, the conversion to degrees is a multiplication.",
        details = "Hevelius gave star positions in degrees of arc, and the hour scale used for right ascension later converts to the same degrees by a plain multiplication. The example converts three positions to degrees, with each minute worth a quarter of a degree.",
        instructions = """
            1. Save the example as hevelius_ra.py.
            2. Run it: python3 hevelius_ra.py
        """,
        example = """
            def degrees_from_right_ascension(hours, minutes):
                # The sky turns 15 degrees an hour, so one hour is 15 degrees and one minute is a quarter of a degree.
                return 15 * hours + minutes / 4

            for hours, minutes in ((2, 30), (0, 40), (23, 59)):
                degrees = degrees_from_right_ascension(hours, minutes)
                print(f"right ascension {hours}h {minutes}m: {degrees:.2f} degrees")
        """,
    ),

)
