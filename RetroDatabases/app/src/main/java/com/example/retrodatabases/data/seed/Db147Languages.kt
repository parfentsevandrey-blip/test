package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 570 to 580: a Persian version of the Panchatantra fables and Agathias's Greek history of Justinian's wars. */
val db147Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 147,
        kind = Kind.LANGUAGE,
        section = "Middle Persian",
        title = "Burzoe's Middle Persian version of the Indian fables of the Panchatantra, made in the reign of Khosrow I",
        originalTitle = "Karilag and Damnag",
        author = "Burzoe, physician of Khosrow I",
        year = 570,
        summary = "The physician Burzoe rendered the Indian fables of the Panchatantra into Middle Persian under Khosrow I, and the Persian text survives only through an Arabic descendant.",
        details = "Accounts differ on the date: some place the translation in the reign of Khosrow I, who ruled from 531 to 579, and one gives a window of about 550 to 578, so 570 is a placed year inside that window and not a date from the sources. Ibn al-Muqaffa's Arabic Kalila wa Dimna, made from the Persian text, carries the fables forward.",
        rare = true,
    ),

    entry(
        databaseId = 147,
        kind = Kind.LANGUAGE,
        section = "Greek",
        title = "Agathias's Histories, five Greek books that carry Procopius's account of Justinian's wars on from 552",
        originalTitle = "Historiai",
        author = "Agathias of Myrina",
        year = 575,
        summary = "Agathias of Myrina wrote five books of Greek history that take up Procopius's narrative of Justinian's wars and run on to about 558.",
        details = "No source gives the year the Histories were finished; they were written after Justinian died in 565, and the 570s is the usual estimate, so 575 is a placed year. Accounts differ on the last year, 558 or 559, and some sources give Agathias's death as 582 or 594.",
        rare = true,
    ),

)
