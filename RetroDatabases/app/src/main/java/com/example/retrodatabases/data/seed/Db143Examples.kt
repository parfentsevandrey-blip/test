package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One example of 610 to 620: the 28 chapters of the Lotus Sutra and the 14 of the Vimalakirti Sutra, counted in code. */
val db143Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 143,
        kind = Kind.EXAMPLE,
        section = "Buddhist",
        title = "The 28 chapters of the Lotus Sutra and the 14 of the Vimalakirti Sutra, counted in code",
        originalTitle = "Miaofa lianhua jing",
        author = "Kumarajiva",
        year = 615,
        summary = "A short program counts the 28 chapters of the Lotus Sutra and the 14 of the Vimalakirti Sutra, the two texts that tradition ties to Prince Shotoku's commentaries.",
        details = "Kumarajiva's Chinese translations of both sutras have these chapter counts, and the program adds them to 42 and names the longer text. The year 615 is the traditional date of the Hokke gisho, the commentary on the Lotus, and the count itself does not depend on that date.",
        instructions = """
            1. Save the example as sutra_chapters.py.
            2. Run it: python3 sutra_chapters.py
        """,
        example = """
            LOTUS = 28        # chapters of the Lotus Sutra, Kumarajiva's translation
            VIMALAKIRTI = 14  # chapters of the Vimalakirti Sutra, Kumarajiva's translation
            print("lotus chapters:", LOTUS)
            print("vimalakirti chapters:", VIMALAKIRTI)
            print("chapters of the two sutras:", LOTUS + VIMALAKIRTI)
            print("longer of the two:", "Lotus" if LOTUS > VIMALAKIRTI else "Vimalakirti")
        """,
    ),

)
