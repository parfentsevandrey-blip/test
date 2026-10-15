package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1570 to 1580: the hands of a clock dial, a Nahuatl word list, the syllables of measured verse and a tally of questionnaire replies. */
val db47Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 47,
        kind = Kind.PROJECT,
        section = "Clocks",
        title = "Angles of the clock hands at set times on a dial",
        originalTitle = "Astronomische Uhr",
        author = "Conrad Dasypodius and Isaac Habrecht",
        year = 1574,
        summary = "On a twelve-hour dial the hour hand turns 30 degrees an hour and the minute hand 6 degrees a minute, and the example computes both angles for four set times.",
        details = "The clock at Strasbourg was finished in 1574, and any dial of this kind follows the same arithmetic for its two hands. The example is a plain sum of the two hands' turns, not a model of the cathedral clock's own gearing, and it uses exact fractions.",
        instructions = """
            1. Save the example as clock_hands.py.
            2. Run it: python3 clock_hands.py
        """,
        example = """
            from fractions import Fraction as F

            def hand_angles(hour, minute):
                # Degrees from the twelve o'clock mark: the hour hand turns 30 degrees an hour, the minute hand 6 degrees a minute.
                hour_angle = F(hour % 12) * 30 + F(minute, 2)
                minute_angle = F(minute) * 6
                return hour_angle, minute_angle

            for hour, minute in ((3, 30), (12, 0), (6, 15), (9, 45)):
                hour_angle, minute_angle = hand_angles(hour, minute)
                print(f"{hour}:{minute:02d}: hour hand {hour_angle}, minute hand {minute_angle}")
        """,
    ),

    entry(
        databaseId = 47,
        kind = Kind.PROJECT,
        section = "Nahuatl",
        title = "A lookup of Nahuatl words to Spanish glosses",
        originalTitle = "Vocabulario en lengua mexicana y castellana",
        author = "Alonso de Molina",
        year = 1571,
        summary = "A lookup table turns a Nahuatl word into its Spanish gloss, as a reader of a bilingual dictionary does, and the example checks four words against a short list.",
        details = "Molina's dictionary of 1571 pairs Nahuatl words with Spanish ones, and the lookup works in the same direction on a short list. The glosses are common meanings of the words, and the example reports a word that is not in the list, which a full dictionary would also have to handle.",
        instructions = """
            1. Save the example as nahuatl_lookup.py.
            2. Run it: python3 nahuatl_lookup.py
        """,
        example = """
            GLOSSES = {"atl": "water", "tepetl": "hill", "calli": "house", "tochtli": "rabbit", "coyotl": "coyote"}
            for word in ("calli", "atl", "tochtli", "pilli"):
                print(f"{word}: {GLOSSES.get(word, 'not in this short list')}")
        """,
    ),

    entry(
        databaseId = 47,
        kind = Kind.PROJECT,
        section = "Verse",
        title = "Counting the syllables of a line of measured verse",
        originalTitle = "vers mesures",
        author = "Jean-Antoine de Baif",
        year = 1570,
        summary = "Measured verse set the syllables by their length, and a short program counts the syllables of an illustrative line and checks the total against twelve.",
        details = "Baif's academy of 1570 wanted French verse to follow the measure of the syllables, so that a line could be scanned as the classical lines had been. The line in the example is illustrative and is not a quotation from any poem, and the program only counts the syllables and checks the total.",
        instructions = """
            1. Save the example as syllables.py.
            2. Run it: python3 syllables.py
        """,
        example = """
            line = ["ma", "ri", "on", "ne", "pen", "sa", "bien", "de", "ces", "vers", "ca", "nons"]
            print("syllables in the line:", len(line))
            print("twelve-syllable line:", len(line) == 12)
            print("first syllable:", line[0])
        """,
    ),

    entry(
        databaseId = 47,
        kind = Kind.PROJECT,
        section = "Surveys",
        title = "Tallying illustrative questionnaire replies from five towns",
        originalTitle = "Relaciones geograficas",
        author = "Philip II of Spain",
        year = 1577,
        summary = "A tally of questionnaire replies by region shows how the answers of many places can be summed, and the example uses an invented table of five towns.",
        details = "Philip II's questionnaire of 1577 asked each place for an account, and the replies were gathered by region, which is the tally this program makes. The table is invented for illustration, so its counts say nothing about the real replies of any town.",
        instructions = """
            1. Save the example as replies_tally.py.
            2. Run it: python3 replies_tally.py
        """,
        example = """
            REPLIES = [("Town A", "north", True), ("Town B", "north", False), ("Town C", "south", True),
                       ("Town D", "south", True), ("Town E", "east", False)]
            tally = {}
            for town, region, has_river in REPLIES:
                count, rivers = tally.get(region, (0, 0))
                tally[region] = (count + 1, rivers + (1 if has_river else 0))
            for region in sorted(tally):
                count, rivers = tally[region]
                print(f"{region} region: replies {count}, with a river {rivers}")
            print(f"total replies: {len(REPLIES)}")
        """,
    ),

)
