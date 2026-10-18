package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1000 to 1008: a Japanese court romance, a court lady's notes, an Old English grammar of Latin and a Cordoba medical encyclopedia. */
val db104Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 104,
        kind = Kind.LANGUAGE,
        section = "Japan",
        title = "The Tale of Genji, a long prose romance of the Heian court",
        originalTitle = "Genji monogatari",
        author = "Murasaki Shikibu",
        year = 1005,
        summary = "Murasaki Shikibu's Tale of Genji, a long prose romance written in installments, circulated among court readers in the early eleventh century.",
        details = "An encyclopedia places its writing at about 1000 to 1012, and the author had joined the household of Empress Shoshi by about 1005, where Fujiwara no Michinaga noticed a copy of the tale that Shoshi held. Its fifty-four chapters follow Prince Genji and his descendants, and the text is written largely in the cursive kana syllabary.",
    ),

    entry(
        databaseId = 104,
        kind = Kind.LANGUAGE,
        section = "Japan",
        title = "The Pillow Book of Sei Shonagon, lists and sketches from the Heian court",
        originalTitle = "Makura no soshi",
        author = "Sei Shonagon",
        year = 1002,
        summary = "Sei Shonagon's Pillow Book, a collection of lists, sketches and remarks from the court of Empress Teishi, was written about 1002.",
        details = "Sei Shonagon served Empress Teishi, who died in 1000, and the book mixes short lists of likes and dislikes with scenes of court life and personal comments. Its title means notes kept beside a pillow, and it is among the best known prose works of the Heian period.",
    ),

    entry(
        databaseId = 104,
        kind = Kind.LANGUAGE,
        section = "England",
        title = "AElfric's Grammar, a Latin grammar taught in Old English",
        originalTitle = "Excerptiones de arte grammatica anglice",
        author = "AElfric",
        year = 1000,
        summary = "AElfric wrote a Latin grammar in Old English about 1000, so that English pupils could learn the parts of speech and the rules of Latin in their own language.",
        details = "The text draws on the late Latin grammar tradition and explains its rules in the vernacular, which made Latin grammar available to Anglo-Saxon students. It is one of the earliest books written in Old English about the structure of a language, and its Latin title is Excerptiones de arte grammatica anglice.",
        rare = true,
    ),

    entry(
        databaseId = 104,
        kind = Kind.LANGUAGE,
        section = "Medicine",
        title = "Al-Zahrawi's Kitab al-Tasrif, a medical encyclopedia that ends with surgery",
        originalTitle = "Kitab al-Tasrif li-man 'ajiza 'an al-ta'lif",
        author = "Abu al-Qasim al-Zahrawi",
        year = 1000,
        summary = "Al-Zahrawi wrote his medical encyclopedia Kitab al-Tasrif in Cordoba about 1000, and its last treatise is a surgical manual with drawings of instruments.",
        details = "The work has thirty treatises, and the thirtieth, on surgery, describes operations and the instruments used in them, with drawings of the instruments in its manuscripts. Part of the text was later rendered into Latin, which carried its surgical methods into European medicine.",
        rare = true,
    ),

)
