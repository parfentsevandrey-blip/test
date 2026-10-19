package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One guide of 870 to 880, a placed record: al-Kindi's letter counts for breaking a substitution cipher, placed at 873, the year of his death. */
val db117Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 117,
        kind = Kind.GUIDE,
        section = "Arabic",
        title = "How letter counts break a substitution cipher, as al-Kindi set out",
        originalTitle = "Risalah fi istikhraj al-mu'amma",
        author = "Ya'qub ibn Ishaq al-Kindi",
        year = 873,
        summary = "Al-Kindi's treatise on deciphering messages sets out how the letter counts of a ciphertext point to its plain letters, and a short program applies that count to a sample cipher.",
        details = "This is a placed record: the treatise is usually dated about 850, so 873 is the year of an anchor, al-Kindi's death, and not the date of the method. The example uses English letter frequencies, where E, T and A are among the commonest letters, and the same counting works for Arabic, the language al-Kindi wrote in.",
        instructions = """
            1. Save the example as tally.py.
            2. Run it: python3 tally.py
        """,
        example = """
            CIPHER = "RBQ RQW WRQ RQZ KQQ"
            letters = [c for c in CIPHER if c.isalpha()]
            counts = {}
            for c in letters:
                counts[c] = counts.get(c, 0) + 1
            ranked = sorted(counts.items(), key=lambda pair: (-pair[1], pair[0]))
            print("letters in the sample:", len(letters))
            for letter, n in ranked[:3]:
                print(letter, n)
            guess = {ranked[0][0]: "E", ranked[1][0]: "T", ranked[2][0]: "A"}
            print(f"guessed: {ranked[0][0]} is E, {ranked[1][0]} is T, {ranked[2][0]} is A")
            decoded = "".join(guess.get(c, "?") if c.isalpha() else " " for c in CIPHER)
            print("partly decoded:", decoded)
        """,
        rare = true,
    ),

)
