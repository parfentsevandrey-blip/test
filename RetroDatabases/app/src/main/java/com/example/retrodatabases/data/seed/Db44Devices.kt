package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1600 to 1610: a telescope patent, Galileo's compass, Gilbert's electrical needle and sphere, a striking clock, a land yacht, a copying frame and a printing press. */
val db44Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 44,
        kind = Kind.DEVICE,
        section = "Optics",
        title = "Lipperhey's telescope patent of 1608",
        originalTitle = "Kijker",
        author = "Hans Lippershey",
        year = 1608,
        summary = "Lippershey asked the States General at The Hague in October 1608 for a patent on an instrument of two lenses that made distant things look near.",
        details = "The States General did not grant the patent, and Jacob Metius of Alkmaar asked for one in the same year. Accounts differ on who built the first such instrument, and the spyglass spread quickly across Europe in the years that followed.",
    ),

    entry(
        databaseId = 44,
        kind = Kind.DEVICE,
        section = "Instruments",
        title = "Galileo's geometric and military compass",
        originalTitle = "Le operazioni del compasso geometrico et militare",
        author = "Galileo Galilei",
        year = 1606,
        summary = "Galileo's compass of 1606 was a pair of scaled arms for dividing lines, reading gunnery ranges and solving geometry problems.",
        details = "The book describing it was printed at Padua in 1606, but Galileo's own account dates the invention about a decade earlier, so accounts differ on its date. Students of mathematics and officers of artillery used such compasses to scale figures and to set the range of a gun.",
    ),

    entry(
        databaseId = 44,
        kind = Kind.DEVICE,
        section = "Electricity",
        title = "Gilbert's versorium, the first electroscope",
        originalTitle = "De magnete",
        author = "William Gilbert",
        year = 1600,
        summary = "Gilbert's versorium of 1600 was a light needle on a pivot that turned toward rubbed amber and other substances, the earliest electric instrument.",
        details = "Gilbert described it in his De magnete of 1600, and it showed that many substances draw light bodies once they are rubbed. Gilbert gave the attraction the name electric, from the Greek word for amber.",
    ),

    entry(
        databaseId = 44,
        kind = Kind.DEVICE,
        section = "Magnetism",
        title = "Gilbert's terrella, the lodestone earth",
        originalTitle = "De magnete",
        author = "William Gilbert",
        year = 1600,
        summary = "Gilbert's terrella was a sphere of lodestone shaped to stand for the earth, so that a compass needle set on it behaved as it does on the earth.",
        details = "Gilbert used it in his experiments and described the results in his De magnete of 1600, printed in London. He used the sphere to argue that the earth is itself a great magnet, though many of the causes he proposed were later abandoned.",
    ),

    entry(
        databaseId = 44,
        kind = Kind.DEVICE,
        section = "Clocks",
        title = "Ricci's striking clock for the Wanli emperor",
        originalTitle = "Presentation clock for the Wanli emperor",
        author = "Matteo Ricci",
        year = 1601,
        summary = "In 1601 Matteo Ricci gave the Wanli emperor a mechanical clock with a striking mechanism, one of the first European clocks at the Ming court.",
        details = "The gift came when Ricci reached Beijing in 1601, and it was one of several European presents made to the court. Clocks of this kind were a novelty at the Ming court in those years.",
    ),

    entry(
        databaseId = 44,
        kind = Kind.DEVICE,
        section = "Vehicles",
        title = "Stevin's land yacht for Prince Maurice",
        originalTitle = "Currus veliferi",
        author = "Simon Stevin",
        year = 1602,
        summary = "Simon Stevin built a wind-powered sail carriage for Prince Maurice of Nassau that ran along the beach at Scheveningen.",
        details = "Accounts differ on the date, with the trials given as autumn 1600 in one account and spring 1602 in another, while the only surviving record is de Gheyn's engraving printed in 1603. The engraving shows a run from Scheveningen to Petten in two hours over fourteen Dutch miles.",
        rare = true,
    ),

    entry(
        databaseId = 44,
        kind = Kind.DEVICE,
        section = "Drawing instruments",
        title = "Scheiner's pantograph, the copying frame",
        originalTitle = "Pantographice seu ars delineandi",
        author = "Christoph Scheiner",
        year = 1603,
        summary = "Scheiner's pantograph is a jointed frame of rods that copies a drawing at a fixed scale, dated by him to about 1603 and described in his book on the art of drawing.",
        details = "The book was printed in Rome in 1631, though a drawing of the device had already appeared in Scheiner's Rosa Ursina of 1630. Accounts put the first build at 1603, and some sources give a window of 1603 to 1605 for the invention.",
    ),

    entry(
        databaseId = 44,
        kind = Kind.DEVICE,
        section = "Printing",
        title = "The Jesuit printing press at Nagasaki",
        originalTitle = "Kirishitan-ban",
        author = "The Jesuit mission in Japan",
        year = 1603,
        summary = "The Jesuit mission press at Nagasaki printed books in Roman letters in Japan, among them the Japanese-Portuguese dictionary of 1603.",
        details = "A summary of the press gives its working years in Japan as 1590 to 1610, and the Jesuits later moved their printing to Macao. Its books were printed in Roman letters alone, with no kanji or kana.",
        rare = true,
    ),

)
