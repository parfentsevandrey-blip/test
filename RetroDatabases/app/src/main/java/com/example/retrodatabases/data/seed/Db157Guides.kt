package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 470 to 480: how a five-syllable king's name is spelled with Chinese characters chosen for sound. */
val db157Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 157,
        kind = Kind.GUIDE,
        section = "Sound",
        title = "How a king's five-syllable name is spelled with Chinese characters chosen for sound",
        originalTitle = "How a king's five-syllable name is spelled with Chinese characters chosen for sound",
        author = "Scribes of the Yamato court",
        year = 471,
        summary = "Old Japanese names were written with Chinese characters picked for their sound, and a short program pairs each syllable of a king's name with its chosen character, shown by a reading label.",
        details = "The Inariyama sword writes the name of a great king with five characters, one for each syllable, and the meanings of those characters play no part in the spelling. The Mandarin readings in the example are only labels, so the program shows the method and not the Old Japanese sound of each character.",
        instructions = """
            1. Save the example as wakatakeru.py.
            2. Run it: python3 wakatakeru.py
        """,
        example = """
            # Old Japanese names were written with Chinese characters chosen for their sound, not their sense.
            # Each syllable of the king's name gets one character; the Mandarin reading is only a label here.
            SOUND_TO_CHARACTER = {"wa": "huo", "ka": "jia", "ta": "duo", "ke": "zhi", "ru": "lu"}
            NAME = ["wa", "ka", "ta", "ke", "ru"]
            for syllable in NAME:
                print(syllable, "is written with the character read", SOUND_TO_CHARACTER[syllable])
            print("syllables in the name:", len(NAME))
            print("characters in the name:", len({SOUND_TO_CHARACTER[s] for s in NAME}))
        """,
        rare = true,
    ),

)
