package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One guide of 600 to 610: how a syllable is placed in the entering tone of the Qieyun, worked out by a short program. */
val db144Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 144,
        kind = Kind.GUIDE,
        section = "Chinese",
        title = "How a syllable is placed in the entering tone when its final sound is a stop, p, t or k",
        originalTitle = "ru sheng (entering tone)",
        author = "Compilers of the Qieyun",
        year = 601,
        summary = "A syllable whose final sound is a stop, written here as p, t or k, falls in the entering tone, one of the four tone classes of the Qieyun, and a short program can sort it.",
        details = "The Qieyun of 601 divides its syllables into four tones, and the entering tone is marked by a final stop, so 601 is the year of the dictionary that applied the rule, not the date of the rule itself. The example uses Latin stand-in syllables, not the readings of any real character.",
        rare = true,
        instructions = """
            1. Save the example as entering_tone.py.
            2. Run it: python3 entering_tone.py
        """,
        example = """
            # Stand-in syllables, not real readings: a final p, t or k places a syllable in the entering tone.
            SYLLABLES = ["dap", "sin", "kuk", "tang", "mit", "ung"]
            STOPS = ("p", "t", "k")

            entering = [s for s in SYLLABLES if s.endswith(STOPS)]
            others = [s for s in SYLLABLES if not s.endswith(STOPS)]

            print("entering tone:", " ".join(entering))
            print("other tones:", " ".join(others))
            print("counts:", len(entering), len(others))
        """,
    ),

)
