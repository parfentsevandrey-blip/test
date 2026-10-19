package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Political systems of 790 to 800: the Council of Frankfurt, Irene's sole rule, the Avar treasure, the Japanese seii taishogun and the attack on Pope Leo III. */
val db125Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 125,
        kind = Kind.SYSTEM,
        section = "Frankish",
        title = "The Council of Frankfurt of 794, which rejected the image decrees of the Second Council of Nicaea and condemned adoptionism",
        originalTitle = "Synodus Francofurtensis",
        author = "Charlemagne and the bishops of his realm",
        year = 794,
        summary = "The council held at Frankfurt in 794 under Charlemagne rejected the image decrees of the Second Council of Nicaea and condemned adoptionism.",
        details = "Its decisions set the Frankish position against Nicaea's endorsement of the veneration of images, and it also condemned the adoptionist teaching of Felix of Urgel. The date 794 is the usual one for the council, and the Libri Carolini, written in the same years, give the Frankish case against Nicaea.",
    ),

    entry(
        databaseId = 125,
        kind = Kind.SYSTEM,
        section = "Byzantine",
        title = "Irene has her son Constantine VI blinded in 797 and rules the Byzantine empire alone",
        originalTitle = "Eirene",
        author = "Irene, Byzantine empress",
        year = 797,
        summary = "In 797 the empress Irene had her son Constantine VI blinded and took sole power in Constantinople, which she kept until 802.",
        details = "Constantine VI had been emperor since childhood, and his blinding ended his rule and left his mother as the only ruler of the empire. The usual date for the blinding is 797, and her sole rule lasted until 802, when Nikephoros I took the throne.",
    ),

    entry(
        databaseId = 125,
        kind = Kind.SYSTEM,
        section = "Frankish",
        title = "The Avar treasure, gold and silver taken from the Avar ring and sent to Aachen in 796",
        originalTitle = "The Avar treasure",
        author = "Charlemagne and Pippin of Italy",
        year = 796,
        summary = "In 796 Charlemagne's son Pippin of Italy sent the treasure of the Avar ring to Aachen after the Frankish campaigns against the Avars.",
        details = "The Franks had campaigned against the Avars from 791, and the Avar ring, the circular fortresses where the khagan kept his wealth, was overrun in 796. The sources give no exact total for the treasure, so no weight or value is claimed here.",
    ),

    entry(
        databaseId = 125,
        kind = Kind.SYSTEM,
        section = "Japanese",
        title = "Sakanoue no Tamuramaro is named seii taishogun, the great general against the Emishi, in 797",
        originalTitle = "Seii taishogun",
        author = "Sakanoue no Tamuramaro",
        year = 797,
        summary = "In 797 Sakanoue no Tamuramaro was given the title seii taishogun, the great general who subdues the barbarians, for the campaigns against the Emishi.",
        details = "The title was given for the wars against the Emishi in the north of Honshu. Accounts differ on whether he was the first to hold it, since some name Otomo no Otomaro as an earlier holder, so the claim of first use is left open here.",
        rare = true,
    ),

    entry(
        databaseId = 125,
        kind = Kind.SYSTEM,
        section = "Papal",
        title = "Pope Leo III is attacked in Rome in 799 and flees to Charlemagne at Paderborn",
        originalTitle = "The attack on Pope Leo III, 799",
        author = "Pope Leo III",
        year = 799,
        summary = "In April 799 Pope Leo III was attacked in Rome during a procession, and he fled north to Charlemagne's court at Paderborn.",
        details = "The sources describe an attempt to blind the pope and cut out his tongue, and accounts differ on who planned the attack. He reached Paderborn later in 799, and Charlemagne sent him back to Rome under escort, which prepared the way for the coronation of 800.",
    ),

)
