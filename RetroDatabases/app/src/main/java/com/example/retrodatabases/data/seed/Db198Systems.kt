package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 60 to 69: Joshua ben Gamla's town schools, placed at the year of his high priesthood. */
val db198Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 198,
        kind = Kind.SYSTEM,
        section = "Schools",
        title = "Joshua ben Gamla's schools, teachers set in every town to teach boys the Law",
        originalTitle = "Schools of Joshua ben Gamla",
        author = "Joshua ben Gamla, high priest",
        year = 64,
        summary = "The Talmud credits the high priest Joshua ben Gamla with setting up teachers in every district and town so that boys could be taught the Law.",
        details = "The account in Bava Batra 21a is undated, and some scholars doubt that he founded the schools, since Josephus, who records a high priest of a similar name, says nothing about schools. Reference works date his high priesthood to about 64 CE, so 64 is the year of the anchor, not the year the schools began. The sources do not date this system, so 64 is the placed year and not the year of the making.",
        rare = true,
    ),

)
