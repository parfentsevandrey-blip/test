package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 630 to 640: Isidore of Seville's Etymologies, the Aihole inscription of 634 and Sophronius's synodical letter of 634. */
val db141Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 141,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Isidore of Seville's Etymologies, a Latin encyclopedia of words and things in twenty books, completed about 630",
        originalTitle = "Etymologiae sive Originum libri XX",
        author = "Isidore of Seville",
        year = 630,
        summary = "Isidore of Seville's Etymologies is a Latin encyclopedia of words and things in twenty books, completed about 630 and used for centuries as a standard reference.",
        details = "Its first ten books take up grammar, rhetoric, mathematics, medicine, law, the church and words, and its last ten take up people, animals, the earth, metals, farming, war and food. Isidore, bishop of Seville, died in 636, and the work is usually dated to about 630, which is the year used here.",
    ),

    entry(
        databaseId = 141,
        kind = Kind.LANGUAGE,
        section = "Sanskrit",
        title = "The Aihole inscription of 634, a Sanskrit eulogy of Pulakeshin II cut in stone at the Meguti temple",
        originalTitle = "Aihole prashasti",
        author = "Ravikirti, court poet of Pulakeshin II",
        year = 634,
        summary = "The Aihole inscription is a Sanskrit eulogy of the Chalukya king Pulakeshin II, cut in stone at the Meguti temple and dated 634.",
        details = "The poem praises the king's victories, among them his defeat of Harsha, the ruler of northern India. The date is given in the Saka era as year 556, which is 634 in the Common Era.",
        rare = true,
    ),

    entry(
        databaseId = 141,
        kind = Kind.LANGUAGE,
        section = "Greek",
        title = "Sophronius's synodical letter of 634, which defended the two wills and energies of Christ against the teaching of one will",
        originalTitle = "Synodike epistole",
        author = "Sophronius of Jerusalem",
        year = 634,
        summary = "Sophronius, patriarch of Jerusalem, sent a synodical letter in 634 defending the two wills and energies of Christ.",
        details = "The letter was a formal Greek statement sent to the leading bishops, among them Sergius of Constantinople, against the teaching of one will that the imperial court supported. The decree of 638 that followed, the Ekthesis, tried to end the dispute, and the controversy ran on to the Lateran synod of 649 in Rome.",
        rare = true,
    ),

)
