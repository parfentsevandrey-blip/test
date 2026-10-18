package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1170 to 1175: Gerard of Cremona's Latin Almagest, Herrad's illustrated encyclopedia and the Notre-Dame organum book. */
val db87Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 87,
        kind = Kind.TOOL,
        section = "Astronomy",
        title = "Gerard of Cremona's Latin Almagest, made from Arabic at Toledo",
        originalTitle = "Almagesti",
        author = "Gerard of Cremona",
        year = 1175,
        summary = "Gerard of Cremona's Latin Almagest turned Ptolemy's astronomy into Latin from an Arabic version, and it was made at Toledo about 1175.",
        details = "Gerard worked at Toledo, where many Arabic books were rendered into Latin, and the Arabic text he used was itself a translation of Ptolemy's Greek. The year 1175 is the usual estimate for the Latin version.",
    ),

    entry(
        databaseId = 87,
        kind = Kind.TOOL,
        section = "Encyclopedias",
        title = "Herrad of Landsberg's Hortus deliciarum, an illustrated Latin encyclopedia for nuns",
        originalTitle = "Hortus deliciarum",
        author = "Herrad of Landsberg",
        year = 1175,
        summary = "Herrad of Landsberg's Hortus deliciarum is a Latin encyclopedia of Scripture, history and the liberal arts, illustrated with painted pictures and made for the nuns of Hohenburg.",
        details = "It was compiled in the years about 1167 to 1185, and accounts differ on the dates, which the book does not give exactly. The manuscript burned at Strasbourg in 1870, so its pictures are known now from copies made before the fire.",
    ),

    entry(
        databaseId = 87,
        kind = Kind.TOOL,
        section = "Music",
        title = "The Magnus liber organi, the two-part organum book of Notre-Dame in Paris",
        originalTitle = "Magnus liber organi",
        author = "Leonin and the Notre-Dame school",
        year = 1170,
        summary = "The Magnus liber organi is a collection of two-part organum for the services at Notre-Dame, compiled in the later twelfth century.",
        details = "Scholars date the collection from about 1160 to about 1200, and accounts differ on how much of it is Leonin's work and how much was added later. The surviving copies were written after the twelfth century, so the early form of the book has to be reconstructed.",
        rare = true,
    ),

)
