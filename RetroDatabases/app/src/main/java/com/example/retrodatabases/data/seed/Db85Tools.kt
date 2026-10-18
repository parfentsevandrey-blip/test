package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1190 to 1193: al-Bitruji's critique of Ptolemy's planetary models, Bernard of Pavia's handbook of papal letters and the Suzhou stone star chart. */
val db85Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 85,
        kind = Kind.TOOL,
        section = "Astronomy",
        title = "Al-Bitruji's On the Principles of Astronomy, an Arabic critique of Ptolemy",
        originalTitle = "Kitab fi l-hay'a",
        author = "Abu Ishaq al-Bitruji",
        year = 1190,
        summary = "Al-Bitruji's Arabic book on the principles of astronomy rejected Ptolemy's epicycles and was composed in the years around 1190.",
        details = "A Latin version by Michael Scot, under the title De motibus celorum, is usually dated to about 1217. Sources give the author's active years as about 1185 to 1192, so the composition date is bracketed rather than fixed, and 1190 is a middle date.",
        rare = true,
    ),

    entry(
        databaseId = 85,
        kind = Kind.TOOL,
        section = "Canon law",
        title = "Bernard of Pavia's Breviarium of papal letters, a handbook for lawyers",
        originalTitle = "Breviarium extravagantium",
        author = "Bernard of Pavia",
        year = 1191,
        summary = "Bernard of Pavia compiled his Breviarium extravagantium about 1191, a handbook of papal letters sorted by subject for lawyers and teachers.",
        details = "The collection is also called the Compilatio prima, and it is one of the first collections of papal letters arranged for use in schools and courts. The usual date is 1191.",
        rare = true,
    ),

    entry(
        databaseId = 85,
        kind = Kind.TOOL,
        section = "Astronomy",
        title = "The Suzhou stone star chart, carved at Suzhou in 1193",
        originalTitle = "Suzhou tianwen tu",
        author = "Song astronomers of Suzhou",
        year = 1193,
        summary = "The Suzhou star chart, cut into stone in 1193, is one of the oldest surviving star charts cut in stone and shows the stars of the northern sky.",
        details = "The chart survives as a Song dynasty stone carving at Suzhou, and its date of 1193 is the one usually given for it. Stone was a more durable medium than the paper charts of the period, of which few survive.",
        rare = true,
    ),

)
