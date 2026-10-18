package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1210 to 1220: the citation form of the Decretum, a restatement drill, a seasonal arrangement of poems and the morae of a Japanese sentence. */
val db83Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 83,
        kind = Kind.GUIDE,
        section = "Law",
        title = "How a citation of Gratian's Decretum names its cause, question and canon",
        originalTitle = "Citation form of the Decretum",
        author = "Canon lawyers of the period",
        year = 1215,
        summary = "A citation such as C.11 q.1 c.20 names a place in Gratian's Decretum by cause, question and canon, and a short program spells out each citation in words.",
        details = "The abbreviations are C for causa, q for quaestio, c for canon and D for distinction, and the example expands three citations. The form is the one lawyers used when they cited the Decretum in the glosses of the time, so the guide is dated to the Glossa ordinaria of about 1215.",
        instructions = """
            1. Save the example as decretum.py.
            2. Run it: python3 decretum.py
        """,
        example = """
            ABBREV = {"C": "cause", "q": "question", "c": "canon", "D": "distinction"}
            citations = ["C.11 q.1 c.20", "D.3 c.2", "C.1 q.3 c.9"]
            for cite in citations:
                words = []
                for part in cite.split():
                    letter, number = part.split(".")
                    words.append(ABBREV[letter] + " " + number)
                print(cite, "means", " and ".join(words))
            print("citations expanded:", len(citations))
        """,
    ),

    entry(
        databaseId = 83,
        kind = Kind.GUIDE,
        section = "Rhetoric",
        title = "How one sentence is restated in several words, as the handbook teaches",
        originalTitle = "Restatement of a theme",
        author = "Geoffrey of Vinsauf",
        year = 1210,
        summary = "Geoffrey of Vinsauf's handbook teaches a writer to restate one idea in several words, and a short program lists the variants of one sentence.",
        details = "The example replaces one verb with three synonyms, which gives four variants in all, and it prints each one in turn. The guide follows the handbook's advice to vary a theme, and it is dated to the handbook's date of about 1210.",
        instructions = """
            1. Save the example as restate.py.
            2. Run it: python3 restate.py
        """,
        example = """
            sentence = "the king died"
            synonyms = {"died": ["passed away", "went to God", "was taken"]}
            variants = [sentence] + [sentence.replace("died", word) for word in synonyms["died"]]
            for number, text in enumerate(variants, start=1):
                print(number, text)
            print("variants:", len(variants))
        """,
    ),

    entry(
        databaseId = 83,
        kind = Kind.GUIDE,
        section = "Poetry",
        title = "How a court anthology lists its poems by season",
        originalTitle = "Arrangement by season",
        author = "Compilers of the court anthologies",
        year = 1216,
        summary = "A court anthology lists the seasons first, and a short program sorts five sample poems by season and then by label.",
        details = "The Shin Kokinshu opens its books with the four seasons, and this guide arranges sample poems in the same order. The labels in the example are invented for illustration, and the guide is dated to the completion of the Shin Kokinshu in 1216.",
        instructions = """
            1. Save the example as seasons.py.
            2. Run it: python3 seasons.py
        """,
        example = """
            SEASONS = ["spring", "summer", "autumn", "winter"]
            poems = [("A", "autumn"), ("B", "spring"), ("C", "winter"), ("D", "spring"), ("E", "summer")]
            ordered = sorted(poems, key=lambda poem: (SEASONS.index(poem[1]), poem[0]))
            for label, season in ordered:
                print(season, label)
            print("poems:", len(poems))
        """,
    ),

    entry(
        databaseId = 83,
        kind = Kind.GUIDE,
        section = "Writing",
        title = "How the kana of a Japanese opening sentence are counted",
        originalTitle = "Opening sentence of the Hojoki",
        author = "Kamo no Chomei",
        year = 1212,
        summary = "Each kana stands for one mora, so a short program counts the morae of the Hojoki's opening sentence by listing its syllables in order.",
        details = "The sentence opens with the river that never stays the same, and the example lists its 26 romanized syllables. The count is the same in kana and in romanization, and the guide is dated to the essay of 1212.",
        instructions = """
            1. Save the example as morae.py.
            2. Run it: python3 morae.py
        """,
        example = """
            SYLLABLES = ["yu", "ku", "ka", "wa", "no", "na", "ga", "re", "wa", "ta", "e", "zu", "shi", "te",
                         "shi", "ka", "mo", "mo", "to", "no", "mi", "zu", "ni", "a", "ra", "zu"]
            print("syllables:", len(SYLLABLES))
            print("first five:", " ".join(SYLLABLES[:5]))
            print("last one:", SYLLABLES[-1])
        """,
    ),

)
