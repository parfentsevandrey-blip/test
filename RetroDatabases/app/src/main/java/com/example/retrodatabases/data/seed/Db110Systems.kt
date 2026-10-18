package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** States and courts of 945 to 947: the Khitan takeover of Kaifeng, Liu Zhiyuan's Later Han, the Buyids in Baghdad and the fall of the Min kingdom. */
val db110Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 110,
        kind = Kind.SYSTEM,
        section = "China",
        title = "The Khitan Liao state takes Kaifeng in 947 and ends the Later Jin dynasty",
        originalTitle = "Da Liao",
        author = "Yelu Deguang, Emperor Taizong of Liao, and Shi Chonggui of Later Jin",
        year = 947,
        summary = "The Khitan emperor Taizong of Liao entered Kaifeng in 947, took the last Later Jin emperor north, and ended the Later Jin dynasty.",
        details = "Shi Chonggui, the last Later Jin emperor, surrendered at Kaifeng, and the Khitan held the Chinese capital only briefly before they withdrew north. The gap they left opened the way for Liu Zhiyuan, who founded the Later Han in the same year.",
    ),

    entry(
        databaseId = 110,
        kind = Kind.SYSTEM,
        section = "China",
        title = "Liu Zhiyuan proclaims the Later Han dynasty at Taiyuan in 947",
        originalTitle = "Hou Han",
        author = "Liu Zhiyuan, Emperor Gaozu of Later Han",
        year = 947,
        summary = "Liu Zhiyuan, a general of the Shatuo, proclaimed the Later Han dynasty in 947 and then took Kaifeng after the Khitan withdrew.",
        details = "The new state was one of the short-lived Five Dynasties of north China, and it ended in 951 when the Later Zhou replaced it. Its founding came straight after the Khitan withdrawal from Kaifeng, so the two events of 947 belong to one story.",
        rare = true,
    ),

    entry(
        databaseId = 110,
        kind = Kind.SYSTEM,
        section = "Iraq",
        title = "The Buyid commander Ahmad ibn Buya enters Baghdad in 945 and takes the title Mu'izz al-Dawla",
        originalTitle = "Mu'izz al-Dawla",
        author = "Ahmad ibn Buya, Mu'izz al-Dawla",
        year = 945,
        summary = "In 945 the Buyid commander Ahmad ibn Buya took Baghdad, and the Abbasid caliph became a figurehead under his office of amir al-umara.",
        details = "The Buyids were Daylamite soldiers from northern Iran, and the title amir al-umara, commander of commanders, gave the new ruler control of the caliph's government. The caliphs kept their religious title for more than a century while the Buyid amirs held the real power.",
    ),

    entry(
        databaseId = 110,
        kind = Kind.SYSTEM,
        section = "China",
        title = "The Southern Tang state conquers the Min kingdom of Fujian in 945",
        originalTitle = "Min",
        author = "Li Jing of Southern Tang",
        year = 945,
        summary = "Southern Tang armies conquered the Min kingdom of Fujian in 945, ending a state that had ruled the province since 909.",
        details = "The Min kingdom was one of the regional states of the Ten Kingdoms, and its fall brought Fujian under Southern Tang rule. The year 945 is the usual date for the conquest, and the details of the campaign are not given here.",
        rare = true,
    ),

)
