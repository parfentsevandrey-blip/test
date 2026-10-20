package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 140 to 150: Marcion's canon of scriptures, a short list of a revised Luke and ten letters of Paul. */
val db190Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 190,
        kind = Kind.SYSTEM,
        section = "Scripture",
        title = "Marcion's canon of scriptures, a list that kept a revised Luke and ten letters of Paul",
        originalTitle = "Marcion's canon",
        author = "Marcion of Sinope",
        year = 140,
        summary = "Marcion's canon of scriptures was a short list of a revised Gospel of Luke and ten letters of Paul, and it left out the Hebrew scriptures.",
        details = "Marcion reached Rome about 140, and the Roman congregation expelled him in 144, though some accounts put his move in the late 130s. Early critics report that his list had a revised Gospel of Luke and ten letters of Paul, without the Pastoral letters and without the Hebrew scriptures. Whether his Gospel is an edited Luke or an earlier text is disputed, and his list is often credited with speeding up the church's own canon.",
        rare = true,
    ),

)
