package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1870 to 1880: printing and voice telegraphy, recording, electric light and the cash register. */
val db17Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 17,
        kind = Kind.DEVICE,
        section = "Telegraph printers",
        title = "Baudot's printing telegraph",
        originalTitle = "Telegraphe imprimeur (1874)",
        author = "Emile Baudot",
        year = 1874,
        summary = "A printing telegraph that sent each letter as five equal signals and let several operators share one line by taking turns.",
        details = "Baudot's printing telegraph, patented in 1874, printed received letters on paper, and its distributor let several operators share one line in turn. The five-unit code it used became the basis of the telegraph codes that followed.",
    ),

    entry(
        databaseId = 17,
        kind = Kind.DEVICE,
        section = "Telephones",
        title = "Bell's telephone patent",
        originalTitle = "Improvement in telegraphy",
        author = "Alexander Graham Bell",
        year = 1876,
        summary = "A patent for a device that turned speech into an electric current on one wire and turned the current back into sound at the far end.",
        details = "Bell's application and a caveat filed by Elisha Gray for a similar device were both lodged on 14 February 1876, and the patent was granted on 7 March 1876. Accounts differ on which inventor first had the idea, and the priority dispute went on for years.",
    ),

    entry(
        databaseId = 17,
        kind = Kind.DEVICE,
        section = "Recording sound",
        title = "Edison's phonograph",
        originalTitle = "Phonograph",
        author = "Thomas Alva Edison",
        year = 1877,
        summary = "A machine that recorded sound as an indented groove on a tinfoil cylinder and played it back with a stylus.",
        details = "Edison's first working demonstration came in December 1877 at Menlo Park, where a tinfoil cylinder recorded and replayed a line of verse. A patent application followed later that month, and the patent was granted in 1878.",
    ),

    entry(
        databaseId = 17,
        kind = Kind.DEVICE,
        section = "Electrical generation",
        title = "Gramme's ring dynamo",
        originalTitle = "Machine dynamo-electrique",
        author = "Zenobe Theophile Gramme",
        year = 1871,
        summary = "A dynamo whose ring-wound armature gave a steady direct current, and which was copied widely for electric light and arc lamps.",
        details = "The machine was shown in Paris in 1871, and its ring winding became the model for many later dynamos. It made practical electric lighting possible on a scale that batteries could not supply.",
    ),

    entry(
        databaseId = 17,
        kind = Kind.DEVICE,
        section = "Optical telephony",
        title = "Bell's photophone",
        originalTitle = "Photophone",
        author = "Alexander Graham Bell and Charles Sumner Tainter",
        year = 1880,
        summary = "A device that sent speech on a beam of light, with a mirror that vibrated to the voice and a selenium cell that turned the light back into sound.",
        details = "Bell and Tainter first sent speech over a beam of light in June 1880. The receiver used a selenium cell whose resistance changed with the light, so the sound came back out of a telephone earpiece.",
        rare = true,
    ),

    entry(
        databaseId = 17,
        kind = Kind.DEVICE,
        section = "Electric lighting",
        title = "Jablochkoff's electric candle",
        originalTitle = "Bougie electrique (1876)",
        author = "Pavel Nikolayevich Yablochkov",
        year = 1876,
        summary = "An arc lamp with no regulator, made of two carbon rods set side by side with an insulator between them, which burned down like a candle.",
        details = "Yablochkov's candle was patented in 1876, and it was used for street lighting in Paris in the late 1870s. Its two rods were consumed evenly, so the lamp needed no mechanism to keep the arc steady.",
    ),

    entry(
        databaseId = 17,
        kind = Kind.DEVICE,
        section = "Telephones",
        title = "Hughes's carbon microphone",
        originalTitle = "Microphone",
        author = "David Edward Hughes",
        year = 1878,
        summary = "A loose carbon contact whose electrical resistance changed with sound, so that weak speech became strong enough to hear over a telephone line.",
        details = "Hughes showed the instrument in London in 1878 and gave it the name microphone. It was an early carbon transmitter, and carbon transmitters remained in telephones for decades afterwards.",
    ),

    entry(
        databaseId = 17,
        kind = Kind.DEVICE,
        section = "Business machines",
        title = "Ritty's cash register",
        originalTitle = "Ritty's Incorruptible Cashier",
        author = "James Ritty",
        year = 1879,
        summary = "A cash register that recorded each sale on a counter, so the owner could check the day's takings against the cash in the drawer.",
        details = "Ritty patented the machine in 1879 in Dayton, Ohio. Later companies, including the one that became the National Cash Register Company, grew from his design.",
    ),
)
