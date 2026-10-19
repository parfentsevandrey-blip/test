package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 890 to 900: Leo VI's Taktika, a Byzantine handbook of army organization, and Abu Kamil's algebra, an Arabic book that extends al-Khwarizmi. */
val db115Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 115,
        kind = Kind.TOOL,
        section = "Byzantine",
        title = "Leo VI's Taktika, a Byzantine handbook of army organization and tactics",
        originalTitle = "Taktika",
        author = "Emperor Leo VI",
        year = 900,
        summary = "Leo VI's Taktika, a handbook of Byzantine army organization and battle tactics, is dated to about 900.",
        details = "It covers the make-up of the units, the order of march and the formations for battle, and it was written during Leo's reign from 886 to 912. Dates given for it run from about 900 to 912, and the year 900 is used here because the internal references point to about 900.",
    ),

    entry(
        databaseId = 115,
        kind = Kind.TOOL,
        section = "Arabic",
        title = "Abu Kamil's Book of Algebra, an Arabic algebra that extends al-Khwarizmi's",
        originalTitle = "Kitab fi al-jabr wa al-muqabala",
        author = "Abu Kamil Shuja ibn Aslam",
        year = 900,
        summary = "Abu Kamil's Book of Algebra, an Arabic treatise that extends the algebra of al-Khwarizmi, is dated about 900.",
        details = "Abu Kamil was an Egyptian mathematician, and his book works with irrational roots and with a wider range of problems than al-Khwarizmi's book. Later writers drew on it, and about 900 is the usual date for the book.",
        rare = true,
    ),

)
