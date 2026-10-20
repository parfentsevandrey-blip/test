package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 360 to 370: how a Pahlavi heterogram is spelled in Aramaic letters and read as a Persian word. */
val db168Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 168,
        kind = Kind.GUIDE,
        section = "Writing",
        title = "How a Pahlavi heterogram is spelled in Aramaic letters and read as a Persian word",
        originalTitle = "Pahlavi heterograms",
        author = "Sasanian scribes",
        year = 363,
        summary = "A Pahlavi heterogram is a word spelled in Aramaic letters and read as a Persian word, and a short program looks up two common heterograms and their readings.",
        details = "The sources do not date this writing habit, which Sasanian scribes used in inscriptions and books; 363 is the year of the anchor, Jovian's peace with Persia, not of the making. The readings are given in the common modern transcription, and the program covers only two heterograms, so it does not stand for the whole script.",
        instructions = """
            1. Save the example as heterogram.py.
            2. Run it: python3 heterogram.py
        """,
        example = """
            # Pahlavi heterograms: an Aramaic spelling that a scribe reads as a Persian word.
            READINGS = {"MLKA": "shah (king)", "BR": "pus (son)"}


            def read(spelling):
                return READINGS.get(spelling, spelling + " (not listed)")


            for spelling in ["MLKA", "BR", "XYZ"]:
                print(spelling, "is read as", read(spelling))
            print("heterograms in the table:", len(READINGS))
        """,
        rare = true,
    ),

)
