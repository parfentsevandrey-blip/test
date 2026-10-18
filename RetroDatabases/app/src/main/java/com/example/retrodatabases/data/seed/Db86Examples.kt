package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1185: an eight-knight knockout bracket counted round by round. */
val db86Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 86,
        kind = Kind.EXAMPLE,
        section = "Tournaments",
        title = "How many bouts an eight-knight knockout tournament needs",
        originalTitle = "Knockout tournament",
        author = "Knights of the tournament circuit",
        year = 1185,
        summary = "An eight-knight knockout tournament needs seven bouts in three rounds, and a short program counts the bouts round by round for an illustrative bracket of eight.",
        details = "Tournaments of the 1180s were contests between knights, and a knockout bracket is one way to set out their pairings. The bracket in the example is illustrative and names no real knights, so the printed counts check the arithmetic rather than record any event.",
        instructions = """
            1. Save the example as knockout.py.
            2. Run it: python3 knockout.py
        """,
        example = """
            players = 8
            round_no = 1
            total = 0
            while players > 1:
                bouts = players // 2
                total += bouts
                print("round", round_no, "bouts", bouts)
                players = bouts
                round_no += 1
            print("total bouts", total)
        """,
    ),

)
