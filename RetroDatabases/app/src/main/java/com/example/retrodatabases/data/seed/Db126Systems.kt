package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 780 to 790: Yang Yan's two-tax reform of 780, the Saxon capitulary of 782, the Second Council of Nicaea, Tassilo's deposition of 788 and the Admonitio generalis of 789. */
val db126Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 126,
        kind = Kind.SYSTEM,
        section = "Tang",
        title = "Yang Yan's two-tax reform of 780, which replaced the old levies with taxes on property paid twice a year",
        originalTitle = "Liangshui fa",
        author = "Yang Yan",
        year = 780,
        summary = "In 780 the Tang chancellor Yang Yan replaced the earlier levies with the two-tax system, which assessed households by their property and collected the tax in summer and autumn.",
        details = "The reform was carried out under Emperor Dezong and moved taxation away from the population counts and land allotments of the earlier system. Accounts differ on how far it was enforced in the provinces, and the two yearly collections gave the reform its name.",
        rare = true,
    ),

    entry(
        databaseId = 126,
        kind = Kind.SYSTEM,
        section = "Frankish",
        title = "Charlemagne's capitulary for the Saxon lands, which set the death penalty for refusing baptism",
        originalTitle = "Capitulatio de partibus Saxoniae",
        author = "Charlemagne",
        year = 782,
        summary = "The Capitulatio de partibus Saxoniae, Charlemagne's law for the Saxon lands, set the death penalty for refusing baptism and for church break-ins with theft or arson.",
        details = "It is conventionally dated to 782, although 785 is sometimes given and one recent study argues for a date near 795. Only one manuscript preserves the text, and accounts differ on how widely its penalties were applied, while the Frankish annals report the mass execution at Verden in the same year.",
        rare = true,
    ),

    entry(
        databaseId = 126,
        kind = Kind.SYSTEM,
        section = "Byzantine",
        title = "The Second Council of Nicaea, which restored the veneration of icons in 787 under the empress Irene",
        originalTitle = "Second Council of Nicaea",
        author = "Irene and Patriarch Tarasios",
        year = 787,
        summary = "The Second Council of Nicaea of 787 restored the veneration of icons, which had been forbidden in the iconoclast period, under the empress Irene and the patriarch Tarasios.",
        details = "The council was first called to Constantinople in 786, but soldiers loyal to the iconoclast cause broke up that session, so it met at Nicaea in 787. Its decrees were rejected in the Frankish lands, and the council of Frankfurt in 794 condemned them.",
    ),

    entry(
        databaseId = 126,
        kind = Kind.SYSTEM,
        section = "Frankish",
        title = "Tassilo III of Bavaria deposed at the Synod of Ingelheim in 788, which ended Bavaria's independent duchy",
        originalTitle = "Synod of Ingelheim",
        author = "Charlemagne",
        year = 788,
        summary = "In 788 Charlemagne deposed the Bavarian duke Tassilo III at Ingelheim, and Bavaria passed under Frankish counts, ending the duchy's independence.",
        details = "Tassilo was accused of breaking the oaths of loyalty he had sworn to the king, and the sources that report the trial are mainly Frankish. Accounts differ on the exact charges, but his deposition is usually taken as the end of the Bavarian duchy as an independent power.",
    ),

    entry(
        databaseId = 126,
        kind = Kind.SYSTEM,
        section = "Frankish",
        title = "Charlemagne's Admonitio generalis of 789, the church reform capitulary that called for schools in monasteries and bishoprics",
        originalTitle = "Admonitio generalis",
        author = "Charlemagne and his court clergy",
        year = 789,
        summary = "The Admonitio generalis of 789 was Charlemagne's church reform capitulary, setting out duties for priests, monks and bishops and calling for schools in monasteries and bishoprics.",
        details = "It asked priests to learn the Creed and the Lord's Prayer, and it laid down rules for monastic life and for the conduct of the clergy. Accounts differ on how much of its content came from Alcuin and other advisers, and historians still debate how far it was enforced.",
    ),

)
