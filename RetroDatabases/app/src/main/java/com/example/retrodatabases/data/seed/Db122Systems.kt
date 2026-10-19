package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One system of 820 to 830: Lothair's school ordinance from the assembly at Olona in May 825, which set up teaching centres in Italy. */
val db122Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 122,
        kind = Kind.SYSTEM,
        section = "Italy",
        title = "Lothair's school ordinance from the assembly at Olona, which set up a network of teaching centres in Italy",
        originalTitle = "Constitutio Olonnensis",
        author = "Lothair I, king of Italy, and the assembly at Olona",
        year = 825,
        summary = "Lothair's school ordinance, issued at an assembly at Olona near Pavia in May 825, set up a network of schools in the Italian kingdom with teachers at fixed centres.",
        details = "The ordinance sent pupils from the lower schools on to designated higher centres, and it made advanced teaching the duty of appointed masters at fixed sites, so that distance and poverty could not serve as excuses. How far it was carried out is hard to verify, and the sources do not show whether the centres worked as planned.",
        rare = true,
    ),

)
