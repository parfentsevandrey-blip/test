package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Computers of 1950 to 1960: the first commercial machines, research computers, drum and transistor machines, disks and the first integrated circuit. */
val db6Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 6,
        kind = Kind.DEVICE,
        section = "Early machines",
        title = "SWAC",
        originalTitle = "Standards Western Automatic Computer (SWAC)",
        author = "National Bureau of Standards, run at UCLA's Institute for Numerical Analysis",
        year = 1950,
        summary = "A government computer that worked at UCLA from 1950 and, in 1952, found new Mersenne primes with the Lucas–Lehmer test.",
        details = "The SWAC was built for the National Bureau of Standards. On 30 January 1952 a program written by Robinson found two new Mersenne primes, the first of them 2 to the power 521 minus 1. " +
            "The test it used, the Lucas–Lehmer test, was designed by D. H. Lehmer.",
        rare = true,
    ),

    entry(
        databaseId = 6,
        kind = Kind.DEVICE,
        section = "Early commercial machines",
        title = "Ferranti Mark 1",
        originalTitle = "Ferranti Mark 1, the Manchester Electronic Computer",
        author = "Ferranti and the University of Manchester",
        year = 1951,
        summary = "The first general-purpose computer offered commercially. The first machine went to Manchester University in February 1951.",
        details = "Ferranti built the Mark 1 from the Manchester design, and about nine machines were made. " +
            "Earlier commercial digital machines, such as BINAC and the Z4, existed, but both had serious drawbacks, so the word 'first' depends on the definition.",
    ),

    entry(
        databaseId = 6,
        kind = Kind.DEVICE,
        section = "Early commercial machines",
        title = "UNIVAC I",
        originalTitle = "UNIVAC I (Universal Automatic Computer I)",
        author = "Eckert, Mauchly and Remington Rand",
        year = 1951,
        summary = "The business computer that the US Census Bureau accepted in 1951, and the machine that predicted the 1952 presidential election on television.",
        details = "Its memory used mercury delay lines, in which acoustic pulses travelled through the liquid and were read and rewritten. " +
            "The bureau accepted the first machine on 31 March 1951, and the dedication took place on 14 June 1951. Sources disagree on when the hardware physically arrived. " +
            "The memory held 1,000 words of twelve characters each.",
    ),

    entry(
        databaseId = 6,
        kind = Kind.DEVICE,
        section = "Early commercial machines",
        title = "LEO I",
        originalTitle = "Lyons Electronic Office I (LEO I)",
        author = "J. Lyons and Company",
        year = 1951,
        summary = "The computer that the British food company J. Lyons built for its own office work. Its first commercial job, a bakery valuation, ran in 1951.",
        details = "LEO was based on the EDSAC design at Cambridge. A successful trial is recorded on 5 September 1951, and the bakery calculations were handed over at the end of November. " +
            "Other sources give 28 to 30 November 1951 for the first complete program, so the date of the first run depends on what counts as a trial.",
        rare = true,
    ),

    entry(
        databaseId = 6,
        kind = Kind.DEVICE,
        section = "Real-time computers",
        title = "Whirlwind",
        originalTitle = "Whirlwind I, the MIT Digital Computer",
        author = "MIT's Project Whirlwind, led by Jay Forrester",
        year = 1951,
        summary = "The first computer built for real-time work, with a cathode-ray display, and the testbed for magnetic core memory.",
        details = "Project Whirlwind began in 1945 as a flight simulator for the Navy, and the machine was operational in 1951. " +
            "Its design led to the IBM machine at the heart of SAGE, and the core memory built for it became the standard for a generation of computers.",
    ),

    entry(
        databaseId = 6,
        kind = Kind.DEVICE,
        section = "Research machines",
        title = "ILLIAC I",
        originalTitle = "ILLIAC I, the Illinois Automatic Computer",
        author = "University of Illinois",
        year = 1952,
        summary = "The University of Illinois computer on which Lejaren Hiller and Leonard Isaacson made the Illiac Suite, usually described as the first score written by a computer.",
        details = "ILLIAC I dates to 1952. Hiller and Isaacson used it to generate material for the Illiac Suite, a string quartet that most accounts date to 1957. " +
            "Some sources give 9 August 1956 for the first performance, by students of the School of Music, so the year varies with the source.",
        rare = true,
    ),

    entry(
        databaseId = 6,
        kind = Kind.DEVICE,
        section = "Research machines",
        title = "MANIAC I",
        originalTitle = "MANIAC I, the Mathematical Analyzer, Numerical Integrator and Computer",
        author = "Los Alamos Scientific Laboratory, under Nicholas Metropolis",
        year = 1952,
        summary = "The Los Alamos computer of 1952, built on the design of the IAS machine, which ran the calculations behind the Metropolis method.",
        details = "The machine was built by Metropolis's group at Los Alamos. Its work included the 1953 calculations behind the Metropolis algorithm, a method of sampling that became one of the basic tools of Monte Carlo simulation.",
        rare = true,
    ),

    entry(
        databaseId = 6,
        kind = Kind.DEVICE,
        section = "Early mainframes",
        title = "IBM 701",
        originalTitle = "IBM 701 Defense Calculator",
        author = "IBM",
        year = 1952,
        summary = "IBM's first commercial scientific computer, announced in 1952 as the Defense Calculator, with its memory built from Williams tubes.",
        details = "The 701 was announced in 1952, and its first machines were installed in 1952 and 1953. " +
            "Speedcoding was written for it, and the 704 replaced it in 1954 with a memory of magnetic cores.",
    ),

    entry(
        databaseId = 6,
        kind = Kind.DEVICE,
        section = "Memory",
        title = "Magnetic core memory",
        originalTitle = "Magnetic-core memory, first used on Whirlwind",
        author = "Jay Forrester and the Whirlwind team at MIT",
        year = 1953,
        summary = "Tiny ferrite rings that each hold one bit. They were the main memory of most computers for about twenty years.",
        details = "The Whirlwind team used core memory from 1953, and the IBM 704 of 1954 replaced the 701's Williams-tube memory with cores. " +
            "Cores keep their contents without power, unlike the tubes and drums they replaced, and they stayed the main memory type until semiconductor chips took over in the 1970s.",
        rare = true,
    ),

    entry(
        databaseId = 6,
        kind = Kind.DEVICE,
        section = "Drum computers",
        title = "IBM 650",
        originalTitle = "IBM 650 Magnetic Drum Data-Processing Machine",
        author = "IBM",
        year = 1954,
        summary = "A decimal drum computer that IBM sold in large numbers in the mid-1950s, with each program placed on the drum where the machine could reach it in time.",
        details = "Most references date its first use to 1954. The drum turned past a read head, so the next instruction had to sit where it would arrive just as the previous one finished. " +
            "That timing problem is why SOAP was written for it, and the Internal Translator was also built for the 650.",
    ),

    entry(
        databaseId = 6,
        kind = Kind.DEVICE,
        section = "Early mainframes",
        title = "IBM 704",
        originalTitle = "IBM 704 Electronic Data Processing Machine",
        author = "IBM",
        year = 1954,
        summary = "The mainframe with 36-bit words, magnetic-core memory, three index registers and floating-point hardware, for which FORTRAN was first written.",
        details = "IBM introduced the 704 in 1954. Sources give April or May as the announcement month. " +
            "Many early user groups, including SHARE, were formed around 704 installations, and SAP was written for the 704 as well as FORTRAN.",
    ),

    entry(
        databaseId = 6,
        kind = Kind.DEVICE,
        section = "Storage",
        title = "IBM 305 RAMAC",
        originalTitle = "IBM 305 RAMAC with the IBM 350 disk storage unit",
        author = "IBM",
        year = 1956,
        summary = "IBM's first disk drive: fifty 24-inch platters on one spindle, holding about five million characters.",
        details = "IBM unveiled the 305 on 14 September 1956. Its 350 disk unit stored five million characters on fifty platters, which is 5 MB if each character takes eight bits. " +
            "Counting only the six data bits per character gives about 3.75 MB, so the size depends on the convention. The idea of disk storage is older, and some accounts date it to about 1953.",
    ),

    entry(
        databaseId = 6,
        kind = Kind.DEVICE,
        section = "Transistor computers",
        title = "TX-0",
        originalTitle = "TX-0, the Transistorized Experimental Computer Zero",
        author = "MIT Lincoln Laboratory",
        year = 1956,
        summary = "MIT Lincoln Laboratory's all-transistor computer, built to test transistors as logic elements and to try out a large core memory.",
        details = "Museum records date its design and construction to 1956, though none gives a month for the first run. Its core memory held 65,536 words of 18 bits. " +
            "In 1958 it moved to MIT's Research Laboratory of Electronics. Together with the PDP-1 it became a base for pioneering computer work and, many say, the origins of hacker culture.",
        rare = true,
    ),

    entry(
        databaseId = 6,
        kind = Kind.DEVICE,
        section = "Ternary computers",
        title = "Setun",
        originalTitle = "Setun, the Moscow ternary computer",
        author = "Nikolay Brusentsov and Sergei Sobolev at Moscow State University",
        year = 1958,
        summary = "A Soviet computer that worked in balanced ternary digits instead of binary. About fifty machines were built at a plant in Kazan from 1959.",
        details = "Design work began in 1956 with a small team, and a working machine existed by late 1958. Sources give 1958 or 1959 for the first build. " +
            "Balanced ternary digits are negative, zero or positive, so no separate sign is needed. Setun-70, built from 1967 to 1970, never reached serial production, and claims about its cost compared with binary machines are not well supported.",
        rare = true,
    ),

    entry(
        databaseId = 6,
        kind = Kind.DEVICE,
        section = "Semiconductors",
        title = "The first integrated circuit",
        originalTitle = "Kilby's germanium integrated circuit",
        author = "Jack Kilby at Texas Instruments",
        year = 1958,
        summary = "Jack Kilby's first working integrated circuit: a transistor, resistors and a capacitor made on one small germanium chip.",
        details = "Kilby showed the device to Texas Instruments' management on 12 September 1958. An oscilloscope showed a steady sine wave, so the circuit worked. " +
            "The chip was less than half an inch long. Robert Noyce at Fairchild made a silicon version in 1959 by another method, and the credit for the invention is usually shared by the two men.",
    ),

    entry(
        databaseId = 6,
        kind = Kind.DEVICE,
        section = "Transistor computers",
        title = "IBM 1401",
        originalTitle = "IBM 1401 Data Processing System",
        author = "IBM",
        year = 1959,
        summary = "IBM's transistorized business computer, unveiled on 5 October 1959, which became one of the most widely used machines of the early 1960s.",
        details = "The 1401 was aimed at businesses that already used punched-card machines. Some sources give 1960 for the first deliveries. " +
            "Estimates of the number of machines built range from about 10,000 to about 15,000.",
    ),

    entry(
        databaseId = 6,
        kind = Kind.DEVICE,
        section = "Transistor computers",
        title = "IBM 7090",
        originalTitle = "IBM 7090, the transistorized 709",
        author = "IBM",
        year = 1959,
        summary = "The transistor version of the IBM 709, the scientific mainframe that ran the SHARE operating system and then IBSYS.",
        details = "The 7090 kept the 709's design and replaced its vacuum tubes with transistors. Its operating systems were SHARE's SOS, adapted from the 709, and later IBSYS.",
    ),
)
