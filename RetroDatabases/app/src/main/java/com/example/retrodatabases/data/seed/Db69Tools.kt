package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1350 to 1360: Oresme's tracts, the Oxford calculators' Book of Calculations, Buridan's and Albert of Saxony's logic and physics, and the Gough Map, with approximate dates where the evidence is uncertain. */
val db69Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 69,
        kind = Kind.TOOL,
        section = "Mathematics",
        title = "Oresme's tract on the configurations of qualities and motions",
        originalTitle = "Tractatus de configurationibus qualitatum et motuum",
        author = "Nicole Oresme",
        year = 1350,
        summary = "Oresme's tract on the configurations of qualities and motions, written in Paris about 1350, used graphs to show how a changing quality varies.",
        details = "Marshall Clagett's 1968 edition printed the Latin text with an English translation, built from fourteen manuscripts. The tract is known for its graphs of changing qualities and for its treatment of the mean speed of a body that speeds up evenly.",
        rare = true,
    ),

    entry(
        databaseId = 69,
        kind = Kind.TOOL,
        section = "Logic",
        title = "Swineshead's Book of Calculations, rules for changing qualities",
        originalTitle = "Liber calculationum",
        author = "Richard Swineshead",
        year = 1350,
        summary = "Richard Swineshead's Book of Calculations, written by one of the Oxford calculators about 1350, sets out rules for how a quality changes over time.",
        details = "The Oxford calculators studied the latitude of forms and the rates of change, and Swineshead's book is among their best-known works. Its date is usually given as about 1350, and its rules were discussed by later logicians.",
        rare = true,
    ),

    entry(
        databaseId = 69,
        kind = Kind.TOOL,
        section = "Maps",
        title = "The Gough Map of Britain, one of the earliest maps with roads",
        originalTitle = "Gough Map",
        author = "Unknown cartographer",
        year = 1360,
        summary = "The Gough Map of Britain, dated to about 1360, is one of the earliest surviving maps of the island to mark roads and the distances between towns.",
        details = "The map is kept in the Bodleian Library at Oxford, and its date is approximate, with 1360 used as the usual year. Its road lines and distance figures suggest that it was made to help travellers.",
    ),

    entry(
        databaseId = 69,
        kind = Kind.TOOL,
        section = "Mathematics",
        title = "Oresme's Algorismus proportionum, rules for ratios and their powers",
        originalTitle = "Algorismus proportionum",
        author = "Nicole Oresme",
        year = 1350,
        summary = "Oresme's Algorismus proportionum, a short Latin treatise on the rules for combining ratios and their powers, is dated approximately to the 1350s.",
        details = "The date is approximate, and scholars place the treatise in the middle of the fourteenth century without agreeing on the year. It belongs to the same period as Oresme's tract on configurations, and it sets out how ratios are multiplied, compared and raised to fractional powers.",
    ),

    entry(
        databaseId = 69,
        kind = Kind.TOOL,
        section = "Physics",
        title = "Buridan's questions on Aristotle's Physics, lectures of the 1340s and 1350s",
        originalTitle = "Quaestiones super libros Physicorum",
        author = "John Buridan",
        year = 1350,
        summary = "John Buridan's questions on Aristotle's Physics, university lectures of the 1340s and 1350s, discuss impetus, the force that keeps a thrown body moving.",
        details = "The questions were recorded over several years, and the dates of the surviving versions are approximate. Buridan taught at Paris, and his account of impetus was widely read in later universities.",
    ),

    entry(
        databaseId = 69,
        kind = Kind.TOOL,
        section = "Logic",
        title = "Albert of Saxony's Perutilis logica, a logic textbook of the Paris schools",
        originalTitle = "Perutilis logica",
        author = "Albert of Saxony",
        year = 1355,
        summary = "Albert of Saxony's Perutilis logica, a logic textbook written for the Paris schools, was composed in the mid-1300s and reprinted in later centuries.",
        details = "Its date is approximate, and 1355 is used here as a middle date for the mid-1300s. The book sets out the rules of logic that students used in disputations.",
    ),

)
