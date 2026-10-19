package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One example of 790 to 800: the 52 years between the first and last dated letters of the Codex Carolinus, counted in code. */
val db125Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 125,
        kind = Kind.EXAMPLE,
        section = "Latin",
        title = "The 52 years between the first and last dated letters of the Codex Carolinus, 739 to 791, counted in code",
        originalTitle = "Codex epistolaris Carolinus",
        author = "Six popes, Hadrian I among them",
        year = 791,
        summary = "The Codex Carolinus gathers 99 letters from popes, dated from 739 to 791, and a short program counts the 52 years between its first and last dated letters.",
        details = "Two items at the end of the collection come from the antipope Constantine, and Charlemagne's directive of 791 launched the copying. The one surviving medieval copy is a late ninth-century product now in Vienna, and the program prints the span both as a difference and as an inclusive count.",
        instructions = """
            1. Save the example as codex_span.py.
            2. Run it: python3 codex_span.py
        """,
        example = """
            FIRST_LETTER = 739  # the earliest dated letter in the collection
            LAST_LETTER = 791   # the latest dated letter, the year of the copying directive
            print("years between the first and last letters:", LAST_LETTER - FIRST_LETTER)
            print("years counted inclusively:", LAST_LETTER - FIRST_LETTER + 1)
        """,
        rare = true,
    ),

)
