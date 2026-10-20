package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One flex piece of 250 to 260: the years from Decius's edict to Valerian's capture at Edessa. */
val db179Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 179,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Years from Decius's edict of 250 to Valerian's capture in 260",
        originalTitle = "Years between two events",
        author = "No single author",
        year = 260,
        summary = "Decius's edict of sacrifice is dated 250 and Valerian's capture at Edessa 260, and a short program counts the ten years between them.",
        details = "Both years are the commonly given ones, and the count is a plain subtraction, so it tests arithmetic rather than the events themselves. The edict is dated to 250 in the usual accounts, and the capture to 260.",
        instructions = """
            1. Save the example as decius_valerian.py.
            2. Run it: python3 decius_valerian.py
        """,
        example = """
            decius_edict = 250         # Decius's edict of sacrifice, as commonly dated
            valerian_capture = 260     # Valerian taken at Edessa, as commonly dated
            print("years from Decius's edict to Valerian's capture:", valerian_capture - decius_edict)
        """,
    ),

)
