package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 980 to 990: Ibn al-Nadim's Fihrist, a catalogue of books compiled in Baghdad. */
val db106Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 106,
        kind = Kind.TOOL,
        section = "Baghdad",
        title = "Ibn al-Nadim's Fihrist, a bookseller's catalogue of books in Arabic and other tongues",
        originalTitle = "Kitab al-Fihrist",
        author = "Ibn al-Nadim",
        year = 987,
        summary = "Ibn al-Nadim's Kitab al-Fihrist, a catalogue of books in Arabic and other languages, was compiled in Baghdad during 987 and 988.",
        details = "Ibn al-Nadim worked as a bookseller, and his catalogue lists authors and their books by subject in ten sections, six on Islamic writings and four on non-Islamic works and translations. Its compilation is placed in 987 and 988, and it is an important source for books of earlier Arabic and Persian writers that have since been lost.",
    ),

)
