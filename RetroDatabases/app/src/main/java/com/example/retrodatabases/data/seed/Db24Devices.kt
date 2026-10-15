package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1800 to 1810: a battery, a card-controlled loom, a writing machine, two telegraphs and other machines. */
val db24Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 24,
        kind = Kind.DEVICE,
        section = "Electrical sources",
        title = "Volta's electric pile",
        originalTitle = "On the Electricity excited by the mere Contact of conducting Substances of different kinds (1800)",
        author = "Alessandro Volta",
        year = 1800,
        summary = "Volta stacked discs of two different metals with moistened card between each pair, and the pile gave a steady electric current that could be drawn off.",
        details = "Volta's account is dated 20 March 1800 in the usual references and was read to the Royal Society that year, when it appeared in the Philosophical Transactions. The pile became the standard source of steady current for decades, and the electrolytic telegraphs of 1804 and 1809 drew their current from it.",
    ),

    entry(
        databaseId = 24,
        kind = Kind.DEVICE,
        section = "Weaving",
        title = "Jacquard's punched-card loom",
        originalTitle = "Improved draw-loom with a chain of punched cards (1801 and 1804)",
        author = "Joseph Marie Jacquard",
        year = 1801,
        summary = "Jacquard's loom read a chain of punched cards, one card for each row of the weave, so a figured silk could be woven without a helper pulling cords by hand.",
        details = "This record uses 1801, the year the improved loom was first shown in public at the Paris exhibition, where it was awarded a medal. The patent is dated 1804 in most accounts, with some giving 1805, and earlier card and paper controls by Bouchon, Falcon and Vaucanson fed into the design.",
    ),

    entry(
        databaseId = 24,
        kind = Kind.DEVICE,
        section = "Writing machines",
        title = "Turri's writing machine for a blind countess",
        originalTitle = "Writing machine built for Countess Carolina Fantoni (1808)",
        author = "Pellegrino Turri",
        year = 1808,
        summary = "Turri built a hand-operated writing machine with type for a blind countess, so that she could write letters without help.",
        details = "The date is disputed: accounts put the machine in 1801, 1806 or 1808, and this record uses 1808, the year of a letter from the countess about it. A rival account credits her relative Agostino Fantoni with the design in 1802, with Turri only refining it, and the machine itself is lost.",
        rare = true,
    ),

    entry(
        databaseId = 24,
        kind = Kind.DEVICE,
        section = "Electrochemical telegraph",
        title = "Soemmerring's electrochemical telegraph",
        originalTitle = "Telegraph by electrolysis of water, shown to the Munich Academy (1809)",
        author = "Samuel Thomas von Soemmerring",
        year = 1809,
        summary = "Soemmerring's telegraph sent current down a set of wires that split water, so a bubble of gas rising in a glass tube showed which wire was carrying a signal.",
        details = "One chronology dates the showing at the Munich Academy to 28 August 1809, and accounts give between 27 and 35 wires. Salva's design of 1804 in Barcelona had already used the electrolysis of water, so the Munich telegraph was not the first of its kind.",
    ),

    entry(
        databaseId = 24,
        kind = Kind.DEVICE,
        section = "Electrochemical telegraph",
        title = "Salva's electrolytic telegraph design of 1804",
        originalTitle = "Electrolytic telegraph design taken to Barcelona (1804)",
        author = "Francisco Salva y Campillo",
        year = 1804,
        summary = "Salva's telegraph used a voltaic pile and the electrolysis of water at the receiving end, so each signal appeared as bubbles in a tube.",
        details = "Salva took his design to Barcelona in February 1804, several years before the Munich showing of Soemmerring in 1809. Accounts of how many wires Salva used differ, and this record gives no count.",
        rare = true,
    ),

    entry(
        databaseId = 24,
        kind = Kind.DEVICE,
        section = "Mechanical music",
        title = "Maelzel's Panharmonicon, a band set by pins",
        originalTitle = "Panharmonicon, a mechanical orchestra (1805)",
        author = "Johann Nepomuk Maelzel",
        year = 1805,
        summary = "Maelzel's Panharmonicon was a mechanical band whose music was set by pins on rotating barrels, so the same program could be played again exactly.",
        details = "Maelzel built the instrument in Vienna in 1805, and it played military and orchestral pieces from its barrels. Beethoven wrote Wellington's Victory for the instrument in 1813.",
    ),

    entry(
        databaseId = 24,
        kind = Kind.DEVICE,
        section = "Printing",
        title = "Stanhope's all-iron printing press",
        originalTitle = "Iron printing press built for the Earl Stanhope (1800)",
        author = "Charles Stanhope, third Earl Stanhope",
        year = 1800,
        summary = "Earl Stanhope's press was built of cast iron and used a lever to press the platen, which made printing steadier and faster than the wooden hand press.",
        details = "Stanhope's iron press dates from 1800 and is usually called the first all-iron press of its kind. Its design was copied in London and elsewhere in the following years, and it marks a step toward the mechanical printing machines that led to the typewriter.",
        rare = true,
    ),

    entry(
        databaseId = 24,
        kind = Kind.DEVICE,
        section = "Electric light",
        title = "Davy's electric arc between carbon rods",
        originalTitle = "Electric arc between charcoal points, Royal Institution lectures (1808)",
        author = "Humphry Davy",
        year = 1808,
        summary = "Davy struck a bright arc between two charcoal rods fed by a large voltaic battery, an early electric light that later arc lamps copied.",
        details = "Davy showed the arc at the Royal Institution in 1808 with a battery of many cells, which is the kind of voltaic pile Volta had described in 1800. The carbon arc he produced became the model for later arc lamps, though he was not the first to observe an arc.",
    ),
)
