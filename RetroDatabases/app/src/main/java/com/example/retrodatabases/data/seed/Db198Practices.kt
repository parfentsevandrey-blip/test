package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 60 to 69: the counting of the omer, placed at the year the Temple was destroyed. */
val db198Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 198,
        kind = Kind.PRACTICE,
        section = "Calendar",
        title = "Counting the omer, forty-nine days from Passover to the feast of weeks",
        originalTitle = "Sefirat ha-omer",
        author = "The priestly law of Leviticus",
        year = 70,
        summary = "Leviticus has the Israelites count seven full weeks from the day after the Passover sabbath to the feast of weeks, forty-nine days in all.",
        details = "The count began with the barley sheaf of the omer, which was waved in the Temple, and the sources do not date the custom itself. The Romans destroyed the Temple in 70 CE, which ended that offering, so 70 is the year of this anchor and not the year of the custom, and the count went on after it. The year is placed for that reason alone.",
        rare = true,
    ),

)
