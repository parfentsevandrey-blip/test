package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One example of 890 to 900, a placed record: a count of the letters on the Alfred Jewel, placed at 899 because the jewel itself is not closely dated. */
val db115Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 115,
        kind = Kind.EXAMPLE,
        section = "English",
        title = "Counting the letters of the Alfred Jewel inscription, AELFRED MEC HEHT GEWYRCAN",
        originalTitle = "AELFRED MEC HEHT GEWYRCAN",
        author = "Unknown goldsmith working for King Alfred",
        year = 899,
        summary = "A short program counts the words and letters of the inscription on the Alfred Jewel, which reads AELFRED MEC HEHT GEWYRCAN.",
        details = "This is a placed record: the sources do not date the jewel closely, and its inscription places it in the reign of Alfred, which lasted from about 871 to 899. The year 899 is the start of Edward the Elder's reign after Alfred's death, the anchor used for placement, and not the date of the making.",
        instructions = """
            1. Save the example as jewel.py.
            2. Run it: python3 jewel.py
        """,
        example = """
            inscription = "AELFRED MEC HEHT GEWYRCAN"
            words = inscription.split()
            print("words:", len(words))
            print("letters:", sum(len(word) for word in words))
            for word in words:
                print(word, len(word))
        """,
    ),

)
