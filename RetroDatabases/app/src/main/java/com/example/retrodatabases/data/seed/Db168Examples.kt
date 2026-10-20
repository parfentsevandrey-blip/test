package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 360 to 370: the years of Antony's life, counted from the usual dates of his birth and death. */
val db168Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 168,
        kind = Kind.EXAMPLE,
        section = "Lives",
        title = "The years of Antony's life from 251 to 356, counted in code",
        originalTitle = "Years of life",
        author = "Athanasius and later chroniclers",
        year = 360,
        summary = "The usual dates of Antony's life, 251 for his birth and 356 for his death, give 105 years, the age the Life gives for him, and a short program counts them.",
        details = "The birth year 251 and the death year 356 are the usual dates, and the count is an exact subtraction of those dates. The program prints both dates, the years lived, and a check that the count agrees with the subtraction.",
        instructions = """
            1. Save the example as antony_years.py.
            2. Run it: python3 antony_years.py
        """,
        example = """
            # Antony of Egypt: the usual dates are about 251 for his birth and 356 for his death.
            birth = 251
            death = 356
            years_lived = len(range(birth, death))
            print("birth year:", birth)
            print("death year:", death)
            print("years lived:", years_lived)
            print("same as subtraction:", years_lived == death - birth)
        """,
    ),

)
