package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 830 to 833: Einhard's Life of Charlemagne, the Old Saxon Heliand, Kukai's Chinese summary and the Ryo no gige. */
val db121Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 121,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Einhard's Life of Charlemagne, a Latin biography of the Frankish emperor",
        originalTitle = "Vita Karoli Magni",
        author = "Einhard",
        year = 830,
        summary = "Einhard's Life of Charlemagne, a Latin biography of the Frankish king and emperor, is usually dated to about 830.",
        details = "Einhard had served at Charlemagne's court and then under his son Louis the Pious, and he modelled the Life on Suetonius's lives of the Roman emperors. The date of writing is disputed, and 830 is the usual year given for the work.",
    ),

    entry(
        databaseId = 121,
        kind = Kind.LANGUAGE,
        section = "Old Saxon",
        title = "The Heliand, a gospel poem in Old Saxon alliterative verse",
        originalTitle = "Heliand",
        author = "An unnamed Saxon poet",
        year = 830,
        summary = "The Heliand, a poem of the gospel story in Old Saxon alliterative verse, is usually dated to about 830.",
        details = "The poem tells the life of Christ in some six thousand lines, built on the four gospels and set in the long alliterative line of the native Germanic verse. Its poet is unknown, and the usual date of about 830 falls in the reign of Louis the Pious.",
        rare = true,
    ),

    entry(
        databaseId = 121,
        kind = Kind.LANGUAGE,
        section = "Japan",
        title = "Kukai's Precious Key to the Secret Treasury, a summary of his teaching in Chinese",
        originalTitle = "Hizo hoyaku",
        author = "Kukai",
        year = 830,
        summary = "Kukai's Precious Key to the Secret Treasury, a short summary of his Shingon teaching written in Chinese, is dated 830.",
        details = "It condenses his longer work, the Jujushinron, which ranks ten stages of mind from the most ordinary up to the Shingon teaching at the top. Kukai wrote it in classical Chinese, the language of learned Japanese religious writing in his day, and 830 is the year usually given.",
        rare = true,
    ),

    entry(
        databaseId = 121,
        kind = Kind.LANGUAGE,
        section = "Japan",
        title = "The Ryo no gige, an official commentary on the administrative regulations of the Yoro code",
        originalTitle = "Ryo no gige",
        author = "Court compilers of Japan",
        year = 833,
        summary = "The Ryo no gige, an official commentary on the administrative part of the Yoro code, is dated 833.",
        details = "The administrative regulations of the Yoro code were put into force in 757, and this commentary explains their wording in an official form. The work is dated 833, and it is one of two later commentaries on those regulations.",
        rare = true,
    ),

)
