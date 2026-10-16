package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1440 to 1450: Haarlem type, a Korean rain gauge, Mons Meg, a balance, a diving helmet, a tarot pack, a pilgrim mirror and Joseon standard rulers. */
val db60Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 60,
        kind = Kind.DEVICE,
        section = "Printing",
        title = "Coster's movable type at Haarlem",
        originalTitle = "Coster's movable type at Haarlem",
        author = "Laurens Janszoon Coster",
        year = 1440,
        summary = "The Haarlem tradition credits Laurens Coster with movable type about 1440, a claim that rests on a later account and is still disputed.",
        details = "Ulrich Zell's account, printed in the Cologne Chronicle of 1499, gives Coster the invention in 1440 and says Gutenberg took his type in 1442. Hadrianus Junius repeated the Haarlem claim later, and sources still give Coster's own dates in different ways.",
    ),

    entry(
        databaseId = 60,
        kind = Kind.DEVICE,
        section = "Measurement",
        title = "The Korean rain gauge, the cheugugi",
        originalTitle = "Cheugugi",
        author = "Sejong's court",
        year = 1441,
        summary = "Sejong's court in Korea used a standard rain gauge from 1441, a vessel that measured the depth of rain so that readings could be compared from place to place.",
        details = "Sources date the cheugugi to 1441, while others date its standard design and network of stations to 1442, so accounts differ between the two years. A surviving example from Gongju is dated 1837, much later than the gauge's first use.",
    ),

    entry(
        databaseId = 60,
        kind = Kind.DEVICE,
        section = "Ordnance",
        title = "Mons Meg, the iron bombard of Mons",
        originalTitle = "Mons Meg",
        author = "Makers of Mons for Philip the Good",
        year = 1449,
        summary = "Mons Meg, a great bombard built of iron bars and hoops at Mons in 1449, was later given to the king of Scots and is still kept in Edinburgh.",
        details = "The gun was made for Philip the Good of Burgundy and passed to James II of Scotland in 1457, as the usual accounts give it. It is not a coding device; it is listed because the decade's devices include engines of war, and its history is well documented.",
    ),

    entry(
        databaseId = 60,
        kind = Kind.DEVICE,
        section = "Weighing",
        title = "Cusanus's balance experiments, the De staticis of 1450",
        originalTitle = "Idiota de staticis experimentis",
        author = "Nicholas of Cusa",
        year = 1450,
        summary = "Nicholas of Cusa's Idiota de staticis experimentis of 1450 treated the balance as an instrument for experiments, weighing things to test claims about them.",
        details = "The book is a dialogue in which a layman, not a scholar, explains how weighing can show the nature of things. Accounts differ on how new its experiments were, and this record gives the title and year without listing its particular results.",
        rare = true,
    ),

    entry(
        databaseId = 60,
        kind = Kind.DEVICE,
        section = "Machines",
        title = "Taccola's diving helmet in De ingeneis",
        originalTitle = "De ingeneis",
        author = "Mariano di Jacopo, called Taccola",
        year = 1449,
        summary = "Taccola's De ingeneis, drawn in Siena, includes a helmet with a breathing tube, drawn for soldiers who wanted to cross a stream unseen.",
        details = "The book was finished in 1433, but its drawings and notes were revised until about 1449, so 1449 is used here for the helmet's final form. One catalogue dates the Munich volume to the range 1419 to about 1450.",
        rare = true,
    ),

    entry(
        databaseId = 60,
        kind = Kind.DEVICE,
        section = "Cards",
        title = "The Visconti-Sforza tarot cards of Milan",
        originalTitle = "Tarocchi",
        author = "Painters at the Milanese court",
        year = 1450,
        summary = "The Visconti-Sforza tarot cards, painted for a Milanese court in the mid-1400s, are among the oldest surviving packs of tarot cards.",
        details = "The record dates the set to about 1450, and some cards of the pack are lost. It is listed as a device because a pack is a fixed set of cards that can be shuffled, dealt and counted.",
        rare = true,
    ),

    entry(
        databaseId = 60,
        kind = Kind.DEVICE,
        section = "Pilgrim goods",
        title = "The cast mirrors of Strasbourg's pilgrim trade",
        originalTitle = "Pilgrim mirror",
        author = "Gutenberg and his Strasbourg partners",
        year = 1440,
        summary = "Cast-metal mirrors made by a Strasbourg partnership were meant for pilgrims bound for the Aachen pilgrimage of 1440, to reflect light from the relics.",
        details = "The partnership was formed in 1438, and charges brought before the Strasbourg Council in 1439 concern its affairs. Sources differ on whether the Aachen pilgrimage was held in 1439 or 1440, and the mirror is listed as a device because it was meant to reflect light from a relic to the viewer.",
    ),

    entry(
        databaseId = 60,
        kind = Kind.DEVICE,
        section = "Standards",
        title = "Sejong's standard rulers of 1446, set by the pitch pipe",
        originalTitle = "Yeongjocheok",
        author = "Sejong's court",
        year = 1446,
        summary = "In 1446 Sejong's court tied its standard ruler, the yeongjocheok, to the length of the hwangjong pitch pipe, and local offices received bronze reference rulers as official standards.",
        details = "The hwangjong pitch pipe set the standard musical pitch, and the court measured its rulers against the pipe's length. Modern reconstructions of those ruler lengths give different figures, so this record names the year and the link to the pipe without quoting any length in centimetres.",
    ),

)
