package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 790 to 800: the Libri Carolini on images, about 790, and the first paper mill in Baghdad, about 794. */
val db125Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 125,
        kind = Kind.PRACTICE,
        section = "Frankish",
        title = "The Libri Carolini, a Frankish treatise against the image decrees of the Second Council of Nicaea, written in Charlemagne's name about 790",
        originalTitle = "Opus Caroli regis contra synodum",
        author = "Unnamed court theologian, writing in Charlemagne's name",
        year = 790,
        summary = "The Libri Carolini, a treatise written in Charlemagne's name against the image decrees of the Second Council of Nicaea, dates from about 790.",
        details = "The author is not named in the sources, though Theodulf of Orleans is often proposed, and accounts date its writing between 790 and 792. It argues against the worship of images while answering the Greek case for them, and its position matches that of the Frankfurt council of 794.",
        rare = true,
    ),

    entry(
        databaseId = 125,
        kind = Kind.PRACTICE,
        section = "Abbasid",
        title = "The first paper mill in Baghdad, set up about 794 under the Barmakid viziers",
        originalTitle = "First paper mill in Baghdad",
        author = "Ja'far ibn Yahya al-Barmaki (attributed)",
        year = 794,
        summary = "The first paper mill in Baghdad is dated to about 794, when the Barmakid viziers of Harun al-Rashid set up a works for making paper.",
        details = "Accounts credit Ja'far ibn Yahya with the mill, but some name his brother al-Fadl ibn Yahya, and the date is given as 794 or 795 in different accounts. Writers of the period seem to have called the works a manufactory rather than a mill, so whether water power drove it is also disputed.",
        rare = true,
    ),

)
