package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One example of 720 to 730: the latitude span covered by Yi Xing's survey of 724, and the average gap between thirteen sites. */
val db132Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 132,
        kind = Kind.EXAMPLE,
        section = "Survey",
        title = "The latitude span of Yi Xing's survey, from about 17 to about 50 degrees north, and the average gap between thirteen sites",
        originalTitle = "Latitude span of the survey",
        author = "Yi Xing and the Tang observers",
        year = 724,
        summary = "The survey's two ends lie about 33 degrees of latitude apart, and a short program divides that span by the gaps between thirteen sites, giving an average gap of 2.75 degrees.",
        details = "The southern end is Jiaozhou, near 17 degrees north, and the northern end lies south of Lake Baikal, near 50 degrees north, so the figures are rounded. Thirteen sites is the count in one description, and another account names twenty gnomons, so the average shows the arithmetic rather than a settled figure.",
        rare = true,
        instructions = """
            1. Save the example as survey_span.py.
            2. Run it: python3 survey_span.py
        """,
        example = """
            SOUTH_END = 17   # Jiaozhou, about 17 degrees north
            NORTH_END = 50   # south of Lake Baikal, about 50 degrees north
            SITES = 13       # the count given in one description

            span = NORTH_END - SOUTH_END
            gaps = SITES - 1
            print("latitude span of the survey:", span, "degrees")
            print("gaps between the sites:", gaps)
            print("average gap:", span / gaps, "degrees")
        """,
    ),

)
