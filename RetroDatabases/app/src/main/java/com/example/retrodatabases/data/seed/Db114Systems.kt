package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 900 to 910: Later Baekje and Later Goguryeo in Korea, the Former Shu of Sichuan, the Min of Fujian, and the Fatimid caliphate in North Africa. */
val db114Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 114,
        kind = Kind.SYSTEM,
        section = "Korea",
        title = "Gyeon Hwon's Later Baekje, a kingdom proclaimed at Wansan in 900",
        originalTitle = "Hubaekje",
        author = "Gyeon Hwon",
        year = 900,
        summary = "Gyeon Hwon proclaimed a new kingdom of Baekje in 900 at Wansan, the place now called Jeonju, in the south-west of the Korean peninsula.",
        details = "Some accounts date the start of his rise to 892, when he took the military governorship of Muju, so 900 is the year of the proclamation rather than of his first step. The kingdom ended in 936, when Wang Geon's Goryeo took it over.",
        rare = true,
    ),

    entry(
        databaseId = 114,
        kind = Kind.SYSTEM,
        section = "Korea",
        title = "Gung Ye's Later Goguryeo, founded in 901 by a Buddhist monk",
        originalTitle = "Majin, later Taebong",
        author = "Gung Ye",
        year = 901,
        summary = "Gung Ye founded the state of Later Goguryeo in 901 in the north of the Korean peninsula, and in 918 Wang Geon took over its power.",
        details = "The state was called Majin from 904 and took the name Taebong in 911, so the founding year and the name changes are separate dates. Gung Ye's rule ended in 918, when Wang Geon took over and the state became part of Goryeo.",
        rare = true,
    ),

    entry(
        databaseId = 114,
        kind = Kind.SYSTEM,
        section = "China",
        title = "Wang Jian's Former Shu, a state of Sichuan founded in 907 at Chengdu",
        originalTitle = "Qian Shu",
        author = "Wang Jian",
        year = 907,
        summary = "Wang Jian founded the Former Shu state in Sichuan in 907, with Chengdu as its capital, in the same year that the Tang dynasty ended.",
        details = "Former Shu was one of the regional states of the Ten Kingdoms period, and it fell to the Later Tang in 925. Its Chinese name is Qian Shu.",
        rare = true,
    ),

    entry(
        databaseId = 114,
        kind = Kind.SYSTEM,
        section = "North Africa",
        title = "The Fatimid caliphate, founded in North Africa by Ubayd Allah al-Mahdi",
        originalTitle = "Al-Dawla al-Fatimiyya",
        author = "Ubayd Allah al-Mahdi",
        year = 909,
        summary = "The Fatimid caliphate began under Ubayd Allah al-Mahdi in North Africa in 909, and it ended in 1171 under Saladin.",
        details = "Accounts differ on the start, since some date the dynasty to 909 with Raqqada as its first capital, while others date the caliphal declaration to January 910 after Ubayd Allah entered Qayrawan. The dynasty took Egypt in the tenth century and ended in 1171.",
    ),

    entry(
        databaseId = 114,
        kind = Kind.SYSTEM,
        section = "China",
        title = "Wang Shenzhi's Min kingdom of Fujian, set up in 909 with Fuzhou as its seat",
        originalTitle = "Min",
        author = "Wang Shenzhi",
        year = 909,
        summary = "Wang Shenzhi set up a kingdom of Min in Fujian in 909 with Fuzhou as its seat, and it was one of the Ten Kingdoms of southern China.",
        details = "The state is usually dated from 909, when its founder was given the title of Prince of Min, and it lasted until the 940s, when a neighbouring southern state took it over. Its capital was Fuzhou.",
        rare = true,
    ),

)
