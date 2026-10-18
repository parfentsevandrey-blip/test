package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1130 to 1140: a Sanskrit encyclopedia, a Persian poem, Geoffrey's Latin history, Abelard's letter, Halevi's dialogue and a Jurchen script. */
val db91Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 91,
        kind = Kind.LANGUAGE,
        section = "Sanskrit",
        title = "Someshvara's Manasollasa, a Sanskrit encyclopedia of arts and sciences",
        originalTitle = "Manasollasa",
        author = "Someshvara III, with the authorship disputed",
        year = 1130,
        summary = "The Manasollasa is a Sanskrit encyclopedia of the arts, sciences and courtly life, dated to about 1130 in the Chalukya lands of the Deccan.",
        details = "One reckoning dates the text to Saka 1051, which is 1129 CE, from a verse inside it, while an older edition gives Saka 1052, a year that other reckonings place at 1130 or 1131 CE. Its notes also doubt that the king wrote the work himself and suspect a learned member of the court.",
        rare = true,
    ),

    entry(
        databaseId = 91,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Geoffrey of Monmouth's History of the Kings of Britain, a Latin prose history",
        originalTitle = "Historia regum Britanniae",
        author = "Geoffrey of Monmouth",
        year = 1136,
        summary = "Geoffrey of Monmouth's Latin history of the kings of Britain, from Brutus to Cadwallader, was finished about 1136 and became a major source of the Arthurian legend.",
        details = "Geoffrey claimed to translate a very old book in the British tongue, given to him by Walter, archdeacon of Oxford, and he dedicated the history to Robert, earl of Gloucester. The date of about 1136 is the usual one for its completion, and the claim to a British source has been doubted since the twelfth century.",
    ),

    entry(
        databaseId = 91,
        kind = Kind.LANGUAGE,
        section = "Judeo-Arabic",
        title = "Judah Halevi's Kuzari, a dialogue on the conversion of the Khazar king",
        originalTitle = "Kitab al-Khazari",
        author = "Judah Halevi",
        year = 1140,
        summary = "Judah Halevi's Kuzari is a dialogue in Judeo-Arabic in which a Khazar king examines the religions of his day, and it was completed about 1140.",
        details = "Halevi wrote the work over the 1130s, and a Hebrew translation followed later in the twelfth century. The date of 1140 is the usual one for its completion, and the Khazar setting rests on a story whose historical basis is debated.",
    ),

    entry(
        databaseId = 91,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Abelard's Story of My Misfortunes, a Latin letter of about 1132",
        originalTitle = "Historia calamitatum",
        author = "Peter Abelard",
        year = 1132,
        summary = "Abelard's Historia calamitatum is a Latin autobiographical letter, written about 1132 to a friend, that recounts his teaching, his injury and his love for Heloise.",
        details = "The letter was sent to a friend as a consolation, and the later letters between Abelard and Heloise take up the story it tells. The date is usually given as 1132, but accounts place it in 1132 or 1133, so the year here is the usual one.",
    ),

    entry(
        databaseId = 91,
        kind = Kind.LANGUAGE,
        section = "Persian",
        title = "Sanai's Garden of Truth, a Persian poem of mystical verse",
        originalTitle = "Hadiqat al-haqiqa wa shariat al-tariqa",
        author = "Sanai of Ghazna",
        year = 1131,
        summary = "Sanai's Garden of Truth is a Persian poem of mystical teaching in rhymed couplets, completed about 1131, and it became a model for later Persian mystical poets.",
        details = "Later poets such as Attar and Rumi took up its form and its moral stories. Accounts of the poet's own death differ between about 1131 and about 1141, so 1131 is the usual date of completion and not a fixed year.",
        rare = true,
    ),

    entry(
        databaseId = 91,
        kind = Kind.LANGUAGE,
        section = "Jurchen",
        title = "The Jurchen small script, a second writing system of the Jin introduced in 1138",
        originalTitle = "Nuzhen xiao zi",
        author = "Emperor Xizong of the Jin, credited with its making",
        year = 1138,
        summary = "The Jurchen small script was a writing system of the Jin dynasty, introduced in 1138 beside the large script of about 1119 to write the Jurchen language.",
        details = "The large script grew out of Khitan writing, and the small script came later under Emperor Xizong, who reigned from 1135 to 1150. The Jin official history dates its first formal use to 1145, so 1138 marks its creation rather than its adoption, and scholars still decode the surviving texts only in part.",
        rare = true,
    ),

)
