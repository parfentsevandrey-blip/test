package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One guide of 880 to 890: Notker's method of setting one syllable under each note of a long melody, dated about 884 and shown as a short program. */
val db116Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 116,
        kind = Kind.GUIDE,
        section = "Music",
        title = "How Notker's sequences set one syllable under each note of a jubilus",
        originalTitle = "Liber hymnorum",
        author = "Notker the Stammerer of Saint Gall",
        year = 884,
        summary = "Notker of Saint Gall fitted new Latin words to long melodies by setting one syllable under each note, and a short program shows that method.",
        details = "His preface to the Liber hymnorum is a letter to Liutward of Vercelli, and a scholar's inference from its history dates its delivery to 884, while the Catholic Encyclopedia gives a wider window of 881 to 887. Notker recalls that his teacher Iso pointed out that each note of the long jubilus needed its own word, and the example is a simplified reading of that method. It puts one syllable under each note and holds the last syllable across the notes that remain.",
        instructions = """
            1. Save the example as sequence_notes.py.
            2. Run it: python3 sequence_notes.py
        """,
        example = """
            NOTES = ["a", "g", "f", "g", "a", "c", "d", "c", "a"]
            SYLLABLES = ["Lau", "da", "te", "Do", "mi", "num"]
            for i, note in enumerate(NOTES):
                syllable = SYLLABLES[min(i, len(SYLLABLES) - 1)]
                print(f"note {i + 1} ({note}): {syllable}")
            print("notes in all:", len(NOTES))
            print("syllables in all:", len(SYLLABLES))
            print("notes that hold the last syllable:", len(NOTES) - len(SYLLABLES) + 1)
        """,
    ),

)
