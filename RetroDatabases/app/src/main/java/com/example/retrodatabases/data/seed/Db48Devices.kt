package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1560 to 1570: Juanelo's water lift, a lens in the camera obscura, the Moscow press, perspective and sundial instruments, and a surgeon's ligature. */
val db48Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 48,
        kind = Kind.DEVICE,
        section = "Hydraulics",
        title = "Juanelo's water lift for Toledo",
        originalTitle = "Artificio de Juanelo",
        author = "Juanelo Turriano",
        year = 1565,
        summary = "Juanelo Turriano's machine was built to lift water from the Tagus about a hundred metres to Toledo's Alcazar, under a contract of 1565.",
        details = "Philip II recommended the project, and Toledo ordered the machine from Turriano, who regained control of it by a contract with the city council in 1565. Accounts differ on the completion year, with some giving 1568, and the machine later fell out of reliable service.",
    ),

    entry(
        databaseId = 48,
        kind = Kind.DEVICE,
        section = "Optics",
        title = "Barbaro's lens in the camera obscura",
        originalTitle = "La pratica della perspettiva",
        author = "Daniele Barbaro",
        year = 1569,
        summary = "Barbaro's book on perspective described a camera obscura whose opening held a lens, which gave a brighter and sharper image than a bare pinhole.",
        details = "The book was printed in Venice in 1569, and some accounts date the lens to about 1568, so the year depends on whether the text or the printed book is counted. Accounts differ on whether earlier users had put lenses in their dark rooms, and the record makes no claim to an invention.",
    ),

    entry(
        databaseId = 48,
        kind = Kind.DEVICE,
        section = "Printing",
        title = "The Moscow press that printed the Apostol",
        originalTitle = "Apostol",
        author = "Ivan Fyodorov and Peter Mstislavets",
        year = 1564,
        summary = "The Apostol printed in Moscow in 1564 is the first dated book printed in Russia, and it came from a press set up by Ivan Fyodorov and Peter Mstislavets.",
        details = "The book carries the date 1564 and is usually named as the first dated printed book in Russia. Accounts differ on which earlier undated items may have come from the same press, so the record gives the date of the Apostol itself.",
        rare = true,
    ),

    entry(
        databaseId = 48,
        kind = Kind.DEVICE,
        section = "Perspective",
        title = "Jamnitzer's instrument for drawing solids in perspective",
        originalTitle = "Perspectiva corporum regularium",
        author = "Wenzel Jamnitzer",
        year = 1568,
        summary = "Jamnitzer's book of 1568 showed the five regular solids in perspective and described an instrument for drawing them with fixed sight lines.",
        details = "The book was printed in Nuremberg in 1568, with engraved plates of the regular solids and their perspective drawings. Accounts differ on how far the instrument was used in practice, so the record describes its purpose only in general terms.",
    ),

    entry(
        databaseId = 48,
        kind = Kind.DEVICE,
        section = "Sundials",
        title = "A pillar dial in a woodcut of 1567",
        originalTitle = "Woodcut of a pillar dial",
        author = "Anonymous German woodcutter",
        year = 1567,
        summary = "A German woodcut of 1567 shows a pillar sundial, a column whose faces carry hour lines for reading the time by the sun.",
        details = "The woodcut is dated 1567 and shows a full pillar dial. Pillar dials stood in gardens and squares, and the record gives the year of the picture, not of any particular dial.",
        rare = true,
    ),

    entry(
        databaseId = 48,
        kind = Kind.DEVICE,
        section = "Instruments",
        title = "Vize Menusier's astronomical compendium of Pau",
        originalTitle = "Astronomical compendium",
        author = "Hierosme Vize Menusier",
        year = 1567,
        summary = "An astronomical compendium of 1567 by Hierosme Vize Menusier, made at Pau, folds several instruments into one case.",
        details = "The British Museum holds the compendium, which is dated 1567 and credited to Hierosme Vize Menusier with Pau given as the place of manufacture. Compendia of this kind often combined a sundial, a compass and calendar tables, and the record does not list the functions of this particular example.",
        rare = true,
    ),

    entry(
        databaseId = 48,
        kind = Kind.DEVICE,
        section = "Astrolabes",
        title = "Quiniet's astrolabe made at Antwerp",
        originalTitle = "Astrolabe",
        author = "Quiniet",
        year = 1570,
        summary = "An astrolabe by Quiniet, made at Antwerp, is dated to the years 1570 to 1580, and the record uses the first year of that span.",
        details = "An astrolabe of this kind shows the altitude of stars and the time by a rotating plate. The record does not describe the scales of the Antwerp example, which is held in the British Museum.",
        rare = true,
    ),

    entry(
        databaseId = 48,
        kind = Kind.DEVICE,
        section = "Surgery",
        title = "Pare's silk ligature for cut arteries",
        originalTitle = "Dix livres de la chirurgie",
        author = "Ambroise Pare",
        year = 1564,
        summary = "Pare's silk ligature tied off a cut artery in place of the hot iron, a surgical method set out in his printed book of 1564.",
        details = "The method appears in the Dix livres de la chirurgie, printed in Paris in 1564. Accounts differ on how far Pare was the first surgeon to tie arteries in this way.",
    ),

)
