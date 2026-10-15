package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Computers and devices of 1960 to 1970: virtual memory machines, minicomputers, a supercomputer, the first mouse and the network's processors. */
val db7Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 7,
        kind = Kind.DEVICE,
        section = "Stack machines",
        title = "Burroughs B5000",
        originalTitle = "Burroughs B5000 Information Processing System",
        author = "Burroughs Corporation",
        year = 1961,
        summary = "A stack machine built for ALGOL, whose operating system was written in a high-level language, which is often called a first for a machine of its kind.",
        details = "Its hardware kept operands on a stack, so compiled ALGOL code was short and simple to generate. The Master Control Program was written in an ALGOL dialect, which is often given as the first operating system written in a high-level language.",
        rare = true,
    ),

    entry(
        databaseId = 7,
        kind = Kind.DEVICE,
        section = "Virtual memory",
        title = "Atlas computer",
        originalTitle = "Atlas, built at Manchester University with Ferranti",
        author = "Manchester University and Ferranti",
        year = 1962,
        summary = "The Manchester computer whose one-level store gave programmers virtual memory with paging, the idea that most computers use today.",
        details = "The first Atlas went into service in 1962. Its one-level store merged the drum and the core memory into one address space, and the supervisor moved pages between them. " +
            "The IEEE Milestone for Atlas calls virtual memory its chief innovation.",
    ),

    entry(
        databaseId = 7,
        kind = Kind.DEVICE,
        section = "Laboratory computers",
        title = "LINC",
        originalTitle = "Laboratory Instrument Computer (LINC)",
        author = "Wesley Clark and Charles Molnar at MIT's Lincoln Laboratory",
        year = 1962,
        summary = "A small laboratory computer built for biomedical researchers, and one of the candidates for the first personal computer.",
        details = "The first unit was finished in March 1962. Clark had the core idea and Molnar did the engineering. The project moved to MIT in 1963 and to Washington University in 1964, and DEC sold it from late 1964. " +
            "Some call it the first minicomputer, and a computing dictionary says the IEEE Computer Society accepts it as the first interactive personal computer, though the claim is debated.",
        rare = true,
    ),

    entry(
        databaseId = 7,
        kind = Kind.DEVICE,
        section = "Supercomputers",
        title = "CDC 6600",
        originalTitle = "Control Data 6600",
        author = "Seymour Cray at Control Data",
        year = 1964,
        summary = "Seymour Cray's machine for Control Data, which led the speed tables until 1969 and is often credited with starting the word supercomputer.",
        details = "Released in September 1964, it had about 400,000 transistors and a 100-nanosecond cycle. Ten peripheral processors took over the input and output, so the central unit could work on the arithmetic. " +
            "IBM answered by announcing faster System/360 models in advance, and the promised machines never reached customers.",
    ),

    entry(
        databaseId = 7,
        kind = Kind.DEVICE,
        section = "Minicomputers",
        title = "PDP-8",
        originalTitle = "Digital Equipment PDP-8",
        author = "Digital Equipment Corporation",
        year = 1965,
        summary = "DEC's 12-bit minicomputer, launched on 22 March 1965 for less than $20,000, which put a computer within reach of laboratories and small firms.",
        details = "Sources give the launch price as between $18,000 and $18,500. The PDP-8 is often called the first minicomputer priced below $20,000, and it sold in large numbers for years.",
    ),

    entry(
        databaseId = 7,
        kind = Kind.DEVICE,
        section = "Timesharing machines",
        title = "PDP-10",
        originalTitle = "Digital Equipment PDP-10",
        author = "Digital Equipment Corporation",
        year = 1966,
        summary = "DEC's 36-bit timesharing computer, the machine that the TOPS-10 system and many research systems ran on for much of the 1970s.",
        details = "The PDP-10 followed the PDP-6 and kept its 36-bit words. Research labs, including the MIT AI Lab, ran it for years, and it was a common timesharing machine in universities.",
        rare = true,
    ),

    entry(
        databaseId = 7,
        kind = Kind.DEVICE,
        section = "Input devices",
        title = "Computer mouse",
        originalTitle = "The wooden mouse of NLS",
        author = "Douglas Engelbart and Bill English at SRI",
        year = 1968,
        summary = "Douglas Engelbart's pointing device, first shown to the public on 9 December 1968 in the demonstration now called the Mother of All Demos.",
        details = "The first mouse was a wooden shell with two metal wheels. Bill English built the first prototype at SRI in 1964, and the demonstration ran the NLS system on an SDS 940 computer about thirty miles away.",
    ),

    entry(
        databaseId = 7,
        kind = Kind.DEVICE,
        section = "Minicomputers",
        title = "Data General Nova",
        originalTitle = "Data General Nova",
        author = "Data General, founded by former DEC engineers",
        year = 1969,
        summary = "A 16-bit minicomputer from a company founded in 1968 by former Digital Equipment engineers, designed to beat the PDP-8 on speed and on price.",
        details = "Edson de Castro, the PDP-8's chief engineer, led the design, which put a 16-bit processor on one large board. Sources give different launch prices, from about $8,000 to $9,200, and the Nova sold in tens of thousands through the 1970s.",
        rare = true,
    ),

    entry(
        databaseId = 7,
        kind = Kind.DEVICE,
        section = "Networks",
        title = "Interface Message Processor",
        originalTitle = "Interface Message Processor (IMP) of the ARPANET",
        author = "Bolt Beranek and Newman (BBN) under a DARPA contract",
        year = 1969,
        summary = "The small computer that connected each ARPANET site to the network, passing the packets of messages on from one IMP to the next.",
        details = "The first IMPs went to UCLA and SRI in 1969, and UCLA's unit was IMP number one. Each IMP stored a packet, checked it and forwarded it, which is the store-and-forward idea in hardware. The network had four sites by December 1969.",
        rare = true,
    ),

    entry(
        databaseId = 7,
        kind = Kind.DEVICE,
        section = "Terminals",
        title = "Teletype Model 33 ASR",
        originalTitle = "Teletype Model 33 Automatic Send-Receive",
        author = "Teletype Corporation",
        year = 1963,
        summary = "The paper-tape terminal that most time-sharing users typed on through the 1960s and 1970s, printing about ten characters a second.",
        details = "It read and punched five-hole paper tape as well as printing on paper. Its printing was slow and noisy, but it was cheap enough for desks, and it was the terminal of many early time-sharing services.",
        rare = true,
    ),
)
