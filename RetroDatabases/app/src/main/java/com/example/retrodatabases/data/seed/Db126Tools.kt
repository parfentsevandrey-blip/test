package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tool of 780 to 790: Paul the Deacon's Homiliary, a book of sermons for the church year, placed at 789 because its making is not dated. */
val db126Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 126,
        kind = Kind.TOOL,
        section = "Frankish",
        title = "Paul the Deacon's Homiliary, a book of sermons for the church year, compiled at Charlemagne's request",
        originalTitle = "Homiliarius",
        author = "Paul the Deacon",
        year = 789,
        summary = "Paul the Deacon's Homiliary, a book of sermons for the church year, was compiled at Charlemagne's request, and one account places its completion near 797.",
        details = "The sources do not date the making of this book, so 789, the year of Charlemagne's Admonitio generalis, is an anchor and not the year of the book itself. One account places its completion near 797, and the sermons are drawn largely from Bede and the Latin Fathers. Earlier writers thought priests read them aloud to the laity, a view that is still contested.",
        rare = true,
    ),

)
