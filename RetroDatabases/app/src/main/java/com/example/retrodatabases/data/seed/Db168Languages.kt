package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 360 to 370: Athanasius's Life of Antony, Hilary's On the Trinity, two Greek works of Julian and the Shangqing revelations of Yang Xi. */
val db168Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 168,
        kind = Kind.LANGUAGE,
        section = "Greek",
        title = "Athanasius's Life of Antony, a Greek life of the Egyptian monk",
        originalTitle = "Bios kai politeia tou hosiou patros hemon Antoniou",
        author = "Athanasius of Alexandria",
        year = 360,
        summary = "Athanasius's Life of Antony tells the story of the Egyptian hermit in Greek, and it became a model for later lives of monks.",
        details = "Antony died in 356, and scholars date the Life between 356 and 362, most often about 360. The text says that Antony lived 105 years, and it presents his solitary prayer in the desert as an example for monks.",
    ),

    entry(
        databaseId = 168,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Hilary of Poitiers's On the Trinity, twelve books written in exile",
        originalTitle = "De Trinitate libri duodecim",
        author = "Hilary of Poitiers",
        year = 360,
        summary = "Hilary of Poitiers wrote his twelve books On the Trinity while he was exiled in Phrygia, as a Latin defence of the Nicene faith against the Arians.",
        details = "Hilary was sent into exile in Phrygia in 356, and the work took shape there over several years. Accounts differ on when it was begun, between about 356 and 358, and it was completed around 360, when he returned from exile to Gaul.",
    ),

    entry(
        databaseId = 168,
        kind = Kind.LANGUAGE,
        section = "Greek",
        title = "Julian's Misopogon, his Greek satire on the people of Antioch and his own beard",
        originalTitle = "Misopogon",
        author = "The emperor Julian",
        year = 363,
        summary = "Julian's Misopogon is a Greek satire that he wrote at Antioch, mocking the city's people and his own long beard.",
        details = "Julian wrote it at Antioch in the winter before his last campaign, and it answers the Antiochenes who had laughed at his beard and his manners. It is an unusual piece of self-mockery by an emperor, written in Greek.",
        rare = true,
    ),

    entry(
        databaseId = 168,
        kind = Kind.LANGUAGE,
        section = "Greek",
        title = "Julian's Against the Galileans, a Greek attack on the Christian scriptures",
        originalTitle = "Kata Galilaion",
        author = "The emperor Julian",
        year = 363,
        summary = "Julian's Against the Galileans is a Greek treatise that attacks the Christian scriptures and the teachings of the Galileans, as he called Christians.",
        details = "Julian wrote it in the last years of his reign, about 362 to 363, and the work is known only from fragments quoted in a later Christian reply by Cyril of Alexandria. Because the fragments come through Cyril's quotations, their wording depends on how faithfully he copied them.",
        rare = true,
    ),

    entry(
        databaseId = 168,
        kind = Kind.LANGUAGE,
        section = "Chinese",
        title = "The Shangqing revelations of Yang Xi, visions written down from 364 to 370",
        originalTitle = "Shangqing",
        author = "Yang Xi, with the copyist Xu Mi",
        year = 364,
        summary = "The Shangqing revelations are Taoist scriptures that Yang Xi received in visions from 364 to 370, and his copyists wrote them down as they came.",
        details = "Yang Xi, a man of the Eastern Jin period, reported that immortals spoke to him in visions over several years. The texts were edited later, and the collection that survives was arranged by Tao Hongjing in the late fifth century.",
        rare = true,
    ),

)
