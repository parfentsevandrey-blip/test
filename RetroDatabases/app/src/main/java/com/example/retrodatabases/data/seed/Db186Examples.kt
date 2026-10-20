package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 180 to 190: the smallest and largest totals of the thirty-six sections of the Yellow Turban rising, counted in code. */
val db186Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 186,
        kind = Kind.EXAMPLE,
        section = "Sums",
        title = "The smallest and largest totals of the thirty-six Yellow Turban sections",
        originalTitle = "Section totals",
        author = "The Yellow Turban leaders",
        year = 184,
        summary = "Thirty-six sections of about six thousand to ten thousand followers each give totals of 216000 and 360000, and a short program prints the two bounds.",
        details = "One account gives the sections as running from roughly six or seven thousand people to about ten thousand, and the example uses 6000 and 10000 as round ends of that range. The totals are bounds on the same arithmetic, not a count of the real followers.",
        instructions = """
            1. Save the example as section_totals.py.
            2. Run it: python3 section_totals.py
        """,
        example = """
            SECTIONS = 36
            SMALLEST = 6000     # the small end of the reported section sizes
            LARGEST = 10000     # the large end of the reported section sizes
            low = SECTIONS * SMALLEST
            high = SECTIONS * LARGEST
            print("smallest total:", low)
            print("largest total:", high)
            print("spread:", high - low)
        """,
    ),

)
