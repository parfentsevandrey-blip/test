package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1480 to 1490: a Church Slavonic missal in Glagolitic letters, Italian books, a Greek Homer, a Hebrew Bible, a Latin Plato and a hidden algebra of powers. */
val db56Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 56,
        kind = Kind.LANGUAGE,
        section = "Slavonic",
        title = "The Glagolitic Missal of Venice",
        originalTitle = "Missale Romanum Glagolitice",
        author = "Andrea Torresani de Asula, printer",
        year = 1483,
        summary = "A Roman missal printed at Venice in 1483 in Glagolitic letters, the first book printed in the script that the Croatian Church used for Church Slavonic.",
        details = "The missal was printed at Venice in 1483 for the Croatian coast, and the Glagolitic script it uses was older than printing. Copies are few, and the reading of the text follows the Church Slavonic of the Croatian liturgy.",
        rare = true,
    ),

    entry(
        databaseId = 56,
        kind = Kind.LANGUAGE,
        section = "Italian",
        title = "Landino's Dante with the Florentine engravings",
        originalTitle = "La Comedia di Dante Alighieri con la spositione di Christophoro Landino",
        author = "Dante Alighieri, with Cristoforo Landino",
        year = 1481,
        summary = "Landino's commentary on Dante's Comedy was printed at Florence in 1481 with engravings for the cantos of the Inferno, one of the first illustrated printed editions of the poem.",
        details = "The Florentine printer Nicolaus Laurentii issued the edition in 1481, and the engravings are usually tied to Baccio Baldini, with a design attributed to Botticelli that is not firmly established. Sources count nineteen engravings for the first nineteen cantos, and accounts differ on the number of plates, since some later plates were tipped in loose.",
    ),

    entry(
        databaseId = 56,
        kind = Kind.LANGUAGE,
        section = "Greek",
        title = "Homer's Iliad and Odyssey printed at Florence in 1488",
        originalTitle = "Homeru Ilias kai Odysseia",
        author = "Homer, edited by Demetrios Chalcondyles",
        year = 1488,
        summary = "The Iliad and the Odyssey were printed in Greek at Florence in 1488, the first printed edition of Homer's two epics, edited by the Greek teacher Demetrios Chalcondyles.",
        details = "The Nerli brothers paid for the printing, and the Florentine edition is usually counted as the editio princeps of Homer. Bibliographic records give 8 or 9 December 1488 as the day, and sources name the printer differently, so accounts differ on those points.",
    ),

    entry(
        databaseId = 56,
        kind = Kind.LANGUAGE,
        section = "Notation",
        title = "Chuquet's Triparty on the science of numbers",
        originalTitle = "Le Triparty en la science des nombres",
        author = "Nicolas Chuquet",
        year = 1484,
        summary = "Chuquet's manuscript algebra of 1484 wrote powers and negative exponents in a notation of its own, and it stayed unprinted until the nineteenth century.",
        details = "Chuquet completed the Triparty at Lyon in 1484, and the book circulated only in manuscript. Its first printed edition was made in 1880, so its notation had no influence in print; accounts differ on how far its exponent rules were used by his contemporaries.",
        rare = true,
    ),

    entry(
        databaseId = 56,
        kind = Kind.LANGUAGE,
        section = "Hebrew",
        title = "Soncino's complete printed Hebrew Bible",
        originalTitle = "Tanakh",
        author = "Joshua Solomon Soncino, printer",
        year = 1488,
        summary = "The Soncino family printed the complete Hebrew Bible in 1488 at Soncino in Italy, and it is counted as the first full printed edition of the Hebrew Scriptures.",
        details = "The printing was done for Joshua Solomon Soncino by Abraham ben Hayyim, and the edition is dated to April 1488. The Naples Bible of 1491 to 1493 is usually counted as the next complete printed Hebrew Bible.",
    ),

    entry(
        databaseId = 56,
        kind = Kind.LANGUAGE,
        section = "Greek into Latin",
        title = "Ficino's Latin Plato printed at Florence",
        originalTitle = "Platonis opera omnia",
        author = "Plato, translated by Marsilio Ficino",
        year = 1484,
        summary = "Ficino's Latin version of Plato's dialogues was printed at Florence in 1484, and it made the whole of Plato available in print for European readers who had no Greek.",
        details = "The translation was made by Ficino for the Medici family, and the Florentine edition of 1484 is the first printed collection of Plato's works in Latin. Accounts differ on the exact printing house, and the Greek text was not printed in this edition.",
        rare = true,
    ),

)
