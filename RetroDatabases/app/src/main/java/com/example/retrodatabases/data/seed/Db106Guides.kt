package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 984: a fixed ratio between two media, written out as code in the sine form used today. */
val db106Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 106,
        kind = Kind.GUIDE,
        section = "Baghdad",
        title = "How a fixed ratio sets the bend of a ray at a surface, in the sine form used today",
        originalTitle = "How a fixed ratio sets the bend of a ray at a surface, in the sine form used today",
        author = "Ibn Sahl",
        year = 984,
        summary = "Today's sine form of the refraction rule fixes the bent ray from the incoming ray and a fixed ratio, and Ibn Sahl's treatise of about 984 is named as an early source of it.",
        details = "Historians disagree about how far Ibn Sahl's geometry anticipates this form, since his statement is geometric and does not use sines, so the sine version here is a modern restatement. The example uses exact fractions, so the answer is a fraction and not a rounded decimal.",
        instructions = """
            1. Save the example as refraction.py.
            2. Run it: python3 refraction.py
        """,
        example = """
            from fractions import Fraction
            ratio = Fraction(3, 2)
            sin_incidence = Fraction(3, 5)
            sin_refraction = sin_incidence / ratio
            print("sine of incidence:", sin_incidence)
            print("ratio:", ratio)
            print("sine of refraction:", sin_refraction)
        """,
        rare = true,
    ),

)
