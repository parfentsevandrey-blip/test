package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1470 to 1480: Caxton's English, Greek and Latin grammars, a Dante, a Valencian anthology and a Greek Aesop. */
val db57Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 57,
        kind = Kind.LANGUAGE,
        section = "Printed English",
        title = "Caxton's Recuyell of the Histories of Troy",
        originalTitle = "The Recuyell of the Historyes of Troye",
        author = "William Caxton",
        year = 1474,
        summary = "Caxton's English version of a French story of Troy, printed at Bruges, is usually called the first book printed in English.",
        details = "Caxton made the translation from French and is usually said to have finished it in 1471. He printed the book at Bruges, where he had set up a press, and accounts give the printing as 1473 or 1474. The claim that it is the first book printed in English is standard, but it depends on how earlier printing is counted.",
    ),

    entry(
        databaseId = 57,
        kind = Kind.LANGUAGE,
        section = "Greek grammar",
        title = "Lascaris's Erotemata, the Greek grammar in Greek type",
        originalTitle = "Erotemata",
        author = "Constantine Lascaris",
        year = 1476,
        summary = "Lascaris's Greek grammar, printed at Milan in January 1476, is widely credited as the earliest book set wholly in Greek type.",
        details = "Dionysius Paravisinus printed it on 30 January 1476 for Ippolita Sforza, whom Lascaris had tutored after Constantinople fell in 1453. The Greek letters are credited to the Cretan scribe Demetrius Damilas, though accounts say the letter forms follow another scribe's hand, and a second edition came out in 1480.",
        rare = true,
    ),

    entry(
        databaseId = 57,
        kind = Kind.LANGUAGE,
        section = "Italian verse",
        title = "Dante's Commedia printed at Foligno",
        originalTitle = "La Commedia",
        author = "Dante Alighieri",
        year = 1472,
        summary = "The Divine Comedy first appeared in print at Foligno in 1472, after more than a century of manuscript copies.",
        details = "Dante wrote the poem in the early 1300s, and copies had circulated by hand for generations before the Foligno edition. Foligno is a small town in Umbria, far from the printing centres of Venice and Rome, so the edition shows how printing reached places beyond the big cities.",
    ),

    entry(
        databaseId = 57,
        kind = Kind.LANGUAGE,
        section = "Latin usage",
        title = "Valla's Elegantiae, the rules of refined Latin in print",
        originalTitle = "Elegantiae linguae Latinae",
        author = "Lorenzo Valla",
        year = 1471,
        summary = "Valla's handbook of refined Latin usage, a school book for generations, reached print at Rome about 1471.",
        details = "Valla wrote the Elegantiae in the 1430s and 1440s, and manuscript copies circulated long before printing. Accounts differ on the printer and on the exact date of the first printed edition, which is usually put at about 1471.",
        rare = true,
    ),

    entry(
        databaseId = 57,
        kind = Kind.LANGUAGE,
        section = "Catalan verse",
        title = "The Valencian Trobes in praise of the Virgin",
        originalTitle = "Trobes en lahor de la Verge Maria",
        author = "Valencian poets, an anthology",
        year = 1474,
        summary = "A Valencian anthology of verses in praise of the Virgin Mary, printed in 1474, is often cited as the earliest book printed in Spain.",
        details = "The verses are in the Catalan of Valencia, and the book was printed at Valencia in that year. Some accounts give another book the first place in Spanish printing, so the claim is stated here as a common view rather than a settled fact.",
        rare = true,
    ),

    entry(
        databaseId = 57,
        kind = Kind.LANGUAGE,
        section = "Greek fables",
        title = "A Greek Aesop printed at Milan in 1480",
        originalTitle = "Aesopi Fabulae",
        author = "Aesop, edited by Bonus Accursius",
        year = 1480,
        summary = "A Greek text of Aesop's fables printed at Milan in 1480 gave readers of Greek a school text four years after the Erotemata.",
        details = "The usual account names Bonus Accursius as the editor of the Milan printing. The record gives no more detail than the city, the year and the editor, because accounts describe the arrangement of the Greek text in different ways.",
        rare = true,
    ),

)
