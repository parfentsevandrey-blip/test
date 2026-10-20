package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 40 to 50: Claudius's census of the Roman citizens, a count of citizens kept in lists by the censors. */
val db200Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 200,
        kind = Kind.SYSTEM,
        section = "Census",
        title = "Claudius's census of the Roman citizens, begun in 47",
        originalTitle = "Census of Claudius",
        author = "The censors of Claudius's reign",
        year = 47,
        summary = "Claudius's census counted the Roman citizens in lists kept by the censors, and accounts give its start as 47 or 48.",
        details = "This record uses 47, the year that some accounts give for the start, while others place the census in 48. The total usually given is 5,984,072 citizens, but the figures have come down with possible copying errors, and accounts differ on whether the count reached all citizens or only adult men.",
        rare = true,
    ),

)
