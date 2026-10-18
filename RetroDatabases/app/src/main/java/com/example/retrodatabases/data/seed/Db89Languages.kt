package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1150 to 1160: Hildegard's Scivias, an Icelandic treatise on sounds, Wace's Brut, the Metalogicon, Caffaro's Genoese annals and Otto's Deeds of Frederick. */
val db89Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 89,
        kind = Kind.LANGUAGE,
        section = "Latin visions",
        title = "Hildegard of Bingen's Scivias, a book of twenty-six visions",
        originalTitle = "Scivias",
        author = "Hildegard of Bingen",
        year = 1151,
        summary = "Hildegard of Bingen completed her Latin book of visions, the Scivias, in about 1151, and it sets out twenty-six visions in three books.",
        details = "She dated her first vision to 1141, but the writing took about a decade, so accounts give 1151 or 1152 for the finish. The three books hold six, seven and thirteen visions, after a preface that some scholars date to about 1150 to 1151.",
    ),

    entry(
        databaseId = 89,
        kind = Kind.LANGUAGE,
        section = "Old Norse",
        title = "The First Grammatical Treatise, an Icelandic study of sounds and spelling",
        originalTitle = "Fyrsta malfraediritgerdin",
        author = "An unnamed Icelandic writer",
        year = 1150,
        summary = "The First Grammatical Treatise is an anonymous Icelandic study of the sounds of Old Norse and of how each sound should be written, dated about 1150.",
        details = "Hreinn Benediktsson's dating gives a window from 1125 to 1175, so about 1150 is the midpoint of that range rather than a fixed year. The treatise tries to give each distinct sound its own letter, and its 12th-century spelling standard lies behind the modern Icelandic alphabet.",
        rare = true,
    ),

    entry(
        databaseId = 89,
        kind = Kind.LANGUAGE,
        section = "French verse",
        title = "Wace's Roman de Brut, a French verse history of Britain",
        originalTitle = "Roman de Brut",
        author = "Wace",
        year = 1155,
        summary = "Wace finished his Roman de Brut in 1155, a French verse history of the kings of Britain that he dedicated to Queen Eleanor.",
        details = "The poem turns the Latin history of Geoffrey of Monmouth into octosyllabic couplets, and it is the earliest known work to name the Round Table. The year 1155 is the usual date for its completion, and copies of the poem survive from England and France.",
    ),

    entry(
        databaseId = 89,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "John of Salisbury's Metalogicon, a defence of grammar and logic",
        originalTitle = "Metalogicon",
        author = "John of Salisbury",
        year = 1159,
        summary = "John of Salisbury completed his Metalogicon in 1159, a Latin defence of the study of grammar and logic against scholars who scorned them.",
        details = "The book answers critics who thought that grammar and logic were of little use to a learned man. John wrote it in the same year as his Policraticus, so the two books are usually dated together to 1159.",
    ),

    entry(
        databaseId = 89,
        kind = Kind.LANGUAGE,
        section = "Genoa",
        title = "Caffaro's Annals of Genoa, a chronicle given to the city archive",
        originalTitle = "Annales Ianuenses",
        author = "Caffaro di Rustico",
        year = 1152,
        summary = "Caffaro's Latin annals of Genoa were given a place in the public archive after his petition of 1152, and they record Genoese affairs from 1099 to 1163.",
        details = "In 1152 Caffaro asked the city to keep a copy of his annals in the public archive, which turned a private account into a civic record. The Paris manuscript lists his section as 1099 to 1163, and accounts differ on when he began writing, so 1152 is the firmest date given here.",
        rare = true,
    ),

    entry(
        databaseId = 89,
        kind = Kind.LANGUAGE,
        section = "Latin history",
        title = "The Deeds of Frederick Barbarossa, Otto of Freising's unfinished history",
        originalTitle = "Gesta Friderici imperatoris",
        author = "Otto of Freising and Rahewin",
        year = 1158,
        summary = "Otto of Freising's Latin history of the emperor Frederick Barbarossa was left unfinished when he died in 1158, and his fellow canon Rahewin carried it on.",
        details = "Otto wrote the early books before his death, and Rahewin completed the work in about 1160, so accounts differ on the year it was finished. The history follows the emperor's reign from his election in 1152.",
        rare = true,
    ),

)
