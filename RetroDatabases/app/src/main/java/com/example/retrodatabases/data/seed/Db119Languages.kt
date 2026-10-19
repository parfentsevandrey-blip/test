package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 850 to 860: al-Jahiz's Book of Animals, the Accounts of China and India of 851, the Kavirajamarga of about 850 and the Youyang zazu of 860. */
val db119Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 119,
        kind = Kind.LANGUAGE,
        section = "Arabic",
        title = "The Book of Animals by al-Jahiz, a miscellany of zoology, stories and verse",
        originalTitle = "Kitab al-Hayawan",
        author = "Abu Uthman al-Jahiz",
        year = 850,
        summary = "Al-Jahiz's Book of Animals, a miscellany of natural history, stories and verse, runs to seven volumes in its printed edition.",
        details = "The year of composition is only approximate, and about 850 is the date used here. Al-Jahiz describes a competition among living things for food and survival, which modern writers compare with natural selection, a comparison that is a modern reading rather than his own language.",
    ),

    entry(
        databaseId = 119,
        kind = Kind.LANGUAGE,
        section = "Arabic",
        title = "The Accounts of China and India, an Arab merchant's report of the sea route to China",
        originalTitle = "Akhbar al-Sin wa'l-Hind",
        author = "Attributed to a merchant named Sulayman",
        year = 851,
        summary = "The Accounts of China and India, an Arabic report on the sea voyage from the Persian Gulf to China, was set down in 851 from the reports of traders.",
        details = "The surviving text is anonymous and is attributed to a merchant named Sulayman, and it describes the Chinese port of Khanfu, the Arab name for Guangzhou, and the trade that Arab traders found there. A later compiler, Abu Zayd al-Hasan, added material to it in 916.",
        rare = true,
    ),

    entry(
        databaseId = 119,
        kind = Kind.LANGUAGE,
        section = "Kannada",
        title = "The Kavirajamarga, the earliest surviving Kannada work on poetics",
        originalTitle = "Kavirajamarga",
        author = "Attributed to Amoghavarsha I, with Srivijaya credited by some",
        year = 850,
        summary = "The Kavirajamarga, a Kannada guide to rhetoric, poetics and grammar, is dated to about 850 in the time of the Rashtrakuta king Amoghavarsha I.",
        details = "Its title means roughly the royal road of poets, and it was written as a practical manual to bring the varied styles of Kannada writing together. Authorship is disputed: some credit the king himself, and others the court poet Srivijaya, who may have written it or advised its author.",
        rare = true,
    ),

    entry(
        databaseId = 119,
        kind = Kind.LANGUAGE,
        section = "Chinese",
        title = "The Youyang zazu, a Tang miscellany of anecdotes, plants, animals and crafts",
        originalTitle = "Youyang zazu",
        author = "Duan Chengshi",
        year = 860,
        summary = "The Youyang zazu, a Chinese miscellany of anecdotes and natural lore by Duan Chengshi, was completed in 860.",
        details = "Its sections range over marvels, plants, animals, Buddhist and Daoist tales and the crafts. The text is written in the classical prose of the Tang literati, and it is a useful source for the everyday knowledge of its time.",
        rare = true,
    ),

)
