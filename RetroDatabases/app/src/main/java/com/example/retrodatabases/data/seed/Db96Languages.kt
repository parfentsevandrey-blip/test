package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Language records of 1080 to 1090: a Chinese miscellany, a Chinese chronicle, a Persian book of advice and a canon-law collection. */
val db96Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 96,
        kind = Kind.LANGUAGE,
        section = "China",
        title = "The Dream Pool Essays, a Chinese miscellany of natural knowledge and technical notes",
        originalTitle = "Mengxi bitan",
        author = "Shen Kuo",
        year = 1088,
        summary = "Shen Kuo's Dream Pool Essays gathers short notes on the calendar, instruments, medicine and the natural world, and is usually dated to 1088.",
        details = "Shen Kuo wrote the book in retirement near Zhenjiang, where he died in 1095, and its entries range over many subjects in short notes. English versions of the title differ, and 1088 is the usual date given for the book as a whole.",
    ),

    entry(
        databaseId = 96,
        kind = Kind.LANGUAGE,
        section = "China",
        title = "Sima Guang's Comprehensive Mirror to Aid in Government, a chronicle from 403 BC to 959",
        originalTitle = "Zizhi tongjian",
        author = "Sima Guang and his team of assistants",
        year = 1084,
        summary = "Sima Guang presented his Comprehensive Mirror to Aid in Government to the throne in 1084, a chronicle that runs from 403 BC to 959 AD.",
        details = "The chronicle was compiled with a team of assistants under the patronage of Emperor Shenzong, and it is arranged year by year. It opens in 403 BC, the traditional start of the Warring States period, and ends in 959 AD.",
    ),

    entry(
        databaseId = 96,
        kind = Kind.LANGUAGE,
        section = "Persia",
        title = "Qabus-nama, a Persian book of advice a prince wrote for his son",
        originalTitle = "Qabus-nama",
        author = "Kay Kavus ibn Iskandar",
        year = 1082,
        summary = "Kay Kavus ibn Iskandar, a prince of Gurgan, wrote the Qabus-nama in Persian as advice to his son, and it is dated to 1082 or 1083.",
        details = "The book gives advice on conduct, learning and a ruler's duties, and it belongs to the tradition of advice books for princes. Accounts give the dating as 1082 or 1083, and the son who received the advice was Gilanshah.",
        rare = true,
    ),

    entry(
        databaseId = 96,
        kind = Kind.LANGUAGE,
        section = "Rome",
        title = "Deusdedit's Collection of Canons, a church-law collection for the papal reform",
        originalTitle = "Collectio canonum",
        author = "Cardinal Deusdedit",
        year = 1087,
        summary = "Cardinal Deusdedit's Collection of Canons gathers church law in four books for the papal reform, and it is usually dated to 1087.",
        details = "The four books cover the authority of the Roman church, the Roman clergy, church affairs and the freedom and immunities of the clergy, and the work was dedicated to the Roman clergy and to Victor III. Accounts put its composition between 1083 and 1087, with 1087 as the finishing year, and some give 1078 as the earlier start.",
        rare = true,
    ),

)
