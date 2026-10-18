package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1031 to 1040: a law on fiefs in Italy, a Tangut empire, a battle that ended Ghaznavid rule in Khorasan, the end of a caliphate and a northern realm that broke up. */
val db101Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 101,
        kind = Kind.SYSTEM,
        section = "Italy",
        title = "Conrad II's Constitutio de feudis, a law that made lesser fiefs in Italy heritable",
        originalTitle = "Constitutio de feudis",
        author = "Conrad II",
        year = 1037,
        summary = "Conrad II issued the Constitutio de feudis at Pavia on 28 May 1037, a law that made the fiefs of lesser vassals in Italy heritable.",
        details = "He issued it while besieging Milan, and it is also called the Edictum de beneficiis regni Italici. It settled a rift between the capitanei of Milan and the lesser vassals, but the calm it brought was short-lived, and 28 May 1037 fell on the eve of Pentecost.",
        rare = true,
    ),

    entry(
        databaseId = 101,
        kind = Kind.SYSTEM,
        section = "Western Xia",
        title = "Li Yuanhao's imperial title and the Tangut state of Western Xia from 1038",
        originalTitle = "Da Xia",
        author = "Li Yuanhao, Emperor Jingzong",
        year = 1038,
        summary = "In 1038 Li Yuanhao took the imperial title, and the Tangut state of Western Xia became an empire on the northwest frontier of the Song.",
        details = "The Tangut script had been issued two years earlier under the same ruler, and the state fought the Song in the early 1040s. Accounts differ on the start of the dynasty: 1038 is the usual date for the imperial title, but some scholars begin the state in 982.",
        rare = true,
    ),

    entry(
        databaseId = 101,
        kind = Kind.SYSTEM,
        section = "Khorasan",
        title = "The Seljuq victory at Dandanaqan, which ended Ghaznavid rule in Greater Khorasan",
        originalTitle = "Dandanqan",
        author = "Tughril and Chaghri, against Mas'ud I of Ghazni",
        year = 1040,
        summary = "On 23 May 1040 the Turkmen army of the Seljuq brothers Tughril and Chaghri beat the Ghaznavid army of Mas'ud I near Dandanaqan, and Seljuq power spread across Khorasan.",
        details = "The battle was fought in desert country between Merv and Sarakhs, and the Ghaznavid army had been weakened by hunger and drought before it engaged. Estimates of the Ghaznavid side run from 16,000 to 50,000 men, Mas'ud lost his hold on Khorasan, and a revolt in 1041 ended his life.",
        rare = true,
    ),

    entry(
        databaseId = 101,
        kind = Kind.SYSTEM,
        section = "Al-Andalus",
        title = "The end of the Umayyad caliphate of Cordoba, when its notables deposed Hisham III",
        originalTitle = "Caliphate of Cordoba",
        author = "The notables of Cordoba",
        year = 1031,
        summary = "In 1031 the notables of Cordoba deposed Hisham III and ended the Umayyad caliphate, and al-Andalus broke into many small taifa kingdoms.",
        details = "The caliphate had dated from 929, when Abd al-Rahman III took the title, and it had been torn by civil war since 1009. The taifa kingdoms that replaced it were small and often at war with one another.",
    ),

    entry(
        databaseId = 101,
        kind = Kind.SYSTEM,
        section = "England and Scandinavia",
        title = "Cnut's North Sea realm, which broke apart after his death in 1035",
        originalTitle = "North Sea Empire",
        author = "Cnut the Great",
        year = 1035,
        summary = "Cnut's realm of England, Denmark and Norway broke apart after his death in 1035, when his English and Danish lands passed to his sons and Norway rose against Danish rule.",
        details = "Cnut had ruled England from 1016 and Denmark from 1018, and the union rested on his own authority and the loyalty of his earls. The Norwegian rising brought Magnus, son of St Olaf, to the throne.",
    ),

)
