package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 110 to 120: the figures on the spiral frieze of Trajan's Column, counted from the stated floor. */
val db193Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 193,
        kind = Kind.EXAMPLE,
        section = "Columns",
        title = "Figures per turn of Trajan's Column frieze, counted from the stated floor",
        originalTitle = "Columna Traiana",
        author = "Sculptors of Trajan's Column",
        year = 113,
        summary = "Trajan's Column carries a spiral frieze of 23 turns and more than 2,500 figures, and a short program counts the figures on each turn from that floor.",
        details = "The column was completed in 113, although some accounts date its work to about 107 to 113, and the figure count is disputed: accounts say more than 2,500 figures, and some give 2,662. The example uses 2,500 as a floor, so the 108 figures per turn it prints is a lower bound and not an exact count.",
        instructions = """
            1. Save the example as column_turns.py.
            2. Run it: python3 column_turns.py
        """,
        example = """
            TURNS = 23          # turns of the spiral, as the accounts give them
            FIGURES = 2500      # the stated floor: more than 2,500 figures
            per_turn, left = divmod(FIGURES, TURNS)
            print("turns:", TURNS)
            print("figures counted:", FIGURES)
            print("at least per turn:", per_turn)
            print("left over:", left)
        """,
    ),

)
