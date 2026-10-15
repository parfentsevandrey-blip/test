package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1680 to 1690: a water lift for Versailles, telescopes and burning glasses, a telegraph, plate glass, great globes, a watch and a Dutch paper beater. */
val db36Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 36,
        kind = Kind.DEVICE,
        section = "Hydraulics",
        title = "The Machine of Marly, a water lift for Versailles",
        originalTitle = "La Machine de Marly",
        author = "Rennequin Sualem",
        year = 1684,
        summary = "The Machine of Marly used a row of water wheels on the Seine to pump water up a long slope, to feed the fountains and gardens of Versailles.",
        details = "It was built between 1681 and 1684 for Louis XIV, and its pumps lifted the water in stages to an aqueduct and a reservoir at Louveciennes. The engineer Rennequin Sualem is named as its designer in most accounts, and the machine is a classic case of a long lift made from many short ones.",
    ),

    entry(
        databaseId = 36,
        kind = Kind.DEVICE,
        section = "Optics",
        title = "Huygens's aerial telescope with no tube",
        originalTitle = "Aerial telescope",
        author = "Christiaan Huygens",
        year = 1686,
        summary = "Huygens set his objective lens on a mast with no tube between the lenses, which allowed very long focal lengths.",
        details = "In the mid-1680s Huygens made telescopes whose objectives had focal lengths of a hundred feet or more, and the eyepiece was held on a separate pole and aligned by hand. The long focal length reduced the colour fringes that short single lenses gave, though the instrument was awkward to use.",
    ),

    entry(
        databaseId = 36,
        kind = Kind.DEVICE,
        section = "Optics",
        title = "Tschirnhaus's burning glasses of 1687",
        originalTitle = "Brennglaeser",
        author = "Ehrenfried Walther von Tschirnhaus",
        year = 1687,
        summary = "Tschirnhaus made large burning glasses and mirrors that focused sunlight to very high heat, in a glassworks he set up in Saxony.",
        details = "Accounts date the work to 1687 and 1688, at a glassworks that Tschirnhaus established to improve glassmaking. One account praises the unusual perfection of his burning glasses, and another says they were not without flaws.",
    ),

    entry(
        databaseId = 36,
        kind = Kind.DEVICE,
        section = "Signals",
        title = "Hooke's visual telegraph, shown to the Royal Society",
        originalTitle = "Visual telegraph",
        author = "Robert Hooke",
        year = 1684,
        summary = "Hooke set out to the Royal Society a scheme for sending messages by sight over a distance, with symbols that a watcher with a telescope could read.",
        details = "The design was presented in 1684, and Hooke said he had thought of a visual rather than audible signal years before. Accounts differ on whether the telegraph was ever built, and the picture of its symbols that circulates was drawn much later, around 1870.",
        rare = true,
    ),

    entry(
        databaseId = 36,
        kind = Kind.DEVICE,
        section = "Glass",
        title = "Plate glass cast on an iron table in 1688",
        originalTitle = "Glace coulee",
        author = "Louis Lucas de Nehou",
        year = 1688,
        summary = "Pouring molten glass onto an iron table and rolling it flat gave plates far larger than blown sheet, which made large mirrors possible.",
        details = "One account credits Louis Lucas de Nehou, working with Abraham Thevart, with perfecting this casting in 1688. Earlier mirror plates were limited to the size of blown sheet glass, so the new method opened the way to much larger mirrors.",
    ),

    entry(
        databaseId = 36,
        kind = Kind.DEVICE,
        section = "Globes",
        title = "Coronelli's pair of great globes for Louis XIV",
        originalTitle = "Globes by Coronelli for Louis XIV",
        author = "Vincenzo Coronelli",
        year = 1683,
        summary = "A pair of globes some four metres across reached Louis XIV in 1683, made in Paris by the cosmographer Vincenzo Coronelli.",
        details = "Coronelli worked in Paris from 1681, and one dealer's account dates the commission to 1678, after Cardinal d'Estrees had seen his earlier globes. Catalogues give the diameter as between 382 and 390 centimetres, so the figure varies with the source.",
        rare = true,
    ),

    entry(
        databaseId = 36,
        kind = Kind.DEVICE,
        section = "Timekeeping",
        title = "Fromanteel's verge watch movement of 1685",
        originalTitle = "Fromanteel's verge watch movement of 1685",
        author = "Ahasuerus Fromanteel",
        year = 1685,
        summary = "A London watch movement by Ahasuerus Fromanteel, dated 1685, shows the verge escapement that drove most watches of its day.",
        details = "The verge escapement lets a toothed crown wheel turn one tooth at a time, through two pallets on a rocking verge. Ahasuerus Fromanteel was one of the London makers who first built pendulum clocks in England, from the late 1650s.",
    ),

    entry(
        databaseId = 36,
        kind = Kind.DEVICE,
        section = "Paper",
        title = "The Hollander beater, a Dutch paper-pulp engine",
        originalTitle = "Hollander beater",
        author = "Dutch papermakers",
        year = 1680,
        summary = "A Dutch beater, with a roller turning in a trough, broke rags into the pulp for paper faster than the stamping mills it replaced.",
        details = "Accounts date its Dutch origin variously, to the middle or later 1600s or to about 1680, so the year here is a working date. Its roller and bed-plate do the beating, and the name Hollander went with the machine to other paper-making countries.",
        rare = true,
    ),

)
