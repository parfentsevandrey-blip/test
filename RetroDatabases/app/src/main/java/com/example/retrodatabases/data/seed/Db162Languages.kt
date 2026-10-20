package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 420 to 430: Augustine's City of God, a Greek book of desert monks, two Chinese Buddhist translations and Liu Yiqing's book of anecdotes. */
val db162Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 162,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Augustine's City of God, twenty-two books on the two cities, finished in 426",
        originalTitle = "De civitate Dei",
        author = "Augustine of Hippo",
        year = 426,
        summary = "Augustine's City of God is a Latin work in twenty-two books that sets the earthly city against the heavenly one, finished in 426 after more than a decade of writing.",
        details = "Augustine began the work in 413, after the sack of Rome in 410 had led pagan critics to blame the Christians, and its books came out in parts as they were written. Books one to ten answer the pagans, and books eleven to twenty-two treat the origin, course and end of the two cities; 426 is the usual date for the completed work.",
    ),

    entry(
        databaseId = 162,
        kind = Kind.LANGUAGE,
        section = "Greek",
        title = "Palladius's Lausiac History, Greek lives of desert monks written for the chamberlain Lausus",
        originalTitle = "Historia Lausiaca",
        author = "Palladius of Helenopolis",
        year = 420,
        summary = "Palladius's Lausiac History is a Greek collection of lives and sayings of monks in Egypt and Palestine, written about 420 and dedicated to the chamberlain Lausus.",
        details = "Palladius had lived among the monks of Egypt before he became a bishop, and he dedicated the book to Lausus, the chamberlain of the emperor Theodosius II. The year 420 is the usual date for the book, and its name comes from the man who received it.",
        rare = true,
    ),

    entry(
        databaseId = 162,
        kind = Kind.LANGUAGE,
        section = "Chinese",
        title = "Buddhabhadra's Flower Garland Sutra in sixty fascicles, a Chinese translation of about 420",
        originalTitle = "Da fangguang fo huayan jing",
        author = "Buddhabhadra and his team",
        year = 420,
        summary = "Buddhabhadra and his team rendered the Flower Garland Sutra into Chinese in sixty fascicles, a long translation completed around 420.",
        details = "Buddhabhadra was an Indian monk who led the team, and the sixty-fascicle text is the long version of the sutra. Accounts give the completion between 418 and 422, and one gives 421, so 420 is the date used here.",
        rare = true,
    ),

    entry(
        databaseId = 162,
        kind = Kind.LANGUAGE,
        section = "Chinese",
        title = "Dharmakshema's Chinese Nirvana Sutra, a long translation made at Guzang in 421",
        originalTitle = "Da banniepan jing",
        author = "Dharmakshema",
        year = 421,
        summary = "Dharmakshema's Chinese version of the Nirvana Sutra was made in 421 at Guzang, the capital of the Northern Liang in what is now Gansu.",
        details = "The translation was made under the patronage of Juqu Mengxun, the ruler of the Northern Liang, and the sutra was widely studied in the decades that followed. Catalogues give different fascicle counts for it, so the record names no number of fascicles.",
        rare = true,
    ),

    entry(
        databaseId = 162,
        kind = Kind.LANGUAGE,
        section = "Chinese",
        title = "Liu Yiqing's Shishuo xinyu, anecdotes of famous people from the late Han to the Jin, compiled about 430",
        originalTitle = "Shishuo xinyu",
        author = "Liu Yiqing",
        year = 430,
        summary = "Liu Yiqing's Shishuo xinyu is a collection of short anecdotes about famous men and women of the late Han and the Jin, compiled at the Liu Song court about 430.",
        details = "The anecdotes are grouped into thirty-six sections by theme, such as speech, virtue and quick wit, and the book is written in a terse classical Chinese. Liu Yiqing (403 to 444) was a prince of the Liu Song house, and about 430 is the usual date for the compilation.",
    ),

)
