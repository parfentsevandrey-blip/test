package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 90 to 100: Quintilian's Latin textbook of a teacher's whole education, Tacitus's Agricola and Ban Zhao's Lessons for Women. */
val db195Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 195,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Quintilian's Institutio oratoria, the Latin textbook of a teacher's whole education",
        originalTitle = "Institutio oratoria",
        author = "Quintilian",
        year = 95,
        summary = "Quintilian's Institutio oratoria is a Latin textbook in twelve books that follows a pupil from infancy to the ideal orator, and it is usually dated to about 95.",
        details = "The work is usually dated to about 95, in the last years of Domitian, and that date is an estimate rather than a date given in the text. Quintilian taught rhetoric at Rome and wrote for teachers and pupils of the subject. The complete text was found again at the monastery of St Gall in 1416 by Poggio Bracciolini, and it was widely copied afterwards.",
    ),

    entry(
        databaseId = 195,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Tacitus's Agricola, a life of his father-in-law and an account of Roman Britain",
        originalTitle = "De vita Iulii Agricolae",
        author = "Tacitus",
        year = 98,
        summary = "Tacitus's Agricola is a short Latin biography of his father-in-law, the governor of Britain, written in the early years of Trajan's reign.",
        details = "Agricola governed Britain for about seven years, from about 77 to 84, and the book describes his campaigns and the battle of Mons Graupius. Accounts differ on the year of that battle, usually given as 83 or 84. The date of about 98 is the usual one for the book, which was written after the death of Domitian in 96.",
    ),

    entry(
        databaseId = 195,
        kind = Kind.LANGUAGE,
        section = "Chinese",
        title = "Ban Zhao's Lessons for Women, a Chinese book of conduct written by a court historian",
        originalTitle = "Nujie",
        author = "Ban Zhao",
        year = 100,
        summary = "Ban Zhao's Lessons for Women is a short Chinese guide to a wife's conduct, written by a Han court historian about the year 100.",
        details = "Accounts of the date differ: one account puts it near 80, one gives 99 to 105, and most give about 100, which is the year used here. One modern account calls it the earliest Chinese written work known to be authored by a woman and meant solely for the education of women. The author finished the Han history that her brother Ban Gu had begun, after he died in 92.",
        rare = true,
    ),

)
