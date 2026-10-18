package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 950: how a name is spelled sound by sound with the sixteen runes of the Younger futhark, the alphabet of the Jelling stone. */
val db109Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 109,
        kind = Kind.GUIDE,
        section = "Writing",
        title = "How a name is spelled with the sixteen runes of the Younger futhark",
        originalTitle = "Yngre futhark",
        author = "Scandinavian rune carvers",
        year = 950,
        summary = "A name is spelled sound by sound with the sixteen runes of the Younger futhark, and a short program writes three Norse names in the rune names.",
        details = "Several sounds share one rune, so k and g both use the kaun rune and t and d both use the tyr rune. The alphabet is older than the Jelling runestone that uses it, so 950 is the year of the anchor, the date of the stone, and not the year when the alphabet was made.",
        instructions = """
            1. Save the example as runes.py.
            2. Run it: python3 runes.py
        """,
        example = """
            RUNES = [
                "fe", "ur", "thurs", "oss", "reid", "kaun", "hagall", "naudr",
                "iss", "ar", "sol", "tyr", "bjarkan", "madr", "logr", "yr",
            ]
            SOUNDS = {
                "f": "fe", "u": "ur", "th": "thurs", "r": "reid", "k": "kaun", "g": "kaun",
                "h": "hagall", "n": "naudr", "i": "iss", "e": "iss", "a": "ar", "s": "sol",
                "t": "tyr", "d": "tyr", "b": "bjarkan", "p": "bjarkan", "m": "madr", "l": "logr",
            }


            def spell(name):
                names = []
                i = 0
                while i < len(name):
                    if name[i:i + 2] == "th":
                        names.append(SOUNDS["th"])
                        i += 2
                    else:
                        names.append(SOUNDS[name[i]])
                        i += 1
                return names


            print("runes in the alphabet:", len(RUNES))
            for name in ["Dan", "Hal", "Ulf"]:
                print(name + ":", " ".join(spell(name.lower())))
        """,
        rare = true,
    ),

)
