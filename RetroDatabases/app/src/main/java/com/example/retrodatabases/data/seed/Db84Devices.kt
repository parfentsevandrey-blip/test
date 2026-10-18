package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1200 to 1210, all from al-Jazari's 1206 book of mechanical devices: a water clock, a washing automaton and a crank pump. */
val db84Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 84,
        kind = Kind.DEVICE,
        section = "Water clocks",
        title = "Al-Jazari's elephant clock, a water clock of 1206",
        originalTitle = "Kitab fi ma'rifat al-hiyal al-handasiyya",
        author = "Ibn al-Razzaz al-Jazari",
        year = 1206,
        summary = "Al-Jazari's elephant clock is a water clock of 1206 whose hidden mechanism moves figures on an elephant to show the hours.",
        details = "It is described in al-Jazari's 1206 book on ingenious mechanical devices, which gives the clock's design in words and drawings. Accounts differ on how the timing was regulated, and no working example from his time survives.",
    ),

    entry(
        databaseId = 84,
        kind = Kind.DEVICE,
        section = "Automata",
        title = "Al-Jazari's servant automaton for washing the hands, of 1206",
        originalTitle = "Kitab fi ma'rifat al-hiyal al-handasiyya",
        author = "Ibn al-Razzaz al-Jazari",
        year = 1206,
        summary = "In his 1206 book al-Jazari described a washing basin with a servant figure that poured water when a user washed his hands.",
        details = "The device is one of the automata in the same book as the elephant clock, and the description covers the water supply and the movement of the figure. Historians know the design only from the book, since no example of it survives.",
        rare = true,
    ),

    entry(
        databaseId = 84,
        kind = Kind.DEVICE,
        section = "Pumps",
        title = "Al-Jazari's water-raising pump with a crank and a connecting rod",
        originalTitle = "Kitab fi ma'rifat al-hiyal al-handasiyya",
        author = "Ibn al-Razzaz al-Jazari",
        year = 1206,
        summary = "Al-Jazari's 1206 book describes a water-raising machine driven by a crank and connecting rod, which historians treat as an early description of that linkage in a machine.",
        details = "The crank turns the rotary motion of a wheel into the back and forth stroke of a piston, and the machine lifts water to a higher level. Accounts differ on whether earlier machines used the same linkage, so the 1206 drawing is the early description usually cited.",
    ),

)
