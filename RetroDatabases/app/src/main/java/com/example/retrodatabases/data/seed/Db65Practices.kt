package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1390 to 1400: a household book for a young wife, a placard of twelve articles and a withdrawal of obedience from a pope. */
val db65Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 65,
        kind = Kind.PRACTICE,
        section = "Household",
        title = "The Menagier de Paris, a household book of 1393 for a young wife",
        originalTitle = "Le Menagier de Paris",
        author = "An anonymous Parisian householder",
        year = 1393,
        summary = "The Menagier de Paris is a French household book of 1393 in which an older Parisian husband instructs his young wife in running the house.",
        details = "It covers housekeeping, cookery, gardening, the care of servants and manners, with recipes and lists of dishes. The author is anonymous, and the year 1393 is the usual date, though the book survives only in later copies.",
    ),

    entry(
        databaseId = 65,
        kind = Kind.PRACTICE,
        section = "Church",
        title = "The Twelve Conclusions of the Lollards, posted at Westminster in 1395",
        originalTitle = "Twelve Conclusions",
        author = "Lollard supporters",
        year = 1395,
        summary = "In 1395 a set of twelve articles against the Church, known as the Twelve Conclusions, was fixed to the doors of Westminster Hall as a public challenge to the clergy.",
        details = "The posting is said to have been made while Parliament was sitting, and accounts differ on who wrote the articles and how many copies were displayed. The year 1395 is the date usually given for the placards.",
        rare = true,
    ),

    entry(
        databaseId = 65,
        kind = Kind.PRACTICE,
        section = "Papacy",
        title = "France's withdrawal of obedience from Pope Benedict XIII in 1398",
        originalTitle = "Soustraction d'obedience",
        author = "The French crown and clergy",
        year = 1398,
        summary = "In 1398 France withdrew its obedience from the Avignon pope Benedict XIII, refusing to recognise him while the Great Schism lasted.",
        details = "The withdrawal was ordered by the crown and the clergy, and accounts differ on how far it was enforced in the provinces, since it was later reversed in part. The schism itself ended only in 1417, when Martin V was elected at the Council of Constance.",
    ),

)
