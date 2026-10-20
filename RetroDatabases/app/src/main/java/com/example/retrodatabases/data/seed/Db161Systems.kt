package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 431 to 439: the Council of Ephesus, the joint rule of Bleda and Attila, the Theodosian Code and the Northern Wei conquest of the Northern Liang. */
val db161Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 161,
        kind = Kind.SYSTEM,
        section = "Councils",
        title = "The Council of Ephesus of 431, which condemned Nestorius",
        originalTitle = "Concilium Ephesinum",
        author = "Theodosius II and the bishops at Ephesus",
        year = 431,
        summary = "The Council of Ephesus of 431, the third ecumenical council, condemned Nestorius and affirmed that Mary is the Theotokos, the bearer of God.",
        details = "The council opened at Ephesus on 22 June 431 after the emperor Theodosius II called it, and Cyril of Alexandria led the majority that deposed Nestorius as patriarch of Constantinople. John of Antioch arrived late and held a rival meeting, so the council ended with two sides, and the quarrel was settled only in 433.",
    ),

    entry(
        databaseId = 161,
        kind = Kind.SYSTEM,
        section = "Steppe",
        title = "The joint rule of the Hunnic brothers Bleda and Attila from 434",
        originalTitle = "Regnum Hunnorum",
        author = "Bleda and Attila, kings of the Huns",
        year = 434,
        summary = "In 434 the brothers Bleda and Attila became joint kings of the Huns after the death of their uncle Rua, and they ruled together until Bleda died about 445.",
        details = "The year 434 is the date usually given for Rua's death, and it begins the joint rule that Attila ended by ruling alone after Bleda's death. Attila went on to raid the eastern and western empires, and he died in 453.",
        rare = true,
    ),

    entry(
        databaseId = 161,
        kind = Kind.SYSTEM,
        section = "Law",
        title = "The Theodosian Code, a collection of imperial laws issued in 438",
        originalTitle = "Codex Theodosianus",
        author = "Theodosius II and a commission set up in 429",
        year = 438,
        summary = "The Theodosian Code, a Latin collection of imperial laws from Constantine onward, was issued in 438 and came into force in both halves of the empire on 1 January 439.",
        details = "The code has sixteen books arranged by subject, and the imperial constitution that issued it is dated 15 February 438 at Constantinople. One modern view holds that the code gained full authority in the West only in 448, and this view is disputed.",
    ),

    entry(
        databaseId = 161,
        kind = Kind.SYSTEM,
        section = "China",
        title = "The Northern Wei conquest of the Northern Liang in 439",
        originalTitle = "Bei Liang",
        author = "Emperor Taiwu of the Northern Wei",
        year = 439,
        summary = "In 439 the Northern Wei took Guzang, the capital of the Northern Liang, and brought northern China under one rule, an event often taken as the end of the Sixteen Kingdoms.",
        details = "The last Northern Liang ruler, Juqu Mengxun, surrendered that year to the Wei court under Emperor Taiwu. Historians differ on where the Sixteen Kingdoms period begins, but they usually end it with this conquest.",
        rare = true,
    ),

)
