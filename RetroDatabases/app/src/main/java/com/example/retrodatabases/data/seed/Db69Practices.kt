package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1350 to 1360: the Yuan paper-money reform, the chevauchee raid of 1355, free companies in France, a Cairo endowment and Venetian galley convoys, with approximate dates where they are uncertain. */
val db69Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 69,
        kind = Kind.PRACTICE,
        section = "War",
        title = "The chevauchee, a mounted raid of the Hundred Years' War, in 1355",
        originalTitle = "Chevauchee",
        author = "English and Gascon commanders",
        year = 1355,
        summary = "The chevauchee, a mounted raid that plundered and burned the enemy's land, was a standard English method of war in France, and the raid through Languedoc in 1355 is a well-known example.",
        details = "The method is documented across the 1340s and 1350s, and the dates of the stages of the 1355 raid are approximate. The chevauchee was meant to show that the king could not protect his subjects, and it was repeated in many campaigns of the war.",
    ),

    entry(
        databaseId = 69,
        kind = Kind.PRACTICE,
        section = "Money",
        title = "The Zhizheng paper-money reform of the Yuan, about 1350",
        originalTitle = "Zhizheng reform of the currency",
        author = "The Yuan government",
        year = 1350,
        summary = "The Yuan government reformed its currency about 1350, issuing new paper notes and copper coins, and the inflation that followed is linked with later unrest.",
        details = "The reform is usually dated to the tenth year of the Zhizheng era, which is 1350, and the date is approximate. Its effect on prices is discussed in later Chinese histories.",
    ),

    entry(
        databaseId = 69,
        kind = Kind.PRACTICE,
        section = "War",
        title = "Free companies, bands of unpaid soldiers in France after 1356",
        originalTitle = "Grandes Compagnies",
        author = "Mercenary captains of the free companies",
        year = 1356,
        summary = "After the battle of Poitiers in 1356, bands of unpaid soldiers called free companies lived by plunder and ransom across the French countryside.",
        details = "The bands grew after the French defeat and were still active in the 1360s, and accounts differ on when the first companies formed, so 1356 marks only the start of their great spread. The practice of hiring out such bands became a standing feature of the Hundred Years' War.",
    ),

    entry(
        databaseId = 69,
        kind = Kind.PRACTICE,
        section = "Charity",
        title = "The waqf of Sultan Hasan's mosque-madrasa in Cairo, 1356 to 1363",
        originalTitle = "Waqf of the Sultan Hasan complex",
        author = "Sultan Hasan and his endowment",
        year = 1356,
        summary = "The mosque-madrasa of Sultan Hasan in Cairo was built between 1356 and 1363 and endowed as a waqf, a trust whose income paid for its staff and upkeep.",
        details = "The building dates are approximate: the complex is given as 757 to 765 AH, which is 1356 to 1363 in the Christian calendar, and other dates given for it are 758 to 764 AH. Waqf endowments were the usual way Mamluk rulers funded their mosques and schools.",
    ),

    entry(
        databaseId = 69,
        kind = Kind.PRACTICE,
        section = "Trade",
        title = "The Venetian galley convoys, called mude, on the trade routes",
        originalTitle = "Mude",
        author = "The Venetian Senate and galley captains",
        year = 1350,
        summary = "Venice sent armed galley convoys, called mude, along fixed trade routes, and the system is recorded from the 1300s.",
        details = "The date of the first convoy rules is uncertain, so 1350 is an approximate year within the system's early history. The convoys carried merchants' goods and sailed under orders from the Senate.",
    ),

)
