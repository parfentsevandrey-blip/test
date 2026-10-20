package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 460 to 470: how the syllables of a Sanskrit verse line are counted by their vowels, a method dated to the Kahaum pillar's year. */
val db158Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 158,
        kind = Kind.GUIDE,
        section = "Prosody",
        title = "How the syllables of a Sanskrit verse line are counted by their vowels",
        originalTitle = "Sanskrit prosody",
        author = "Sanskrit prosodists, from Pingala onward",
        year = 460,
        summary = "Each vowel of a transliterated Sanskrit line marks one syllable, so a short program can count the syllables of four illustrative lines and check each count by hand.",
        details = "Sanskrit verse is measured by its syllables, and each syllable has one vowel, with long vowels and diphthongs counting as one. The method is far older than the Kahaum pillar, whose verse is dated to 460, so the year is the date of that inscription and not of the rule. The four lines in the example are illustrative and are not taken from the pillar.",
        instructions = """
            1. Save the example as syllables.py.
            2. Run it: python3 syllables.py
        """,
        example = """
            import re

            PADAS = [
                "rama nagaram gacchati",
                "sita vanam gacchati",
                "satyam eva jayate",
                "dharmah eva jayati",
            ]

            def syllables(word):
                # each run of vowels is one syllable in this transliteration
                return len(re.findall(r"[aeiou]+", word))

            total = 0
            for number, pada in enumerate(PADAS, start=1):
                count = sum(syllables(word) for word in pada.split())
                total += count
                print(f"pada {number}: {count} syllables")
            print("total:", total)
        """,
        rare = true,
    ),

)
