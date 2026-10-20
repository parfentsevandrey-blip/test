package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 590 to 600: Gregory the Great's Pastoral Rule, Gregory of Tours's Histories, Gregory's Dialogues and Evagrius's Ecclesiastical History. */
val db145Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 145,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Gregory the Great's Pastoral Rule, a Latin guide for bishops on teaching and correcting their flocks, written about 591",
        originalTitle = "Regula pastoralis",
        author = "Gregory the Great",
        year = 591,
        summary = "Gregory the Great's Regula pastoralis is a Latin handbook on how a bishop should guide, teach and correct the people in his care, written in the first years of Gregory's papacy.",
        details = "Gregory became pope in 590, and the book is usually dated 590 to 591; a copy was sent to John, bishop of Ravenna. King Alfred of Wessex later had it put into Old English, which shows how long it stayed in use.",
    ),

    entry(
        databaseId = 145,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Gregory of Tours's Ten Books of Histories, a Latin history of the Franks completed about 594",
        originalTitle = "Historiarum libri decem",
        author = "Gregory of Tours",
        year = 594,
        summary = "Gregory of Tours's Ten Books of Histories is a Latin history of the Franks in ten books, completed in the last years of its author's life.",
        details = "Gregory was bishop of Tours, and he died in 594, the year the work is usually taken to be finished. The books were written over several years, so 594 marks the close of the work rather than its first draft.",
    ),

    entry(
        databaseId = 145,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Gregory the Great's Dialogues, four Latin books of tales of Italian holy men and miracles, written about 593",
        originalTitle = "Dialogi",
        author = "Gregory the Great",
        year = 593,
        summary = "Gregory the Great's Dialogues are four Latin books in the form of a talk between Gregory and his deacon Peter, full of tales of holy men and miracles in Italy.",
        details = "The second book is the life of Benedict of Nursia, and the fourth argues for the immortality of the soul. The usual dates are 593 or 594, and this record uses 593.",
        rare = true,
    ),

    entry(
        databaseId = 145,
        kind = Kind.LANGUAGE,
        section = "Greek",
        title = "Evagrius Scholasticus's Ecclesiastical History, six Greek books covering the years 431 to 594",
        originalTitle = "Ekklesiastike historia",
        author = "Evagrius Scholasticus",
        year = 594,
        summary = "Evagrius Scholasticus's Ecclesiastical History is a Greek church history in six books that covers the years 431 to 594, continuing the work of Socrates, Sozomen and Theodoret.",
        details = "Evagrius was a Syrian lawyer, and the span from 431 to 594 is the one given in the usual description of the work. The exact year of its completion is uncertain, and 594 is used here as the year the history reaches.",
        rare = true,
    ),

)
