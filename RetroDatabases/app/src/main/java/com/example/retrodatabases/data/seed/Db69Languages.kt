package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1350 to 1360: an Italian storybook of the plague, an Arabic travel account, a French book of marvels, a Tuscan treatise on penitence, a French chronicle and an Old Bulgarian gospel. */
val db69Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 69,
        kind = Kind.LANGUAGE,
        section = "Italian",
        title = "Boccaccio's Decameron, a hundred tales told during the plague",
        originalTitle = "Decameron",
        author = "Giovanni Boccaccio",
        year = 1353,
        summary = "Boccaccio's Decameron, a collection of a hundred tales told by ten young people, was written in Italian in the early 1350s.",
        details = "The frame places the ten tellers in the countryside near Florence in 1348, the year of the Black Death, and most scholars date the composition to about 1349 to 1353. The year 1353 is a round date for the book, since accounts differ on when Boccaccio finished it.",
    ),

    entry(
        databaseId = 69,
        kind = Kind.LANGUAGE,
        section = "Arabic",
        title = "Ibn Battuta's Rihla, the Arabic account of his travels",
        originalTitle = "Tuhfat al-nuzzar fi gharaib al-amsar wa-ajaib al-asfar",
        author = "Ibn Battuta, with the scholar Ibn Juzayy",
        year = 1355,
        summary = "Ibn Battuta's Rihla, the account of his travels in the Islamic lands and beyond, was set down in Arabic at Fez in 1355 or 1356.",
        details = "Ibn Battuta returned to Morocco in 1349, and the Marinid sultan had his account dictated over about two years to the Andalusian scholar Ibn Juzayy, although some accounts name the sultan's court poet as the scribe. Accounts differ on whether the work was finished in 1355 or 1356, and the record uses 1355 as the usual date.",
    ),

    entry(
        databaseId = 69,
        kind = Kind.LANGUAGE,
        section = "French",
        title = "The Travels of John Mandeville, written in French",
        originalTitle = "Livre des merveilles du monde",
        author = "John Mandeville, a name that is probably a pseudonym",
        year = 1357,
        summary = "The Travels of John Mandeville, a French book of journeys and marvels, was composed in the later 1350s under a name that is probably a pseudonym.",
        details = "Accounts give 1356 or 1357 for the French text, while reference works spread its composition over a span up to 1371. The compiler has been named as Jehan de Bourgogne, a physician of Liege, but a catalogue record also names Jean d'Outremeuse.",
    ),

    entry(
        databaseId = 69,
        kind = Kind.LANGUAGE,
        section = "Tuscan",
        title = "Passavanti's Mirror of True Penitence, a treatise in Italian prose",
        originalTitle = "Lo specchio della vera penitenza",
        author = "Jacopo Passavanti",
        year = 1354,
        summary = "Passavanti's Mirror of True Penitence, a treatise on confession in Tuscan prose, was composed about 1354.",
        details = "Its author was a Florentine Dominican preacher, and the book is one of the early long works of Italian prose written in the vernacular rather than in Latin. The year 1354 is the usual date for its composition.",
        rare = true,
    ),

    entry(
        databaseId = 69,
        kind = Kind.LANGUAGE,
        section = "French",
        title = "Jean le Bel's chronicle of the wars of Edward III and John II",
        originalTitle = "Chronique de Jean le Bel",
        author = "Jean le Bel",
        year = 1355,
        summary = "Jean le Bel's Chronique, a French prose history of the wars between England and France, was written in the 1350s by a canon of Liege.",
        details = "The chronicle covers the reign of Edward III and the French kings of his time, and the later chronicler Jean Froissart used it as a source. The author's dates are uncertain, and the year 1355 is a middle date for the period in which the work was written.",
        rare = true,
    ),

    entry(
        databaseId = 69,
        kind = Kind.LANGUAGE,
        section = "Old Bulgarian",
        title = "The Gospels of Tsar Ivan Alexander, an illuminated book in Old Bulgarian",
        originalTitle = "Gospels of Tsar Ivan Alexander",
        author = "Commissioned by Tsar Ivan Alexander of Bulgaria",
        year = 1355,
        summary = "The Gospels of Tsar Ivan Alexander is an illuminated Gospel book in Old Bulgarian, made for the Bulgarian tsar in the mid-1350s.",
        details = "The text is written in the Cyrillic script, and its painted pictures are among the best-known Bulgarian work of the fourteenth century. The book is usually dated to 1355 to 1356.",
        rare = true,
    ),

)
