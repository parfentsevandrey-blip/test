package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One flex piece of 540 to 550: the five years from the plague of Justinian in 541 to Totila's entry into Rome in 546. */
val db150Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 150,
        kind = Kind.FLEX,
        section = "Years",
        title = "Years from the plague of Justinian in 541 to Totila's entry into Rome in 546",
        originalTitle = "Years between two events in the Wars of Procopius",
        author = "Procopius of Caesarea",
        year = 541,
        summary = "The plague that reached Pelusium in 541 and Totila's entry into Rome in 546 are five years apart, and a short program prints the gap.",
        details = "Both dates come from the history of Procopius, which covers the plague and the Gothic war, so the gap compares two entries of one account and does not show that the events were linked. The program only subtracts the two years, which a reader can check against the two entries.",
        rare = true,
        instructions = """
            1. Save the example as plague_rome.py.
            2. Run it: python3 plague_rome.py
        """,
        example = """
            PLAGUE = 541  # the plague reaches Pelusium, as Procopius dates it
            TOTILA = 546  # Totila enters Rome in December 546
            print("years from the plague of 541 to Totila's entry into Rome in 546:", TOTILA - PLAGUE)
        """,
    ),

)
