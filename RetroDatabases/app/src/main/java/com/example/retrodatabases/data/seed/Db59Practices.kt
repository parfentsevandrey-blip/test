package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1450 to 1460: oil-based printing ink, blanks left on printed indulgences, proofs, the chase and type set backwards for the press. */
val db59Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 59,
        kind = Kind.PRACTICE,
        section = "Printing shop",
        title = "Oil-based printing ink for metal type",
        originalTitle = "Printing ink",
        author = "The printers of Mainz",
        year = 1450,
        summary = "The printers of the 1450s mixed lampblack with an oil varnish to make an ink that stuck to metal type and gave a dense black impression on paper.",
        details = "Scribes had used water-based inks on parchment, and the oil ink suited metal type and paper alike. No printed recipe of the 1450s survives, so the proportions used at Mainz are reconstructed from later practice and from the books themselves.",
    ),

    entry(
        databaseId = 59,
        kind = Kind.PRACTICE,
        section = "Printing shop",
        title = "Blanks left on printed indulgences for names and dates",
        originalTitle = "Ablassbrief",
        author = "Printers attributed to Mainz",
        year = 1454,
        summary = "The printed indulgence letters of 1454 and 1455 left blank lines for the purchaser's name, place and date, which were entered by hand.",
        details = "The oldest dated copy was made out at Erfurt on 22 October 1454, and the dated copies run to the end of April 1455. Catalogues handle the Gutenberg attribution with caution, and accounts differ on the cause, since one catalogue ties the grant to the defence of Cyprus while others frame it as support for a wider campaign against the Turks.",
    ),

    entry(
        databaseId = 59,
        kind = Kind.PRACTICE,
        section = "Printing shop",
        title = "Pulling a proof sheet before the print run",
        originalTitle = "Probe",
        author = "The printers of Mainz",
        year = 1455,
        summary = "Printers pulled a proof sheet from the locked forme and corrected the type before the full run, so that an error was caught once and not repeated across an edition.",
        details = "Later printing manuals describe the pulling of proofs, and whether the Mainz shop pulled them in the 1450s is not recorded. The proof was read against the copy, and the corrected forme was printed again for the edition.",
    ),

    entry(
        databaseId = 59,
        kind = Kind.PRACTICE,
        section = "Printing shop",
        title = "Locking the type in a chase with wooden wedges",
        originalTitle = "Schliessrahmen",
        author = "The printers of Mainz",
        year = 1450,
        summary = "The forme of type was held in an iron frame called a chase, and wooden wedges were driven in to lock the letters so that they would not shift under the press.",
        details = "The chase and wedges are part of the ordinary equipment of the hand press, and the later record of the trade describes them. Accounts differ on the exact form of the wedges used at Mainz.",
    ),

    entry(
        databaseId = 59,
        kind = Kind.PRACTICE,
        section = "Printing shop",
        title = "Setting type backwards so the printed page reads true",
        originalTitle = "Forme",
        author = "The compositors of Mainz",
        year = 1450,
        summary = "A compositor set each line of metal type in reverse, so that the inked impression on paper read from left to right.",
        details = "The type was held in a composing stick and read backwards, and the compositor checked the order by a proof pulled on paper before the full run. The method is the same one used by printers in the centuries that followed.",
    ),

)
