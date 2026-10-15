package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1720 to 1730: an academy, a silk factory, a petition box, a colonial garden and an observatory of stone. */
val db32Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 32,
        kind = Kind.SYSTEM,
        section = "Academies",
        title = "The St Petersburg Academy of Sciences",
        originalTitle = "Imperatorskaya Akademiya nauk",
        author = "Peter the Great",
        year = 1724,
        summary = "A state academy of sciences founded by decree of Peter the Great, whose work began only after his death.",
        details = "The decree is dated 28 January 1724, and the academy's work began after Peter's death in 1725. The academy was set up by the state to bring scholars into the empire, and it was a new kind of body next to the older academies of Paris and London.",
    ),

    entry(
        databaseId = 32,
        kind = Kind.SYSTEM,
        section = "Textiles",
        title = "Lombe's Derby silk mill",
        originalTitle = "Derby silk mill",
        author = "Thomas Lombe and John Lombe",
        year = 1721,
        summary = "A water-powered silk mill at Derby that ran machinery for throwing silk under one roof, often called an early factory.",
        details = "The mill was built between about 1717 and 1721, and 1721 is the usual date for its opening. The machinery was copied from Italian throwing mills after a visit to Piedmont, and accounts differ on whether the visit was in 1716 or 1717 and on whether the patent year was 1718 or 1719.",
    ),

    entry(
        databaseId = 32,
        kind = Kind.SYSTEM,
        section = "Government",
        title = "Yoshimune's suggestion box for petitions",
        originalTitle = "Me-yasu-bako",
        author = "Tokugawa Yoshimune",
        year = 1721,
        summary = "A box set up in Edo so that people could post written petitions and suggestions for the shogunate to read.",
        details = "Yoshimune set up the box in 1721, and the record for that year calls it a suggestion box devised in Japan. Its contents were sorted by officials, and the box is a well-known example of a channel for public comment in Tokugawa Japan.",
        rare = true,
    ),

    entry(
        databaseId = 32,
        kind = Kind.SYSTEM,
        section = "Gardens",
        title = "Bartram's garden near Philadelphia",
        originalTitle = "Bartram's Garden",
        author = "John Bartram",
        year = 1728,
        summary = "A garden on the Schuylkill river where a Philadelphia botanist grew North American plants for study and for sending to Europe.",
        details = "Bartram set the garden up in September 1728, and it became a place where American plants were grown and sent to European collectors. It is among the early botanical gardens in the American colonies.",
        rare = true,
    ),

    entry(
        databaseId = 32,
        kind = Kind.SYSTEM,
        section = "Observatories",
        title = "Jai Singh's stone observatory at Delhi",
        originalTitle = "Jantar Mantar",
        author = "Sawai Jai Singh II",
        year = 1724,
        summary = "A complex of large masonry instruments for measuring the positions of the sun and stars, built at Delhi under a Mughal patron.",
        details = "The Delhi complex is usually dated to about 1724, and it was prompted by a request from the Mughal emperor Muhammad Shah. Accounts differ on the start of the work, which is given as anywhere from 1719 to 1723, and construction of the five observatories Jai Singh built ran from about 1724 to 1735.",
    ),

)
