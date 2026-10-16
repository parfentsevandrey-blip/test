package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1390 to 1400: a cathedral clock, a dated astrolabe at Oxford and a set of painted cards for a French king. */
val db65Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 65,
        kind = Kind.DEVICE,
        section = "Clocks",
        title = "The Wells Cathedral clock, a weight-driven clock of about 1390",
        originalTitle = "Wells Cathedral clock",
        author = "Makers unknown, for the cathedral chapter",
        year = 1390,
        summary = "The Wells Cathedral clock is a weight-driven clock of about 1390 with a verge and foliot escapement, and it is among the oldest surviving mechanical clocks.",
        details = "The Science Museum in London dates it to 1392 and shows the workings there, while most accounts give about 1390, so the date is a range. Historians dispute whether Wells or Salisbury holds the oldest working clock, since Salisbury is dated to about 1386.",
    ),

    entry(
        databaseId = 65,
        kind = Kind.DEVICE,
        section = "Astronomy",
        title = "The Merton astrolabe dated 1390, a large instrument from Oxford",
        originalTitle = "Merton College astrolabe",
        author = "Unknown instrument maker",
        year = 1390,
        summary = "A large astrolabe in the Merton College collection at Oxford bears the date 1390 on its plate.",
        details = "The instrument has lost its rete and alidade, and its engraving was redone at some later time, so some inscriptions may not be original. Accounts differ on how much of the instrument is medieval, and the date is read from the plate as it now stands.",
        rare = true,
    ),

    entry(
        databaseId = 65,
        kind = Kind.DEVICE,
        section = "Games",
        title = "The painted cards of Charles VI of France, paid for in 1392",
        originalTitle = "Jeu de Charles VI",
        author = "Jacquemin Gringonneur",
        year = 1392,
        summary = "In 1392 the Paris painter Jacquemin Gringonneur was paid for a set of gilded playing cards for King Charles VI of France.",
        details = "The payment record names the painter and the king, and only a few of the painted cards survive. Accounts differ on how many packs the king's order covered, so the number of cards is not fixed by the record.",
        rare = true,
    ),

)
