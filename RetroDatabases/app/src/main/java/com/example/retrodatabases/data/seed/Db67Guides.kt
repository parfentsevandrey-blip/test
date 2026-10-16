package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1371 to 1375: a fanqie spelling, seasonal hours, an alliterative long line, the shichen double hours and the asr shadow rule. */
val db67Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 67,
        kind = Kind.GUIDE,
        section = "Rhymes",
        title = "How a fanqie spelling gives the sound of a Chinese syllable",
        originalTitle = "Fanqie",
        author = "Makers of Chinese rhyme dictionaries",
        year = 1375,
        summary = "A fanqie spelling takes its opening sound from the first character of a pair and its rhyme from the second, and a short program builds three syllables that way.",
        details = "The method was used in Chinese rhyme dictionaries such as the Hongwu zhengyun of 1375, and the tone is given by the rhyme group. The example uses illustrative Latin spellings rather than the sounds of any period, so the output shows the method and not the real pronunciation.",
        instructions = """
            1. Save the example as fanqie.py.
            2. Run it: python3 fanqie.py
        """,
        example = """
            # Each pair: the first character gives the opening sound, the second gives the rest and the tone.
            PAIRS = [
                ("d", "ong", "level"),
                ("t", "ian", "rising"),
                ("m", "ing", "departing"),
            ]
            for opening, rest, tone in PAIRS:
                print(opening + rest, tone)
            print("pairs:", len(PAIRS))
        """,
    ),

    entry(
        databaseId = 67,
        kind = Kind.GUIDE,
        section = "Hours",
        title = "How the seasonal hours of a day are measured from its length",
        originalTitle = "Temporal hours",
        author = "Astronomers of the Islamic and Latin worlds",
        year = 1375,
        summary = "Seasonal hours split the time from sunrise to sunset into twelve equal parts and the night into twelve more, so a short program measures one day's hours.",
        details = "The system was in use in the Islamic and Latin worlds, and it explains the changing hours of prayer and of the working day. The example uses an illustrative day of fourteen hours of daylight, so its numbers hold for that day only.",
        instructions = """
            1. Save the example as seasonal_hours.py.
            2. Run it: python3 seasonal_hours.py
        """,
        example = """
            DAY_MINUTES = 14 * 60              # illustrative day of fourteen hours of daylight
            NIGHT_MINUTES = 24 * 60 - DAY_MINUTES
            print("day hour:", DAY_MINUTES // 12, "minutes")
            print("night hour:", NIGHT_MINUTES // 12, "minutes")
            print("total:", DAY_MINUTES + NIGHT_MINUTES)
        """,
    ),

    entry(
        databaseId = 67,
        kind = Kind.GUIDE,
        section = "Verse",
        title = "How a long line of alliterative verse is checked for its alliteration",
        originalTitle = "Alliterative long line",
        author = "Poets of the alliterative revival",
        year = 1375,
        summary = "An English alliterative long line has two half-lines, and it alliterates when the head of the second half-line matches a stressed sound in the first, so a short program checks three lines.",
        details = "The rule is the one used in the alliterative verse of the period, as in Piers Plowman and Sir Gawain and the Green Knight. The example uses three invented lines, so the outcome says nothing about those poems themselves.",
        instructions = """
            1. Save the example as alliteration.py.
            2. Run it: python3 alliteration.py
        """,
        example = """
            # Each long line: the stressed words of the first half-line, then the stressed words of the second.
            LINES = [
                (["dark", "deep"], ["dread", "beasts"]),
                (["hill", "hollow"], ["field", "wide"]),
                (["green", "grass"], ["gold", "grain"]),
            ]
            count = 0
            for number, (first, second) in enumerate(LINES, start=1):
                head = second[0][0]
                heads = [word[0] for word in first]
                if head in heads:
                    count += 1
                    print("line", number, "alliterates on", head)
                else:
                    print("line", number, "does not alliterate: head", head, "against", ", ".join(heads))
            print("alliterating lines:", count)
        """,
    ),

    entry(
        databaseId = 67,
        kind = Kind.GUIDE,
        section = "Hours",
        title = "How a Chinese double hour, shichen, is named from the clock",
        originalTitle = "Shichen",
        author = "Chinese calendar makers",
        year = 1375,
        summary = "The Chinese day was divided into twelve double hours, each two clock hours long and named by a branch, and a short program names the double hour of five clock times.",
        details = "The double hours began with zi at 23:00 and ran through chou, yin and the rest, so the branch names repeat every day. The example reads 24-hour clock times, and it shows the method rather than any instrument of the period.",
        instructions = """
            1. Save the example as shichen.py.
            2. Run it: python3 shichen.py
        """,
        example = """
            BRANCHES = ["zi", "chou", "yin", "mao", "chen", "si", "wu", "wei", "shen", "you", "xu", "hai"]

            def double_hour(hour):
                # zi covers 23:00 to 01:00, chou 01:00 to 03:00, and so on round the twelve branches.
                return BRANCHES[((hour + 1) // 2) % 12]

            for hour in [23, 0, 6, 12, 15]:
                print(f"{hour:02d}:00 is {double_hour(hour)}")
        """,
    ),

    entry(
        databaseId = 67,
        kind = Kind.GUIDE,
        section = "Prayer",
        title = "How the afternoon prayer time is found from a shadow's length",
        originalTitle = "Shadow rule of asr",
        author = "Muslim jurists and astronomers",
        year = 1371,
        summary = "The afternoon prayer begins when a shadow has grown to a set multiple of the object's height plus the noon shadow, and a short program finds the two shadow lengths for a pole.",
        details = "Jurists argued over the rule, with one view taking the shadow as the height plus the noon shadow and another taking twice the height plus the noon shadow. The example uses an illustrative pole of ten units and a noon shadow of 1.5 units, so it shows the arithmetic and not a real time of day.",
        rare = true,
        instructions = """
            1. Save the example as asr.py.
            2. Run it: python3 asr.py
        """,
        example = """
            POLE = 10.0        # height of the pole, in units (illustrative)
            NOON = 1.5         # length of the noon shadow, in the same units (illustrative)
            one_times = POLE + NOON
            two_times = 2 * POLE + NOON
            print("shadow for one times:", one_times)
            print("shadow for two times:", two_times)
            print("difference:", two_times - one_times)
        """,
    ),

)
