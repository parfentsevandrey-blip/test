package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1520 to 1530: drawing and reckoning aids, Apian's paper wheels, a longitude clock and the machines of Vitruvius's tenth book. */
val db52Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 52,
        kind = Kind.DEVICE,
        section = "Drawing aids",
        title = "Duerer's perspective frame, shown in his woodcut",
        originalTitle = "Underweysung der Messung",
        author = "Albrecht Duerer",
        year = 1525,
        summary = "Duerer's perspective frame was a grid fixed between the draughtsman and the model, so that the lines of the view could be copied onto paper square by square.",
        details = "Duerer printed the frame in a woodcut of his 1525 book on measurement, where a draughtsman sights a reclining figure through a grid set in a frame. Painters had used grids before, so accounts differ on how much of the frame was Duerer's own design and how much he described from existing practice.",
    ),

    entry(
        databaseId = 52,
        kind = Kind.DEVICE,
        section = "Astronomy",
        title = "Apian's paper volvelles in the Cosmographia",
        originalTitle = "Cosmographia",
        author = "Peter Apian",
        year = 1524,
        summary = "Apian's volvelles are paper wheels bound into his Cosmographia, which a reader turns over printed scales to read off dates and positions.",
        details = "The Cosmographia was printed at Landshut in 1524 by Johann Weyssenburger, and its paper wheels came to be called Apian wheels. Surviving copies differ in how many wheels they still hold, so the count is left to the copy in hand.",
    ),

    entry(
        databaseId = 52,
        kind = Kind.DEVICE,
        section = "Reckoning",
        title = "Ries's counting board of lines",
        originalTitle = "Rechnung auff der linihen und federn",
        author = "Adam Ries",
        year = 1522,
        summary = "Ries's counting board was ruled with lines, where each line and each space between lines stood for a place value, and counters were laid on them to carry out a sum.",
        details = "Ries described the reckoning on the lines in his book of 1522, which taught the work with counters and with the pen, so that a merchant could add and multiply on the board. The method was taught in German counting schools, and accounts differ on how many schools used the board in the 1520s.",
    ),

    entry(
        databaseId = 52,
        kind = Kind.DEVICE,
        section = "Timekeeping",
        title = "Vitruvius's water clock, the clepsydra of the ninth book",
        originalTitle = "De architectura",
        author = "Vitruvius",
        year = 1521,
        summary = "Vitruvius describes a water clock in the ninth book of his work, in which the flow of water is used to show the hours.",
        details = "The Como edition of 1521 printed Vitruvius's text in an Italian translation, so the water clock reached readers of 1521 in that form. Accounts differ on how exactly the parts were built, since later readers had to reconstruct the mechanism from the text.",
        rare = true,
    ),

    entry(
        databaseId = 52,
        kind = Kind.DEVICE,
        section = "Vitruvian machines",
        title = "Vitruvius's odometer for counting miles",
        originalTitle = "De architectura",
        author = "Vitruvius",
        year = 1521,
        summary = "Vitruvius describes an odometer, a cart with gears that dropped a pebble into a box at each mile, and the Como edition of 1521 printed his text in Italian.",
        details = "The odometer is described in the tenth book of the work, which the Como edition of 1521 printed in an Italian translation with commentary. The gear train counted the turns of the wheel, so the device is an early mechanical counter, and accounts differ on how exactly the pebbles were released.",
        rare = true,
    ),

    entry(
        databaseId = 52,
        kind = Kind.DEVICE,
        section = "Vitruvian machines",
        title = "Ctesibius's water organ in Vitruvius",
        originalTitle = "De architectura",
        author = "Vitruvius, after Ctesibius",
        year = 1521,
        summary = "The water organ, or hydraulis, of Ctesibius used a tank of water to keep the air pressure steady for its pipes, as Vitruvius explains in the tenth book of his work.",
        details = "Vitruvius credits the instrument to Ctesibius of Alexandria, who worked in the third century before Christ, and his account of the pumps and the water tank is among the earliest descriptions of a keyboard wind instrument. The Como edition of 1521 printed the text in Italian.",
        rare = true,
    ),

    entry(
        databaseId = 52,
        kind = Kind.DEVICE,
        section = "Vitruvian machines",
        title = "Vitruvius's screw for raising water",
        originalTitle = "De architectura",
        author = "Vitruvius",
        year = 1521,
        summary = "Vitruvius describes a screw that raised water up a spiral as it was turned by hand, and the Como edition of 1521 printed the description in Italian.",
        details = "The screw is set out among the water-raising machines of the tenth book, and Vitruvius gives it the name of Archimedes. Accounts differ on whether Archimedes himself built such a screw, so the record names the device after the text that describes it.",
    ),

    entry(
        databaseId = 52,
        kind = Kind.DEVICE,
        section = "Vitruvian machines",
        title = "Vitruvius's scorpio, the bolt-throwing engine",
        originalTitle = "De architectura",
        author = "Vitruvius",
        year = 1521,
        summary = "Vitruvius describes the scorpio, a bolt-throwing engine whose twisted cords were held in a frame, and the Como edition of 1521 printed his rules for its parts.",
        details = "The engines of the tenth book were driven by twisted cords held in frames, and Vitruvius gives proportions for the parts from the size of the missile. The scorpio is a machine of force rather than of counting, and it is included here because it comes from the same book of machines.",
    ),

)
