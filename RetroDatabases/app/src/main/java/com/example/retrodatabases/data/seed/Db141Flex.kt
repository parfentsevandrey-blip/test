package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One flex piece of 630 to 640: the years from Isidore's Etymologies of about 630 to the Ekthesis decree of 638. */
val db141Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 141,
        kind = Kind.FLEX,
        section = "Years",
        title = "Years from Isidore's Etymologies of about 630 to the Ekthesis decree of 638",
        originalTitle = "Years between two texts of the 630s",
        author = "Isidore of Seville and Heraclius",
        year = 630,
        summary = "The Etymologies of about 630 and the Ekthesis of 638 are eight years apart, and a short program prints the gap.",
        details = "The two texts are unrelated, so the span only compares the dates usually given for them. The program prints the difference, which a reader can check against the two dates.",
        instructions = """
            1. Save the example as etymologies_ekthesis.py.
            2. Run it: python3 etymologies_ekthesis.py
        """,
        example = """
            ETYMOLOGIES = 630  # Isidore's Etymologies, completed about 630
            EKTHESIS = 638     # the Ekthesis of 638, as usually dated
            print("years from the Etymologies to the Ekthesis:", EKTHESIS - ETYMOLOGIES)
        """,
    ),

)
