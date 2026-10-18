package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1043 to 1046: Fan Zhongyan's reforms, a border settlement with Xia, a school at Constantinople and the synod of Sutri. */
val db100Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 100,
        kind = Kind.SYSTEM,
        section = "China",
        title = "The Qingli reforms of Fan Zhongyan, ten proposals for better government in 1043",
        originalTitle = "Qingli xinzheng",
        author = "Fan Zhongyan under Emperor Renzong of Song",
        year = 1043,
        summary = "The Qingli reforms were ten proposals that Fan Zhongyan put to Emperor Renzong in 1043 to reform the officials, the examinations and the frontier defences of the Song.",
        details = "Fan rose to a high council post in 1043, and the reforms met opposition from entrenched officials and were reversed in 1044. Historians differ on how much of the programme was carried out in practice, so the reforms are best known through their proposals.",
    ),

    entry(
        databaseId = 100,
        kind = Kind.SYSTEM,
        section = "Frontier",
        title = "The Qingyuan settlement of 1044, a border peace with the Tangut state of Xia",
        originalTitle = "The Qingyuan settlement of 1044, a border peace with the Tangut state of Xia",
        author = "The Song and Western Xia courts",
        year = 1044,
        summary = "The Qingyuan settlement ended the fighting between the Song and the Tangut state of Western Xia in 1044, with the Song agreeing to yearly gifts of silver and silk.",
        details = "Known in English as the Treaty of Qingyuan, the settlement followed fighting that had begun in 1040 and set the terms of a border peace that lasted for several decades. The yearly gifts were the price the Song paid for that peace.",
    ),

    entry(
        databaseId = 100,
        kind = Kind.SYSTEM,
        section = "Byzantium",
        title = "The imperial school at Constantinople, with law and philosophy faculties, founded in 1045",
        originalTitle = "The imperial school at Constantinople, with law and philosophy faculties, founded in 1045",
        author = "Constantine IX Monomachos, with Michael Psellos and John Xiphilinos",
        year = 1045,
        summary = "In 1045 Constantine IX Monomachos set up a school at Constantinople with a philosophy faculty and a law school, to train the men who would serve the state.",
        details = "Michael Psellos was given the philosophy side, and John Xiphilinos was one of the leading figures on the side of law. One account dates the creation of the school to 1045, and historians differ on whether it was a new foundation or a refounding of older teaching in the capital.",
        rare = true,
    ),

    entry(
        databaseId = 100,
        kind = Kind.SYSTEM,
        section = "Papacy",
        title = "The synod of Sutri, which dealt with three rival claimants to the papacy in 1046",
        originalTitle = "The synod of Sutri, which dealt with three rival claimants to the papacy in 1046",
        author = "Henry III of Germany",
        year = 1046,
        summary = "Henry III summoned a synod that opened at Sutri on 20 December 1046 to deal with the rival claimants to the papacy.",
        details = "Sylvester III and Gregory VI came to the synod in person, while Benedict IX stayed away and was removed in his absence, and Clement II was then enthroned on 25 December 1046. Accounts differ on whether Gregory VI resigned or was deposed, and on whether the synod or Henry himself removed the three claimants.",
    ),

)
