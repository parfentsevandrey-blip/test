package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 2000 to 2010: processors, game consoles, graphics cards, phones and handhelds, laptops, clusters and hobby boards. */
val db3Devices: List<RecordEntity> = listOf(

    // ---------- Processors ----------

    entry(
        databaseId = 3,
        kind = Kind.DEVICE,
        section = "Processors",
        title = "Intel Pentium 4",
        originalTitle = "Pentium 4 (NetBurst)",
        author = "Intel",
        year = 2000,
        summary = "Intel's NetBurst processor, built for very high clock speeds with a very long pipeline.",
        details = "The Pentium 4 (2000) introduced the NetBurst architecture, with a long pipeline designed for high clock speeds. " +
            "Intel pushed the clock past 3 GHz in 2002, and the design ran hot, which kept cooling and fan design in the news.",
    ),

    entry(
        databaseId = 3,
        kind = Kind.DEVICE,
        section = "Processors",
        title = "AMD Athlon XP",
        originalTitle = "Athlon XP",
        author = "AMD",
        year = 2001,
        summary = "AMD's Athlon XP, which competed with the Pentium 4 on performance per clock and was sold under model numbers.",
        details = "The Athlon XP (2001) was AMD's answer to the Pentium 4. It did more work per clock cycle, so AMD sold it under model numbers " +
            "that matched the Pentium 4's clock speeds. Enthusiasts favoured it for years.",
    ),

    entry(
        databaseId = 3,
        kind = Kind.DEVICE,
        section = "Processors",
        title = "Intel Itanium",
        originalTitle = "Itanium (IA-64)",
        author = "Intel and Hewlett-Packard",
        year = 2001,
        summary = "A 64-bit EPIC processor that relied on the compiler to find parallel instructions, first shipped in 2001.",
        details = "Itanium, developed by Intel and HP, shipped in 2001 as the first EPIC processor. Its design moved the work of finding parallel instructions " +
            "to the compiler. Its x86 emulation was slow, and the market preferred AMD's 64-bit x86 design.",
        rare = true,
    ),

    entry(
        databaseId = 3,
        kind = Kind.DEVICE,
        section = "Processors",
        title = "AMD Athlon 64",
        originalTitle = "Athlon 64",
        author = "AMD",
        year = 2003,
        summary = "AMD's first 64-bit x86 processor, with the AMD64 instruction set and a memory controller on the chip.",
        details = "The Athlon 64 (2003) extended x86 to 64 bits while keeping old software running. Its memory controller sat on the processor, " +
            "which cut latency. Intel later adopted the same 64-bit instruction set.",
    ),

    entry(
        databaseId = 3,
        kind = Kind.DEVICE,
        section = "Processors",
        title = "Intel Core 2 Duo",
        originalTitle = "Core 2 Duo (Core microarchitecture)",
        author = "Intel",
        year = 2006,
        summary = "Intel's Core microarchitecture in dual-core chips for desktops and laptops, with a shared cache.",
        details = "The Core 2 Duo (2006) brought Intel's new Core microarchitecture to desktops and laptops. " +
            "Its wide execution core and shared second-level cache gave it a large lead over the Pentium 4 in most programs.",
    ),

    entry(
        databaseId = 3,
        kind = Kind.DEVICE,
        section = "Processors",
        title = "Cell Broadband Engine",
        originalTitle = "Cell Broadband Engine",
        author = "IBM, Sony and Toshiba",
        year = 2005,
        summary = "A processor with one PowerPC core and eight synergistic cores, built for the PlayStation 3 and for computing.",
        details = "The Cell was designed by IBM, Sony and Toshiba and used in the PlayStation 3 (2006). " +
            "Its eight synergistic processing elements ran small programs with local memory, which made it powerful and difficult to program.",
        rare = true,
    ),

    entry(
        databaseId = 3,
        kind = Kind.DEVICE,
        section = "Processors",
        title = "PowerPC G5",
        originalTitle = "PowerPC 970 (G5)",
        author = "Apple and IBM",
        year = 2003,
        summary = "Apple's 64-bit PowerPC 970 processor, sold in the Power Mac G5 from June 2003.",
        details = "The PowerPC 970, known as the G5 at Apple, was a 64-bit chip derived from IBM's POWER4 design. " +
            "It appeared in the Power Mac G5 in June 2003, and Apple used the PowerPC line until its move to Intel processors in 2006.",
    ),

    entry(
        databaseId = 3,
        kind = Kind.DEVICE,
        section = "Processors",
        title = "UltraSPARC T1",
        originalTitle = "UltraSPARC T1 (Niagara)",
        author = "Sun Microsystems",
        year = 2005,
        summary = "Sun's Niagara chip with eight cores and four threads per core, designed for servers that spend time waiting for memory.",
        details = "The UltraSPARC T1 (2005) placed eight simple cores on one die, each with four hardware threads. " +
            "It was aimed at web and database servers, where threads spend much of their time waiting for memory.",
        rare = true,
    ),

    entry(
        databaseId = 3,
        kind = Kind.DEVICE,
        section = "Processors",
        title = "Transmeta Crusoe",
        originalTitle = "Crusoe",
        author = "Transmeta",
        year = 2000,
        summary = "A low-power x86 processor that ran its instructions through software translation, launched in 2000.",
        details = "The Crusoe (2000) ran x86 code through a translation layer written in software, which let it use a simpler and cooler hardware core. " +
            "Some laptops used it for long battery life, and its translation approach became a footnote in processor history.",
        rare = true,
    ),

    // ---------- Game consoles ----------

    entry(
        databaseId = 3,
        kind = Kind.DEVICE,
        section = "Game consoles",
        title = "PlayStation 2",
        originalTitle = "PlayStation 2 (PS2)",
        author = "Sony Computer Entertainment",
        year = 2000,
        summary = "Sony's DVD console with the Emotion Engine processor, released in Japan in March 2000.",
        details = "The PlayStation 2 launched in Japan in March 2000 and became the best-selling console of its generation. " +
            "Its Emotion Engine was a custom MIPS-based processor with vector units, and the console played DVDs as well as games.",
    ),

    entry(
        databaseId = 3,
        kind = Kind.DEVICE,
        section = "Game consoles",
        title = "Xbox",
        originalTitle = "Xbox (original)",
        author = "Microsoft",
        year = 2001,
        summary = "Microsoft's first console, built around a custom Intel Pentium III-class processor and an NVIDIA graphics chip.",
        details = "The original Xbox launched in November 2001. Its custom processor was based on an Intel Pentium III design, and its graphics came from NVIDIA. " +
            "Its hard drive and the online service Xbox Live (2002) set the pattern for later consoles.",
    ),

    entry(
        databaseId = 3,
        kind = Kind.DEVICE,
        section = "Game consoles",
        title = "GameCube",
        originalTitle = "Nintendo GameCube",
        author = "Nintendo",
        year = 2001,
        summary = "Nintendo's cube-shaped console with the Gekko PowerPC processor and mini-DVD discs.",
        details = "The GameCube launched in 2001 with a PowerPC processor called Gekko and proprietary mini-DVD discs. " +
            "Nintendo kept its focus on games for families, and the console had a strong library of first-party titles.",
    ),

    entry(
        databaseId = 3,
        kind = Kind.DEVICE,
        section = "Game consoles",
        title = "Game Boy Advance",
        originalTitle = "Game Boy Advance (GBA)",
        author = "Nintendo",
        year = 2001,
        summary = "A 32-bit handheld with an ARM7 processor, a bright screen and a library of cartridge games.",
        details = "The Game Boy Advance (2001) used an ARM7TDMI processor, the same core found in many phones. " +
            "Its screen was bright and its cartridges fast, and it kept the older Game Boy games playable.",
    ),

    entry(
        databaseId = 3,
        kind = Kind.DEVICE,
        section = "Game consoles",
        title = "Nintendo DS",
        originalTitle = "Nintendo DS",
        author = "Nintendo",
        year = 2004,
        summary = "A dual-screen handheld with a touch-sensitive lower screen and a microphone, released in 2004.",
        details = "The Nintendo DS (2004) had two screens, one of them touch-sensitive, and a microphone. " +
            "Its library included puzzle, language and simulation titles, which reached audiences beyond traditional gamers.",
    ),

    entry(
        databaseId = 3,
        kind = Kind.DEVICE,
        section = "Game consoles",
        title = "PlayStation Portable",
        originalTitle = "PlayStation Portable (PSP)",
        author = "Sony Computer Entertainment",
        year = 2004,
        summary = "Sony's widescreen handheld with a MIPS processor, a Memory Stick format and media playback.",
        details = "The PSP launched in 2004 with a widescreen display, a MIPS processor and the Memory Stick format. " +
            "It played games, video and music, and it became Sony's answer to the Game Boy Advance in the handheld market.",
    ),

    entry(
        databaseId = 3,
        kind = Kind.DEVICE,
        section = "Game consoles",
        title = "Xbox 360",
        originalTitle = "Xbox 360",
        author = "Microsoft",
        year = 2005,
        summary = "Microsoft's console with three PowerPC cores and an ATI graphics processor, released in November 2005.",
        details = "The Xbox 360 launched in November 2005 with three PowerPC cores and an ATI graphics chip. " +
            "Its online service and downloadable games set a standard for the next generation of consoles.",
    ),

    entry(
        databaseId = 3,
        kind = Kind.DEVICE,
        section = "Game consoles",
        title = "Wii",
        originalTitle = "Nintendo Wii",
        author = "Nintendo",
        year = 2006,
        summary = "Nintendo's console whose motion-sensing remote made it a family favourite; launched in November 2006.",
        details = "The Wii launched in November 2006 with a remote that sensed motion, which changed how families and older players used games. " +
            "Its low price and simple hardware kept it popular for years.",
    ),

    entry(
        databaseId = 3,
        kind = Kind.DEVICE,
        section = "Game consoles",
        title = "PlayStation 3",
        originalTitle = "PlayStation 3 (PS3)",
        author = "Sony Computer Entertainment",
        year = 2006,
        summary = "Sony's console with the Cell processor and a Blu-ray drive, released in November 2006.",
        details = "The PlayStation 3 launched in November 2006 with the Cell processor and a Blu-ray drive. " +
            "Its complex architecture made development hard in the first years, and its games improved as developers learned the machine.",
    ),

    entry(
        databaseId = 3,
        kind = Kind.DEVICE,
        section = "Game consoles",
        title = "N-Gage",
        originalTitle = "Nokia N-Gage",
        author = "Nokia",
        year = 2003,
        summary = "Nokia's phone shaped like a game deck, with games on cartridges and a library that failed to take off.",
        details = "Nokia released the N-Gage in 2003 as a phone that doubled as a handheld game deck. " +
            "Its games came on cartridges that were awkward to change, and players disliked holding the device to the ear. It is remembered as an unusual experiment.",
        rare = true,
    ),

    // ---------- Graphics ----------

    entry(
        databaseId = 3,
        kind = Kind.DEVICE,
        section = "Graphics",
        title = "ATI Radeon 9700",
        originalTitle = "Radeon 9700 Pro",
        author = "ATI Technologies",
        year = 2002,
        summary = "The first DirectX 9 graphics card, with programmable pixel and vertex shaders.",
        details = "The Radeon 9700 (2002) was the first graphics card to support DirectX 9, with programmable pixel and vertex shaders. " +
            "It gave ATI a lead over NVIDIA for a while, and games began to use shaders for effects that had been impossible before.",
        rare = true,
    ),

    entry(
        databaseId = 3,
        kind = Kind.DEVICE,
        section = "Graphics",
        title = "NVIDIA GeForce 8800 GTX",
        originalTitle = "GeForce 8800 GTX (G80)",
        author = "NVIDIA",
        year = 2006,
        summary = "NVIDIA's G80 card with unified shaders, which made general computing on graphics processors practical.",
        details = "The GeForce 8800 GTX (November 2006) used the G80 chip with unified shaders and 128 stream processors. " +
            "Its general-purpose programming interface, CUDA, arrived a few months later and turned graphics cards into compute devices.",
    ),

    // ---------- Phones and handhelds ----------

    entry(
        databaseId = 3,
        kind = Kind.DEVICE,
        section = "Phones and handhelds",
        title = "Palm Treo 600",
        originalTitle = "Palm Treo 600",
        author = "Palm and Handspring",
        year = 2004,
        summary = "A smartphone that combined a Palm organiser, a phone and a thumb keyboard.",
        details = "The Treo 600 (2004) joined a Palm organiser with a phone and a thumb keyboard. " +
            "Business users valued its email and calendar, and it helped open the market for smartphones before the iPhone.",
    ),

    entry(
        databaseId = 3,
        kind = Kind.DEVICE,
        section = "Phones and handhelds",
        title = "iPod",
        originalTitle = "iPod (first generation)",
        author = "Apple Computer",
        year = 2001,
        summary = "Apple's first iPod: a music player with a click wheel and a 5 GB hard drive, released in October 2001.",
        details = "The first iPod (October 2001) stored about a thousand songs on a 5 GB hard drive and used a click wheel for navigation. " +
            "It started Apple's move into consumer electronics beyond computers.",
    ),

    entry(
        databaseId = 3,
        kind = Kind.DEVICE,
        section = "Phones and handhelds",
        title = "iPhone",
        originalTitle = "iPhone (first generation)",
        author = "Apple Inc.",
        year = 2007,
        summary = "Apple's phone with a multi-touch screen and a full web browser, launched in June 2007.",
        details = "The iPhone launched in June 2007. Its multi-touch screen replaced the keyboard and stylus of earlier smartphones, " +
            "and its browser showed full websites. Its arrival changed the design of phones and of mobile software.",
    ),

    entry(
        databaseId = 3,
        kind = Kind.DEVICE,
        section = "Phones and handhelds",
        title = "Amazon Kindle",
        originalTitle = "Kindle (first generation)",
        author = "Amazon",
        year = 2007,
        summary = "An e-book reader with an e-ink screen and wireless delivery of books, launched in November 2007.",
        details = "The Kindle (November 2007) used an electronic-ink display that looked like paper and read well in sunlight. " +
            "Books arrived over a wireless connection, and the reader made e-books a mass-market product.",
    ),

    // ---------- Laptops and personal computers ----------

    entry(
        databaseId = 3,
        kind = Kind.DEVICE,
        section = "Laptops and personal computers",
        title = "PowerBook G4 Titanium",
        originalTitle = "PowerBook G4 (Titanium)",
        author = "Apple Computer",
        year = 2001,
        summary = "Apple's titanium laptop with a PowerPC G4 processor and a thin, light body.",
        details = "The PowerBook G4 Titanium (2001) had a titanium case and a PowerPC G4 processor. Its design set the look of Apple's laptops for years, " +
            "and its metal shell made it one of the most admired portables of its time.",
        rare = true,
    ),

    entry(
        databaseId = 3,
        kind = Kind.DEVICE,
        section = "Laptops and personal computers",
        title = "Asus Eee PC",
        originalTitle = "Eee PC 701",
        author = "ASUS",
        year = 2007,
        summary = "A small, cheap Linux laptop that started the netbook class in 2007.",
        details = "The Eee PC (October 2007) was a small, inexpensive laptop with a Linux system and flash storage. " +
            "Its success created the netbook class, which sold in large numbers for several years.",
    ),

    entry(
        databaseId = 3,
        kind = Kind.DEVICE,
        section = "Laptops and personal computers",
        title = "OLPC XO-1",
        originalTitle = "XO-1 (One Laptop per Child)",
        author = "One Laptop per Child project",
        year = 2007,
        summary = "A rugged green-and-white laptop for children, with a mesh network and a screen that works in sunlight.",
        details = "The XO-1 was designed for the One Laptop per Child project. Its dual-mode screen worked in sunlight and in colour, " +
            "and its mesh network let laptops share connections. The first units reached children in 2007, and the design influenced the cheap laptops that followed.",
        rare = true,
    ),

    // ---------- Clusters and supercomputers ----------

    entry(
        databaseId = 3,
        kind = Kind.DEVICE,
        section = "Clusters and supercomputers",
        title = "IBM Blue Gene/L",
        originalTitle = "Blue Gene/L",
        author = "IBM",
        year = 2004,
        summary = "A supercomputer built from many low-power PowerPC chips, which topped the TOP500 list in 2004.",
        details = "Blue Gene/L (2004) was built from many low-power PowerPC chips, connected in a three-dimensional network. " +
            "It topped the TOP500 list of supercomputers in November 2004, and its design favoured efficiency over raw clock speed.",
        rare = true,
    ),

    entry(
        databaseId = 3,
        kind = Kind.DEVICE,
        section = "Clusters and supercomputers",
        title = "Earth Simulator",
        originalTitle = "Earth Simulator (ES)",
        author = "NEC",
        year = 2002,
        summary = "NEC's vector supercomputer in Japan, which topped the TOP500 list from 2002 until 2004.",
        details = "The Earth Simulator (2002) was built by NEC for climate modelling in Japan. It used vector processors in large nodes, " +
            "and it was the fastest computer in the world until Blue Gene/L overtook it in 2004.",
        rare = true,
    ),

    // ---------- Hobby boards ----------

    entry(
        databaseId = 3,
        kind = Kind.DEVICE,
        section = "Hobby boards",
        title = "Arduino",
        originalTitle = "Arduino",
        author = "Massimo Banzi, David Cuartielles and the Arduino team",
        year = 2005,
        summary = "An open-source microcontroller board with a simple IDE, started in Italy for art and design students.",
        details = "Arduino began in 2005 at the Interaction Design Institute in Ivrea, Italy, as a cheap board for students who were not electronics engineers. " +
            "Its open hardware and simple programming environment made it popular with artists, makers and schools.",
    ),
)
