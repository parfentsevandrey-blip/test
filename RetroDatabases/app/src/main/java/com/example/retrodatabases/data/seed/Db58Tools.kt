package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1460 to 1470: Regiomontanus's triangle and astronomy books, Torquemada's meditations, Filarete's ideal city, Platina's cookbook and Pius II's Asia. */
val db58Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 58,
        kind = Kind.TOOL,
        section = "Trigonometry",
        title = "Regiomontanus's De triangulis, the book of triangles",
        originalTitle = "De triangulis omnimodis",
        author = "Johannes Regiomontanus",
        year = 1464,
        summary = "Regiomontanus's book on triangles of every kind gives the sine rule and tables for solving plane and spherical triangles.",
        details = "The book was written in the 1460s, and the year used here, 1464, is the usual date of composition, although accounts differ between about 1462 and 1464. The first printed edition appeared at Nuremberg only in 1533, nearly seventy years after the text was written.",
    ),

    entry(
        databaseId = 58,
        kind = Kind.TOOL,
        section = "Astronomy",
        title = "Regiomontanus's Epitome of Ptolemy's Almagest",
        originalTitle = "Epitome in Almagestum Ptolemaei",
        author = "Johannes Regiomontanus",
        year = 1462,
        summary = "Regiomontanus's summary of Ptolemy's Almagest was written about 1462 and served later astronomers as a handbook of the Ptolemaic system.",
        details = "The Epitome is dated to about 1462, and the year used is its date of composition. Its first printed edition came only in 1496, more than thirty years later.",
    ),

    entry(
        databaseId = 58,
        kind = Kind.TOOL,
        section = "Devotion",
        title = "Torquemada's Meditationes, printed at Subiaco",
        originalTitle = "Meditationes",
        author = "Juan de Torquemada",
        year = 1467,
        summary = "Torquemada's meditations, printed at Subiaco in 1467 with woodcut pictures, were a devotional aid for readers.",
        details = "The year is that of the Subiaco printing, which is the first printed form of the text, so no composition date is used. Accounts treat the book as one of the earliest illustrated books printed in Italy.",
    ),

    entry(
        databaseId = 58,
        kind = Kind.TOOL,
        section = "Architecture",
        title = "Filarete's treatise and the ideal city of Sforzinda",
        originalTitle = "Trattato di architettura",
        author = "Antonio Averlino, called Filarete",
        year = 1464,
        summary = "Filarete wrote his architecture treatise for Francesco Sforza in the early 1460s, and it describes an ideal city named Sforzinda.",
        details = "The treatise was written between about 1461 and 1464 and describes an imagined city with a star-shaped plan, along with its buildings and streets. The year used is the date of composition, since the first printed edition appeared only in 1972.",
        rare = true,
    ),

    entry(
        databaseId = 58,
        kind = Kind.TOOL,
        section = "Cookery",
        title = "Platina's book of honest pleasure and health",
        originalTitle = "De honesta voluptate et valetudine",
        author = "Bartolomeo Platina",
        year = 1465,
        summary = "Platina's book on honest pleasure and health, written about 1465, is a cookbook with recipes and advice on diet.",
        details = "Platina wrote the book at Rome about 1465, and its first printed edition came in the 1470s, so the year used is the date of composition. Accounts differ on how much of the book follows earlier cookery sources.",
    ),

    entry(
        databaseId = 58,
        kind = Kind.TOOL,
        section = "Geography",
        title = "Pius II's Asia, a geography of 1461",
        originalTitle = "Asia",
        author = "Aeneas Silvius Piccolomini, later Pope Pius II",
        year = 1461,
        summary = "Pius II's description of Asia, written in 1461, sets out the lands and peoples of the continent for a Christian reader.",
        details = "Piccolomini, later Pope Pius II, wrote this description in 1461, so the year used is the date of composition. Its first printed form is the Venice incunable of 1477 whose title begins Historia rerum ubique gestarum.",
        rare = true,
    ),

)
