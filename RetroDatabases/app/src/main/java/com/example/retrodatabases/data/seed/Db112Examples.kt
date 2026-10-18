package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 929: the years of Abd al-Rahman III counted as emir and as caliph, computed in code. */
val db112Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 112,
        kind = Kind.EXAMPLE,
        section = "Spain",
        title = "The years of Abd al-Rahman III in power, counted as emir and as caliph",
        originalTitle = "Abd al-Rahman III al-Nasir li-Din Allah",
        author = "Abd al-Rahman III of Cordoba",
        year = 929,
        summary = "A short program counts the years of Abd al-Rahman III as emir, 17 from 912 to 929, and as caliph, 32 from 929 to 961, which makes 49 years in power.",
        details = "Abd al-Rahman III succeeded as emir of Cordoba in 912, took the title of caliph in January 929, and died in 961, so his reign is usually given as 49 years. The program subtracts the dates of each title, which is the arithmetic a reader of a chronicle would do by hand.",
        instructions = """
            1. Save the example as abd_rahman.py.
            2. Run it: python3 abd_rahman.py
        """,
        example = """
            emir_from = 912
            caliph_from = 929
            died = 961

            years_as_emir = caliph_from - emir_from
            years_as_caliph = died - caliph_from
            print("years as emir:", years_as_emir)
            print("years as caliph:", years_as_caliph)
            print("years in power:", died - emir_from)
            print("check:", years_as_emir + years_as_caliph == died - emir_from)
        """,
    ),

)
