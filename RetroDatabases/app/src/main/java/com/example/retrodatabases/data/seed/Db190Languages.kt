package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 140 to 150: Ptolemy's Harmonics, a Greek treatise on musical intervals as ratios. */
val db190Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 190,
        kind = Kind.LANGUAGE,
        section = "Greek",
        title = "Ptolemy's Harmonics, a Greek treatise on musical intervals as ratios",
        originalTitle = "Harmonika",
        author = "Claudius Ptolemy",
        year = 140,
        summary = "Ptolemy's Harmonics is a Greek treatise in three books on musical intervals, which it tests by whole-number ratios against the judgement of the ear.",
        details = "Early editions, including a Venice printing of 1562 and John Wallis's edition of 1682, present the work as three books. Its third book turns harmony toward astrology and sets out Ptolemy's own version of the harmony of the spheres. The treatise carries no fixed date of composition, so 140 is a round year inside the second century, not a recorded date.",
        rare = true,
    ),

)
