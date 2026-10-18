package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 960 to 970: a Hebrew dictionary of Cordoba and a Persian history made from al-Tabari's annals. */
val db108Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 108,
        kind = Kind.LANGUAGE,
        section = "Spain",
        title = "Menahem ibn Saruq's Mahberet, a Hebrew dictionary compiled at Cordoba for Hasdai ibn Shaprut",
        originalTitle = "Mahberet",
        author = "Menahem ibn Saruq",
        year = 960,
        summary = "Menahem ibn Saruq compiled the Mahberet, a Hebrew dictionary of the words of the Bible, at Cordoba for the courtier Hasdai ibn Shaprut, in about 960.",
        details = "The work is dated only to the middle of the tenth century, so 960 is an approximate year used here and not a documented one. Dunash ibn Labrat answered the dictionary in verse with a critique of about 200 points, written in the second half of the tenth century and printed in London in 1855 as the Teshuvot Dunash. Menahem's rebuttal came back with equal sharpness, and the quarrel spread into the circles of the two men's students.",
        rare = true,
    ),

    entry(
        databaseId = 108,
        kind = Kind.LANGUAGE,
        section = "Persia",
        title = "Bal'ami's Tarikhnama, a Persian history made from al-Tabari's annals for the Samanid court",
        originalTitle = "Tarikhnama",
        author = "Abu Ali Bal'ami",
        year = 963,
        summary = "The vizier Abu Ali Bal'ami began a Persian history from al-Tabari's Arabic annals for the Samanid ruler Mansur ibn Nuh, and the commission is dated to 963.",
        details = "The order passed through the court official al-Fa'iq, and the year 963 marks when the work began or the year the book is said to have been written, not a completion date. The Tarikhnama is often called one of the earliest surviving prose works in New Persian. A. C. S. Peacock's analysis argues that Bal'ami reworked al-Tabari so heavily that the result is nearly a new book rather than a translation.",
        rare = true,
    ),

)
