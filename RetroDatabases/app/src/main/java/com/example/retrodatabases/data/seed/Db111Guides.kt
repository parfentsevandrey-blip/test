package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 935 and 936: counting the morae of a tanka line in romaji, and unpacking a kenning of a skaldic poem. */
val db111Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 111,
        kind = Kind.GUIDE,
        section = "Japan",
        title = "How to count the morae of a tanka line written in romaji",
        originalTitle = "Tanka of thirty-one morae",
        author = "The poets of the tanka form",
        year = 935,
        summary = "A tanka of thirty-one morae in five lines of five, seven, five, seven and seven is counted by adding the vowels of each line and the syllabic n, which a short program can do.",
        details = "A mora is one beat of the sound, and each kana of the Japanese syllabary is one mora, with a standalone n counting as one more. In romaji without long vowels, each vowel letter marks one mora and each n that is not followed by a vowel marks one more, so the count of the 5-7-5-7-7 pattern can be made by a short program.",
        rare = true,
        instructions = """
            1. Save the example as morae.py.
            2. Run it: python3 morae.py
        """,
        example = """
            import re


            def morae(phrase):
                text = phrase.lower().replace(" ", "")
                vowels = sum(1 for ch in text if ch in "aiueo")
                syllabic_n = len(re.findall(r"n(?![aiueoy])", text))
                return vowels + syllabic_n


            phrases = ["haru", "tsuki", "kanji", "hon", "sakura", "kaze no oto"]
            for phrase in phrases:
                print(f"{phrase}: {morae(phrase)}")
            print("total:", sum(morae(p) for p in phrases))
        """,
    ),

    entry(
        databaseId = 111,
        kind = Kind.GUIDE,
        section = "Iceland",
        title = "How a kenning such as whale-road is unpacked into a plain word",
        originalTitle = "Kenning",
        author = "The skaldic poets of Iceland and Norway",
        year = 936,
        summary = "A kenning, a two-part figure such as whale-road for the sea, is unpacked by looking up each part in a glossary, which is how a listener read the code of a skaldic poem.",
        details = "Hofudlausn is built from kennings, and a listener had to match each figure with its plain meaning, with whale-road for the sea and wave-horse for a ship as the standard examples. The program below holds a small glossary of such figures, decodes a line of them and reports any figure that the glossary does not hold.",
        rare = true,
        instructions = """
            1. Save the example as kenning.py.
            2. Run it: python3 kenning.py
        """,
        example = """
            glossary = {
                "whale-road": "sea",
                "wave-horse": "ship",
            }


            def decode(figure):
                return glossary.get(figure, "no entry for " + figure)


            line = ["whale-road", "wave-horse", "crow-field"]
            for figure in line:
                print(f"{figure} means {decode(figure)}")
            decoded = [decode(f) for f in line if f in glossary]
            print("decoded figures:", len(decoded), "of", len(line))
            print("words:", " and ".join(decoded))
        """,
    ),

)
