package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 680 to 690: two Old Malay stones from Srivijaya, the Kedukan Bukit of 682 and the Kota Kapur of 686. */
val db136Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 136,
        kind = Kind.LANGUAGE,
        section = "Old Malay",
        title = "The Kedukan Bukit stone, one of the earliest dated Old Malay inscriptions, set up in 682 near Palembang",
        originalTitle = "Prasasti Kedukan Bukit",
        author = "The court of Srivijaya",
        year = 682,
        summary = "The Kedukan Bukit stone is a short Old Malay inscription, written in a Pallava-derived script, that records a successful journey of a ruler of Srivijaya.",
        details = "The stone is dated 604 of the Saka era, which one reading puts at 682 CE, while other readings give 605 Saka, or 683 CE, so accounts differ over the year. This record uses 682, the reading that gives 604 Saka, and the stone is counted among the earliest dated texts in Old Malay.",
        rare = true,
    ),

    entry(
        databaseId = 136,
        kind = Kind.LANGUAGE,
        section = "Old Malay",
        title = "The Kota Kapur stone of 686, an Old Malay curse on those who defy the ruler of Srivijaya",
        originalTitle = "Prasasti Kota Kapur",
        author = "The court of Srivijaya",
        year = 686,
        summary = "The Kota Kapur stone, found on Bangka island, is a long Old Malay inscription whose text is largely a curse against enemies and those who do not obey the ruler of Srivijaya.",
        details = "It is dated 608 of the Saka era, which sources give as 28 February 686 CE; a Saka year begins in spring, so the year and the day need care when they are compared. Its curse formula is the main interest of the text, and it is among the earliest long texts in Old Malay.",
        rare = true,
    ),

)
