package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One practice of 610 to 620: the paper and ink skills that the Nihon Shoki credits to the Goguryeo monk Docho in 610. */
val db143Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 143,
        kind = Kind.PRACTICE,
        section = "Japanese",
        title = "Paper and ink skills credited to the Goguryeo monk Docho in Japan in 610, in the Nihon Shoki's entry for that year",
        originalTitle = "kami to sumi",
        author = "Docho",
        year = 610,
        summary = "The Nihon Shoki's entry for 610 credits the Goguryeo monk Docho with skill in making paper and ink in Japan.",
        details = "The entry also lists his learning and his work with pigments, and 610 is the year the chronicle gives for it. Writers differ on whether the entry shows that he brought papermaking to Japan or only praises his craftsmanship, so the record does not claim that he was the first papermaker there.",
        rare = true,
    ),

)
