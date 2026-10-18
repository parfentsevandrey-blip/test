package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1079: a list of Japanese words sorted by the order of the iroha poem. */
val db97Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 97,
        kind = Kind.PROJECT,
        section = "Japan",
        title = "Sorting ten Japanese words by the order of the iroha poem",
        originalTitle = "Iroha uta",
        author = "Anonymous, once credited to Kukai",
        year = 1079,
        summary = "A program sorts ten Japanese words by the order of the iroha poem, which uses each kana sign once and ordered kana until the gojuon table took over.",
        details = "The earliest surviving record of the poem is dated 1079, and the poem runs through the 47 kana signs, each used once. The program takes each word's first syllable, finds its place in the poem's order and sorts the words by that place.",
        instructions = """
            1. Save the example as iroha_sort.py.
            2. Run it: python3 iroha_sort.py
        """,
        example = """
            iroha = ["i", "ro", "ha", "ni", "ho", "he", "to", "chi", "ri", "nu", "ru", "wo", "wa", "ka", "yo", "ta",
                     "re", "so", "tsu", "ne", "na", "ra", "mu", "u", "wi", "no", "o", "ku", "ya", "ma", "ke", "fu",
                     "ko", "e", "te", "a", "sa", "ki", "yu", "me", "mi", "shi", "we", "hi", "mo", "se", "su"]

            def first_syllable(word):
                for size in (3, 2, 1):
                    if word[:size] in iroha:
                        return word[:size]
                raise ValueError(word)

            words = ["mizu", "inu", "yama", "neko", "tsuki", "sora", "umi", "tori", "hana", "kaze"]
            ordered = sorted(words, key=lambda w: iroha.index(first_syllable(w)))
            print("iroha positions:", " ".join(str(iroha.index(first_syllable(w)) + 1) for w in ordered))
            print("sorted:", " ".join(ordered))
            print("kana signs in the poem:", len(iroha))
        """,
    ),

)
