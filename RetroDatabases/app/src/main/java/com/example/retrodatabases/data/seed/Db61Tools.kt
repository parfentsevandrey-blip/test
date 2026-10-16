package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1430 to 1440: Alberti's book of painting, Ulugh Beg's star tables, Cusanus's plan for councils, a Korean compendium of medicines, a Majorcan sea chart and King Duarte's riding manual. */
val db61Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 61,
        kind = Kind.TOOL,
        section = "Painting",
        title = "Alberti's De pictura, the book of linear perspective",
        originalTitle = "De pictura",
        author = "Leon Battista Alberti",
        year = 1435,
        summary = "Alberti's treatise of 1435 set out the rules for drawing a scene in linear perspective, with every receding line running to one vanishing point.",
        details = "Alberti wrote the treatise in Latin in 1435 and made an Italian version the next year, and both explain how to build a receding floor square by square. The treatise became one of the standard books on perspective for painters in the centuries that followed.",
    ),

    entry(
        databaseId = 61,
        kind = Kind.TOOL,
        section = "Astronomy",
        title = "Ulugh Beg's Zij, the star tables of Samarkand",
        originalTitle = "Zij-i Sultani",
        author = "Ulugh Beg, with Jamshid al-Kashi and Ali Qushji",
        year = 1437,
        summary = "Ulugh Beg's Zij gave the positions of about a thousand stars and tables for the planets, compiled from observations made at Samarkand in the late 1430s.",
        details = "Accounts differ on the year of completion, which is given as 1437 in some sources and as 1438 or 1439 in others, and they differ on how many stars the catalogue lists. Jamshid al-Kashi and Ali Qushji are named among the astronomers who worked on the project, and the tables spread through the Islamic world in later copies.",
    ),

    entry(
        databaseId = 61,
        kind = Kind.TOOL,
        section = "Church reform",
        title = "Cusanus's Concordantia catholica, a plan for church councils",
        originalTitle = "De concordantia catholica",
        author = "Nicholas of Cusa",
        year = 1433,
        summary = "Cusanus's Concordantia catholica of 1433 proposed a balance of power between the pope and the councils of the church, and it was written while the Council of Basel was meeting.",
        details = "The treatise is a political work about how the church should be governed, and it is not a concordance or index of the Bible despite its title. Accounts give 1433 as the year it was written, with later revisions, and it remains one of the main statements of conciliar theory from the period.",
    ),

    entry(
        databaseId = 61,
        kind = Kind.TOOL,
        section = "Medicine",
        title = "Hyangyak jipseongbang, Korea's compendium of native medicines of 1433",
        originalTitle = "Hyangyak jipseongbang",
        author = "Yu Hyo-tong and others, under King Sejong",
        year = 1433,
        summary = "The Hyangyak jipseongbang of 1433 was a Korean medical compendium in 85 volumes that gathered remedies made from native materials.",
        details = "Much of its formula material comes from Chinese medical books, and it builds on an earlier Korean compendium of 1399. The compilation was made under King Sejong, whose government wanted domestic herbs to replace some costly Chinese imports.",
    ),

    entry(
        databaseId = 61,
        kind = Kind.TOOL,
        section = "Navigation",
        title = "Gabriel de Vallseca's portolan chart of 1439, a sea chart from Majorca",
        originalTitle = "Carta nautica",
        author = "Gabriel de Vallseca",
        year = 1439,
        summary = "Gabriel de Vallseca's portolan chart of 1439 is a hand-drawn sea chart made in Majorca, and it is counted as the earliest surviving map to show the Azores.",
        details = "The mapmaker had moved from Barcelona to Palma by 1433, and an inscription on the chart places its making in Majorca. Accounts differ on the archive that holds it, since most name the Maritime Museum in Barcelona under inventory number 3236 and one names the Biblioteca de Catalunya.",
        rare = true,
    ),

    entry(
        databaseId = 61,
        kind = Kind.TOOL,
        section = "Horsemanship",
        title = "King Duarte's riding manual, the Livro da ensinanca de bem cavalgar toda sela",
        originalTitle = "Livro da ensinanca de bem cavalgar toda sela",
        author = "King Duarte of Portugal",
        year = 1434,
        summary = "King Duarte of Portugal wrote a manual on good riding about 1434, setting out the rules for riding in every kind of saddle.",
        details = "The manual survives in one manuscript, the Paris codex that also holds the king's Leal Conselheiro. The date of about 1434 is the one usually given for its composition, and the book was written while Duarte was king, before his death in 1438.",
    ),

)
