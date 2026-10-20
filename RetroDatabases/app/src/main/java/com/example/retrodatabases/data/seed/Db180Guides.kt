package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** A guide of 248: the Roman 110-year interval between Secular Games set against the dated games, placed at 248 by Philip's games. */
val db180Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 180,
        kind = Kind.GUIDE,
        section = "Festivals",
        title = "How the 110-year interval of the Secular Games is checked against the dated games",
        originalTitle = "Saeculum",
        author = "Roman priests and magistrates",
        year = 248,
        summary = "The Secular Games were meant to fall every 110 years, and a short program sets the dated games of 88, 204 and 248 against that interval.",
        details = "The 110-year interval is the one that the games of 17 BC were held to follow, and the dated games do not keep it: the gaps are 116 years from 88 to 204 and 44 years from 204 to 248. The year 248 is the anchor, the games of Philip the Arab, which places this method in the decade; it is not the year the rule began.",
        instructions = """
            1. Save the example as saeculum.py.
            2. Run it: python3 saeculum.py
        """,
        example = """
            GAMES = [88, 204, 248]   # years of the Secular Games under Domitian, Severus and Philip, as commonly given
            SAECULUM = 110           # the interval of 110 years that the rule sets
            for previous, current in zip(GAMES, GAMES[1:]):
                print("gap from", previous, "to", current, "is", current - previous, "years")
            for previous, current in zip(GAMES, GAMES[1:]):
                due = previous + SAECULUM
                print("after", previous, "the rule gives", due, "and the games came in", current, "so the difference is", current - due)
        """,
        rare = true,
    ),

)
