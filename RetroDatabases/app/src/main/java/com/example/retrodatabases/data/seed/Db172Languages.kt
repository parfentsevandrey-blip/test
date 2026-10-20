package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 320 to 325: a Greek church history, a lost song-book of Arius, a Greek Bible manuscript and Constantine's letter of 324. */
val db172Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 172,
        kind = Kind.LANGUAGE,
        section = "Church history",
        title = "Eusebius's Ecclesiastical History, a Greek church history in ten books",
        originalTitle = "Ekklesiastike historia",
        author = "Eusebius of Caesarea",
        year = 324,
        summary = "Eusebius of Caesarea's Ecclesiastical History tells the story of the church from its beginnings to Constantine's victory over Licinius in 324, in ten Greek books.",
        details = "The work was revised more than once, and accounts differ on the first edition, which some date to the years before 303 and others to about 311 or 313 to 314. This record uses 324, the year the narrative reaches the victory over Licinius, and some accounts place the final form after 325.",
    ),

    entry(
        databaseId = 172,
        kind = Kind.LANGUAGE,
        section = "Verse",
        title = "Arius's Thalia, a lost song-book of verse for the people of Alexandria",
        originalTitle = "Thalia",
        author = "Arius of Alexandria",
        year = 320,
        summary = "Arius's Thalia was a song-book in Greek, written about 320 to be sung by ordinary people, and it survives only in quotations made by its opponents.",
        details = "Athanasius quoted it to show that its verse taught the Son had a beginning, and the date of about 320 is an estimate that places it in Arius's dispute with his bishop Alexander. No copy of the book survives, so its text is known only from the passages that were quoted.",
        rare = true,
    ),

    entry(
        databaseId = 172,
        kind = Kind.LANGUAGE,
        section = "Manuscripts",
        title = "The Codex Vaticanus, a Greek Bible of the fourth century kept in the Vatican Library",
        originalTitle = "Codex Vaticanus",
        author = "Unknown scribes of the fourth century",
        year = 325,
        summary = "The Codex Vaticanus is one of the oldest surviving Greek Bibles, a large book of parchment that has been kept in the Vatican Library for centuries.",
        details = "Accounts place the copy in the first half of the fourth century, and this record uses 325 as its year, which rests on the handwriting and is an estimate. The book holds the Greek text of much of the Old and New Testaments, but with gaps, and it is one of the main witnesses that scholars compare when they edit the Greek Bible.",
        rare = true,
    ),

    entry(
        databaseId = 172,
        kind = Kind.LANGUAGE,
        section = "Letters",
        title = "Constantine's letter to Alexander and Arius, asking them to end their quarrel",
        originalTitle = "Letter to Alexander and Arius",
        author = "Constantine the Great",
        year = 324,
        summary = "In 324 Constantine sent a letter to the bishop Alexander and the priest Arius, urging them to settle their dispute over the nature of the Son, and the letter survives in Eusebius's Life of Constantine.",
        details = "Hosius of Cordoba is said to have carried the letter to Alexandria, and when it failed to end the quarrel, the council of 325 was called. The year 324 is the one usually given for the letter, and the text survives in Eusebius's account rather than in an archive.",
        rare = true,
    ),

)
