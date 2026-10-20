package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One example of 620 to 630: the 26 years of war between Byzantium and Persia from 602 to 628, counted in code. */
val db142Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 142,
        kind = Kind.EXAMPLE,
        section = "Byzantine",
        title = "The 26 years of war between Byzantium and Persia from 602 to 628, split at Heraclius's accession in 610, counted in code",
        originalTitle = "Byzantine-Sasanian War of 602-628",
        author = "Khosrow II and Heraclius",
        year = 628,
        summary = "The war between Byzantium and the Sasanian Persians ran from 602 to 628, and a short program splits its 26 years at Heraclius's accession in 610.",
        details = "The war is usually dated from 602, when Khosrow II, who owed his throne to the emperor Maurice, went to war after Maurice was overthrown, and it ended with the peace of 628 after Heraclius's campaigns. The split at 610 uses the usual date of Heraclius's accession, so the count is only as exact as those dates.",
        rare = true,
        instructions = """
            1. Save the example as war_years.py.
            2. Run it: python3 war_years.py
        """,
        example = """
            WAR_START = 602  # the war begins after the overthrow of Maurice, as usually dated
            HERACLIUS = 610  # Heraclius's accession, as usually dated
            WAR_END = 628    # the peace of 628
            print("years of war under Phocas:", HERACLIUS - WAR_START)
            print("years of war under Heraclius:", WAR_END - HERACLIUS)
            print("total years of war:", WAR_END - WAR_START)
        """,
    ),

)
