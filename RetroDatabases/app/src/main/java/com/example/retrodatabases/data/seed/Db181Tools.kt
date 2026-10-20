package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 230 to 240: Origen's Hexapla, six columns of the Old Testament compiled at Caesarea. */
val db181Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 181,
        kind = Kind.TOOL,
        section = "Scripture",
        title = "Origen's Hexapla, the Old Testament in six columns of Hebrew and Greek texts",
        originalTitle = "Hexapla",
        author = "Origen",
        year = 240,
        summary = "Origen's Hexapla sets the Hebrew Old Testament beside four Greek translations in six columns, a reference tool compiled at Caesarea.",
        details = "The sources place the compilation at Caesarea, finished before 245, and say that it took roughly twenty years, but they give no start year, so 240 is the date used here. Only scraps survive, and the four Greek versions in its columns are those of Aquila, Symmachus, the Septuagint and Theodotion.",
        rare = true,
    ),

)
