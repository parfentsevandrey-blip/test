package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1680 to 1690: a count of houses, a charter's regnal year, a pump's strokes, panes cut from a plate, a telegraph's messages and organ pipes. */
val db36Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 36,
        kind = Kind.EXAMPLE,
        section = "Statistics",
        title = "Petty's rule: houses times people per house",
        originalTitle = "Political Arithmetick",
        author = "William Petty",
        year = 1690,
        summary = "Petty estimated a population by counting houses and multiplying by an average household size, and the example repeats that step for three wards.",
        details = "Petty's Political Arithmetick of 1690 argued that counts of houses and hearths could be used to estimate a nation's people. The average of six people per house in the example is illustrative, chosen for the arithmetic, and the ward names and counts are invented.",
        instructions = """
            1. Save the example as petty_houses.py.
            2. Run it: python3 petty_houses.py
        """,
        example = """
            # Petty's method: population is about the number of houses times an average number of people per house.
            PEOPLE_PER_HOUSE = 6  # an illustrative average, not a figure from Petty

            wards = {"north ward": 120, "river ward": 85, "east ward": 230}
            total = 0
            for name, houses in wards.items():
                people = houses * PEOPLE_PER_HOUSE
                total += people
                print(f"{name}: {houses} houses, about {people} people")
            print("total: about", total, "people")
        """,
    ),

    entry(
        databaseId = 36,
        kind = Kind.EXAMPLE,
        section = "Charters",
        title = "Dating a charter by its regnal year",
        originalTitle = "De re diplomatica libri VI",
        author = "Jean Mabillon",
        year = 1681,
        summary = "Old charters often named their year by the regnal year of the king, and the example turns a regnal year into a calendar year once the year of accession is known.",
        details = "Mabillon's rules for judging charters included their dating formulas, and a regnal year was one of the commonest. The accession year of 1660 in the example is illustrative, and the charter years are invented for the arithmetic.",
        instructions = """
            1. Save the example as regnal_year.py.
            2. Run it: python3 regnal_year.py
        """,
        example = """
            # The year of accession is regnal year 1, so regnal year n falls in the calendar year accession + n - 1.
            ACCESSION = 1660   # an illustrative accession year

            for regnal in (1, 3, 12):
                print(f"regnal year {regnal}: {ACCESSION + regnal - 1}")
            print("a charter of 1681 is in regnal year", 1681 - ACCESSION + 1)
        """,
    ),

    entry(
        databaseId = 36,
        kind = Kind.EXAMPLE,
        section = "Hydraulics",
        title = "Pump strokes needed to fill a reservoir at Marly",
        originalTitle = "La Machine de Marly",
        author = "Rennequin Sualem",
        year = 1684,
        summary = "The strokes a pump needs are the reservoir volume divided by the volume each stroke delivers, rounded up to whole strokes.",
        details = "The Machine of Marly lifted water through many pumps working in stages, so its output was reckoned in strokes and litres. The example uses an illustrative reservoir of 1000 litres and three stroke sizes, to show the rounding that a pump crew must allow for.",
        instructions = """
            1. Save the example as marly_strokes.py.
            2. Run it: python3 marly_strokes.py
        """,
        example = """
            # A pump delivers a fixed volume at each stroke, so the strokes needed are the volume divided by that amount, rounded up.
            RESERVOIR_LITRES = 1000   # an illustrative reservoir

            def strokes_needed(volume_per_stroke):
                return -(-RESERVOIR_LITRES // volume_per_stroke)   # ceiling division on whole litres

            for volume in (25, 30, 40):
                print(f"{volume} litres per stroke: {strokes_needed(volume)} strokes for {RESERVOIR_LITRES} litres")
        """,
    ),

    entry(
        databaseId = 36,
        kind = Kind.EXAMPLE,
        section = "Glass",
        title = "Panes in a cast plate of glass",
        originalTitle = "Glace coulee",
        author = "Louis Lucas de Nehou",
        year = 1688,
        summary = "A cast plate of glass is cut into panes, and the example counts how many panes of one size a plate of another size gives, by comparing areas.",
        details = "The plate of 120 cm by 60 cm and the pane of 30 cm by 20 cm are illustrative sizes, not the dimensions of any plate of 1688. Four panes fit along the long side and three along the short side, so the division of areas gives the same twelve.",
        instructions = """
            1. Save the example as panes_in_plate.py.
            2. Run it: python3 panes_in_plate.py
        """,
        example = """
            PLATE = (120, 60)   # centimetres, an illustrative cast plate
            PANE = (30, 20)     # centimetres, an illustrative pane

            plate_area = PLATE[0] * PLATE[1]
            pane_area = PANE[0] * PANE[1]
            print("plate area:", plate_area, "square cm")
            print("pane area:", pane_area, "square cm")
            print("panes cut from one plate:", plate_area // pane_area)
        """,
    ),

    entry(
        databaseId = 36,
        kind = Kind.EXAMPLE,
        section = "Signals",
        title = "How many messages a visual telegraph can send",
        originalTitle = "Visual telegraph",
        author = "Robert Hooke",
        year = 1684,
        summary = "A signal with ten possible symbols at each step can send ten to the power of the message length in different messages, and the program counts them for lengths one to three.",
        details = "Hooke's proposal of 1684 relied on signs seen at a distance, so the number of messages depends on how many distinct signs a station can show. The ten symbols here are illustrative, not Hooke's count.",
        instructions = """
            1. Save the example as telegraph_count.py.
            2. Run it: python3 telegraph_count.py
        """,
        example = """
            # A station that shows one of n symbols at each step can send n to the power k messages of k symbols.
            SYMBOLS = 10   # an illustrative number of distinct symbols

            for length in (1, 2, 3):
                print(f"messages of {length} symbol(s): {SYMBOLS ** length}")
            print("messages of every length from 1 to 3:", sum(SYMBOLS ** k for k in (1, 2, 3)))
        """,
    ),

    entry(
        databaseId = 36,
        kind = Kind.EXAMPLE,
        section = "Music",
        title = "Pipes in an organ from its ranks and keys",
        originalTitle = "Orgel-Probe",
        author = "Andreas Werckmeister",
        year = 1681,
        summary = "Each rank of an organ has one pipe for every key, so the pipe count is the keys times the ranks, and the example counts it for two, three and four ranks.",
        details = "Werckmeister's Orgel-Probe of 1681 was a test of organs in use, and a builder or organist could check a count like this against the instrument. The keys and ranks in the example are illustrative, not those of any organ Werckmeister tested.",
        instructions = """
            1. Save the example as organ_pipes.py.
            2. Run it: python3 organ_pipes.py
        """,
        example = """
            KEYS = 45    # an illustrative number of keys on one manual
            RANKS = 4    # an illustrative number of ranks of pipes

            print(f"{RANKS} ranks of {KEYS} keys: {KEYS * RANKS} pipes")
            for ranks in (2, 3):
                print(f"{ranks} ranks of {KEYS} keys: {KEYS * ranks} pipes")
        """,
    ),

)
