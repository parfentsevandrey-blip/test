package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One piece of flex arithmetic of 850 to 860: the years from al-Mutawakkil's decree of 850 to the death of Ibn Hanbal in 855. */
val db119Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 119,
        kind = Kind.FLEX,
        section = "Years",
        title = "Years from al-Mutawakkil's decree of 850 to the death of Ahmad ibn Hanbal",
        originalTitle = "Years between two dated events",
        author = "Al-Mutawakkil and Ahmad ibn Hanbal",
        year = 850,
        summary = "The decree of 850 and the death of Ahmad ibn Hanbal in 855 are five years apart, and a short program prints that span.",
        details = "The decree is the first date and the year of this record, and the death of Ibn Hanbal in 855 is the second. Ibn Hanbal was one of the scholars punished for refusing the inquisition, so the two dates are linked by the same affair.",
        instructions = """
            1. Save the example as decree_years.py.
            2. Run it: python3 decree_years.py
        """,
        example = """
            DECREE = 850          # al-Mutawakkil's decree on the dress of non-Muslim subjects
            HANBAL_DEATH = 855    # the death of Ahmad ibn Hanbal, as commonly given
            print("years from the decree to the death of Ibn Hanbal:", HANBAL_DEATH - DECREE)
        """,
    ),

)
