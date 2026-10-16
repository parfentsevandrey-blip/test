package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1490 to 1500: a binary search in a docket, English plurals, a zodiac lookup, a hexachord and a pole-star latitude. */
val db55Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 55,
        kind = Kind.GUIDE,
        section = "Dockets",
        title = "How a binary search finds a case in a sorted docket",
        originalTitle = "Reichskammergericht",
        author = "Diet of Worms under King Maximilian I",
        year = 1495,
        summary = "A docket kept in number order can be searched by halving the range each time, and a short program finds case numbers in eight entries with three or four probes.",
        details = "The case numbers are invented for the example and are not taken from the court's records. The program counts its probes, so the output is fixed by the method and can be checked by hand.",
        instructions = """
            1. Save the example as binary_docket.py.
            2. Run it: python3 binary_docket.py
        """,
        example = """
            CASES = [3, 8, 12, 17, 23, 31, 42, 57]   # a sorted list of case numbers, invented for the example

            def find(cases, wanted):
                # Halve the range each time until the wanted number is found or the range is empty.
                low, high = 0, len(cases) - 1
                probes = 0
                while low <= high:
                    mid = (low + high) // 2
                    probes += 1
                    if cases[mid] == wanted:
                        return mid, probes
                    if cases[mid] < wanted:
                        low = mid + 1
                    else:
                        high = mid - 1
                return None, probes

            for wanted in (23, 40, 57):
                index, probes = find(CASES, wanted)
                if index is None:
                    print(f"case {wanted} not found after {probes} probes")
                else:
                    print(f"case {wanted} found at index {index} after {probes} probes")
        """,
    ),

    entry(
        databaseId = 55,
        kind = Kind.GUIDE,
        section = "Plurals",
        title = "How English nouns take their plurals by rule",
        originalTitle = "Promptorium parvulorum",
        author = "Anonymous, compiled about 1440, printed by Wynkyn de Worde",
        year = 1499,
        summary = "The Promptorium parvulorum gave English words to boys with their Latin equivalents, and this program forms the English plurals of a short word list by three spelling rules.",
        details = "The three rules are the ordinary spelling rules of English plurals, written for the example, and the words are a short invented list. The output is fixed by the rules and the list, so each plural can be checked by hand.",
        instructions = """
            1. Save the example as plural_rules.py.
            2. Run it: python3 plural_rules.py
        """,
        example = """
            VOWELS = "aeiou"

            def plural(noun):
                # A y after a consonant becomes ies; words ending in s, x, z, ch or sh take es; the rest take s.
                if noun.endswith("y") and noun[-2] not in VOWELS:
                    return noun[:-1] + "ies"
                if noun.endswith(("s", "x", "z", "ch", "sh")):
                    return noun + "es"
                return noun + "s"

            for noun in ("box", "city", "boy", "church", "bus", "day", "dish", "cat"):
                print(f"{noun} -> {plural(noun)}")
        """,
    ),

    entry(
        databaseId = 55,
        kind = Kind.GUIDE,
        section = "Zodiac",
        title = "How a zodiac sign follows from a birth date",
        originalTitle = "Fasciculus medicinae",
        author = "Johannes de Ketham",
        year = 1491,
        summary = "Ketham's zodiac man pairs each sign of the zodiac with a part of the body, and a short program finds the sign that a birth date falls under.",
        details = "The example uses the common modern dates of the signs, which differ from the dates that medical astrologers gave in Ketham's time, so the table shows the arithmetic and not the medical rule. The output is fixed by the table, and each date can be checked by hand.",
        instructions = """
            1. Save the example as zodiac_sign.py.
            2. Run it: python3 zodiac_sign.py
        """,
        example = """
            # Each sign starts on the date given here, in the common modern reckoning.
            STARTS = [
                ((1, 20), "Aquarius"), ((2, 19), "Pisces"), ((3, 21), "Aries"), ((4, 20), "Taurus"),
                ((5, 21), "Gemini"), ((6, 21), "Cancer"), ((7, 23), "Leo"), ((8, 23), "Virgo"),
                ((9, 23), "Libra"), ((10, 23), "Scorpio"), ((11, 22), "Sagittarius"), ((12, 22), "Capricorn"),
            ]

            def sign(month, day):
                # The sign is the last start that is not later than the date; early January belongs to Capricorn.
                name = "Capricorn"
                for start, sign_name in STARTS:
                    if (month, day) >= start:
                        name = sign_name
                return name

            for month, day in ((3, 21), (3, 20), (7, 4), (12, 25), (1, 1), (2, 19), (2, 18)):
                print(f"{month:02d}-{day:02d}: {sign(month, day)}")
        """,
    ),

    entry(
        databaseId = 55,
        kind = Kind.GUIDE,
        section = "Solmization",
        title = "How the natural hexachord names a melody's notes",
        originalTitle = "Practica musicae",
        author = "Franchinus Gaffurius",
        year = 1496,
        summary = "Gaffurio's Practica musicae taught singers to name the notes of a melody with the syllables of the hexachord, ut re mi fa sol la, and this program names the notes of a short tune.",
        details = "The natural hexachord on C names six notes with the syllables ut, re, mi, fa, sol and la. The tune in the example is invented for it and includes one note outside the hexachord, which the program marks.",
        instructions = """
            1. Save the example as hexachord_names.py.
            2. Run it: python3 hexachord_names.py
        """,
        example = """
            # The natural hexachord on C names six notes with the syllables ut, re, mi, fa, sol and la.
            SYLLABLES = {"C": "ut", "D": "re", "E": "mi", "F": "fa", "G": "sol", "A": "la"}
            melody = ["C", "E", "G", "A", "F", "D", "C", "B"]
            for note in melody:
                print(note, SYLLABLES.get(note, "not in this hexachord"))
        """,
    ),

    entry(
        databaseId = 55,
        kind = Kind.GUIDE,
        section = "Navigation",
        title = "How the pole star's height gives a latitude at sea",
        originalTitle = "Astrolabe",
        author = "Unknown maker; object reported by Mearns",
        year = 1496,
        summary = "A navigator who sights the pole star reads its height above the horizon as the latitude, a rule of thumb that an astrolabe could measure at sea.",
        details = "The example takes the pole height as the latitude, as the rule of thumb did, and compares three sights and the change of latitude between the first and the third. The sights are illustrative, and the rule is only approximate because the pole star lies a little away from the pole.",
        instructions = """
            1. Save the example as pole_latitude.py.
            2. Run it: python3 pole_latitude.py
        """,
        example = """
            # Rule of thumb: the height of the pole star above the horizon is taken as the latitude.
            sights = (("first", 38), ("second", 41), ("third", 52))   # pole heights in degrees, illustrative
            for label, height in sights:
                print(f"{label} sight: pole height {height} degrees, latitude {height} degrees north")
            change = sights[2][1] - sights[0][1]
            print(f"change of latitude between the first and third sights: {change} degrees")
        """,
    ),

)
