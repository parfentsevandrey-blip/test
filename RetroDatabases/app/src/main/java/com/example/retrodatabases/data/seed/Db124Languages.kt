package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 800 to 810: Bai Juyi's Song of Everlasting Sorrow of 806, the Kogo Shui and the Book of Armagh of 807, and the Book of Kells of about 800. */
val db124Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 124,
        kind = Kind.LANGUAGE,
        section = "Chinese",
        title = "Bai Juyi's Song of Everlasting Sorrow, a narrative poem on Emperor Xuanzong and Yang Guifei, of 806",
        originalTitle = "Chang hen ge",
        author = "Bai Juyi",
        year = 806,
        summary = "Bai Juyi's Song of Everlasting Sorrow, a long narrative poem in Chinese about the emperor Xuanzong and his favourite Yang Guifei, dates from 806.",
        details = "The poem tells how the An Lushan rebellion of 755 drove the emperor from the capital and cost him Yang Guifei, and it is often read with Chen Hong's prose tale of the same story. The date 806 is the usual one given for the poem.",
    ),

    entry(
        databaseId = 124,
        kind = Kind.LANGUAGE,
        section = "Japanese",
        title = "Kogo Shui, the Inbe family's gleanings from ancient stories, presented to the throne in 807",
        originalTitle = "Kogo Shui",
        author = "Inbe no Hironari",
        year = 807,
        summary = "The Kogo Shui, a record of the Inbe family's traditions about old court rites and offices, was finished by Inbe no Hironari in 807 and presented to Emperor Heizei.",
        details = "Its aim was to defend the Inbe family's claims against the Nakatomi, and its last section lists eleven grievances. Much of its material parallels the Kojiki and the Nihon Shoki and adds episodes found nowhere else, and accounts differ on whether it was first written in Classical Chinese or in Japanese.",
        rare = true,
    ),

    entry(
        databaseId = 124,
        kind = Kind.LANGUAGE,
        section = "Irish",
        title = "The Book of Armagh, a Latin New Testament with writings of Saint Patrick, copied at Armagh about 807",
        originalTitle = "Liber Ardmachanus",
        author = "Ferdomnach, scribe of Armagh",
        year = 807,
        summary = "The Book of Armagh, a Latin manuscript of the New Testament with writings about Saint Patrick, was copied at Armagh about 807 by the scribe Ferdomnach.",
        details = "The scribe's name was recovered in 1846 from text erased from the colophon, and the colophon ties the work to the abbot Torbach, whose abbacy is confined to 807. Accounts differ between 807 and 808 for the first section, and Newport White argued that the closing note may simply repeat an older copy.",
        rare = true,
    ),

    entry(
        databaseId = 124,
        kind = Kind.LANGUAGE,
        section = "Irish",
        title = "The Book of Kells, an illuminated Latin book of the four gospels dated about 800",
        originalTitle = "Book of Kells",
        author = "Unknown scribes and illuminators",
        year = 800,
        summary = "The Book of Kells, an illuminated Latin book of the four gospels now at Trinity College Dublin, is usually dated to about 800.",
        details = "Its pages are decorated with interlace, spirals and figure work. Accounts differ on where it was made, with Iona and Kells both named, and estimates of its date vary by a few decades.",
    ),

)
