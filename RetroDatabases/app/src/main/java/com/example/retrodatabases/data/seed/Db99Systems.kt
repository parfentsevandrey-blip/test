package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1054 to 1059: the Great Schism, Tughril's entry into Baghdad, the Council of Coyanza, a Song examination and a papal election decree. */
val db99Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 99,
        kind = Kind.SYSTEM,
        section = "Church",
        title = "The Great Schism of 1054, when Rome and Constantinople excommunicated each other",
        originalTitle = "Great Schism of 1054",
        author = "Cardinal Humbert and Patriarch Michael Cerularius",
        year = 1054,
        summary = "In July 1054 papal legates led by Cardinal Humbert laid a bull of excommunication on the altar of Hagia Sophia, and Patriarch Michael Cerularius answered by excommunicating them, so each church cut off the other.",
        details = "The bull was laid on the altar on 16 July 1054, and the legates then left the city. Historians debate whether 1054 marks the start of the split or one stage in a longer break, which hardened over the following century and still divides the two churches.",
    ),

    entry(
        databaseId = 99,
        kind = Kind.SYSTEM,
        section = "Baghdad",
        title = "Tughril's entry into Baghdad, which gave the Seljuks the sultanate in 1055",
        originalTitle = "Entry of Tughril Beg into Baghdad",
        author = "Tughril Beg and the caliph al-Qaim",
        year = 1055,
        summary = "In December 1055 the Seljuk leader Tughril Beg entered Baghdad, and the Abbasid caliph gave him the title of sultan, which made the Seljuks the real power behind the caliphs.",
        details = "The caliph al-Qaim had been under the control of the Buyid rulers of Baghdad, and Tughril's arrival changed the balance of power in the caliphate. Historians differ on how much real power the caliphs kept after that.",
    ),

    entry(
        databaseId = 99,
        kind = Kind.SYSTEM,
        section = "Leon",
        title = "The Council of Coyanza, a Leonese assembly whose acts are dated 1055",
        originalTitle = "Concilium Coyanzense",
        author = "Ferdinand I of Leon and his queen",
        year = 1055,
        summary = "The royal couple of Leon convened a council at Coyanza whose acts ordered monasteries to keep the Rule of Saint Benedict and set the Roman rite in place of the Visigothic one.",
        details = "The Coimbra text is dated by the Spanish era 1093, which gives 1055, while the Oviedo text's era 1088 gives 1050, the year that older scholarship used. Historians also differ on whether the gathering was a royal court meeting, a council-court or a true council.",
        rare = true,
    ),

    entry(
        databaseId = 99,
        kind = Kind.SYSTEM,
        section = "Examinations",
        title = "The 1057 metropolitan examination, marked by Ouyang Xiu for plain prose",
        originalTitle = "Jiayou jinshi examination",
        author = "Ouyang Xiu and the Song court",
        year = 1057,
        summary = "The metropolitan examination of 1057 was marked under Ouyang Xiu, who favoured plain ancient-style prose over ornate parallel phrasing, and Su Shi was among those who passed.",
        details = "The examination falls in the Jiayou period of Emperor Renzong, and it is a standard point in the Song move toward plainer writing in the civil service tests. Later accounts of the marking differ in their details, so the names and essays of individual candidates should be read with care.",
        rare = true,
    ),

    entry(
        databaseId = 99,
        kind = Kind.SYSTEM,
        section = "Church law",
        title = "In nomine Domini, the 1059 decree that gave papal elections to the cardinal bishops",
        originalTitle = "In nomine Domini",
        author = "Pope Nicholas II and the Lateran synod",
        year = 1059,
        summary = "A Lateran synod in April 1059 under Pope Nicholas II issued a decree that gave the first choice of a pope to the cardinal bishops, with the rest of the clergy and people consenting.",
        details = "The decree left the king's part in the choice to a clause that kept an honour due to Henry IV, then a boy, and historians read that clause in different ways. It became a key text for later rules on papal elections.",
    ),

)
