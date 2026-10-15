package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 2026 to 2036, as a forecast: glasses, quantum machines, laptops, robots, chips and networks. */
val db9Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 9,
        kind = Kind.DEVICE,
        section = "Wearables",
        title = "Lightweight AR glasses as a developer display",
        originalTitle = "Android XR glasses, with a launch window reported for fall 2026",
        author = "Forecast written for this database",
        year = 2030,
        summary = "Forecast: developers test their apps on AR glasses as easily as on a phone, and the glasses have an app store of their own.",
        details = "Forecast. Google and its partners, among them Samsung and XREAL, announced Android XR glasses for 2026, with a launch window reported for fall 2026 and a third preview of the SDK. Pricing was still unannounced in the May 2026 reports. The forecast is that glasses become a mainstream test target by 2030, with apps as small as today's watch apps. The year is a guess.",
    ),

    entry(
        databaseId = 9,
        kind = Kind.DEVICE,
        section = "Quantum hardware",
        title = "Fault-tolerant quantum computer with 200 logical qubits",
        originalTitle = "IBM Starling, on the public roadmap of June 2025",
        author = "IBM Quantum, roadmap",
        year = 2029,
        summary = "Forecast: a quantum computer that corrects its own errors runs circuits on a few hundred logical qubits and is available to users.",
        details = "Forecast. IBM's public roadmap of June 2025 names Starling as a fault-tolerant machine in Poughkeepsie for 2029, with 200 logical qubits and 100 million operations. The forecast follows that plan, and roadmaps like this one often slip, so the year is a guess.",
    ),

    entry(
        databaseId = 9,
        kind = Kind.DEVICE,
        section = "Client hardware",
        title = "A neural accelerator in every laptop",
        originalTitle = "Neural processing units in laptop chips, from 2024",
        author = "Forecast written for this database",
        year = 2027,
        summary = "Forecast: a neural accelerator is standard in laptops, and local model inference uses it through one common interface.",
        details = "Forecast. Laptops with neural processing units have shipped since 2024, and chip vendors now put AI blocks into most laptop lines. The forecast is that by 2027 a laptop without one is unusual, and that apps reach the accelerator through one interface. The year is a guess.",
    ),

    entry(
        databaseId = 9,
        kind = Kind.DEVICE,
        section = "Robotics",
        title = "A humanoid robot for home chores",
        originalTitle = "Humanoid robots shown in 2024 and 2025",
        author = "Forecast written for this database",
        year = 2033,
        summary = "Forecast: a humanoid robot for home chores is sold in volume and programmed through an SDK that enforces speed and force limits in software.",
        details = "Forecast. Several companies showed humanoid robots in 2024 and 2025, and a few took preorders for home models. The forecast is that by 2033 a home robot is sold in volume, with its safety limits set in software. Reliability in a home is the main uncertainty, so the year is a guess.",
        rare = true,
    ),

    entry(
        databaseId = 9,
        kind = Kind.DEVICE,
        section = "Chips",
        title = "Neuromorphic accelerator on a PCIe card",
        originalTitle = "Loihi 2 and SpiNNaker 2, research chips for spiking networks",
        author = "Forecast written for this database",
        year = 2031,
        summary = "Forecast: a spiking-network chip is sold as a PCIe card and runs small always-on workloads more cheaply than a GPU.",
        details = "Forecast. Research chips such as Loihi 2 and SpiNNaker 2 run spiking networks at low power, but they are used mostly in labs. The forecast is that a commercial card appears by 2031 and wins only on small, always-on workloads. The year is a guess.",
        rare = true,
    ),

    entry(
        databaseId = 9,
        kind = Kind.DEVICE,
        section = "Client hardware",
        title = "A RISC-V laptop that runs mainstream Linux",
        originalTitle = "RVA23, the RISC-V application processor profile",
        author = "RISC-V International",
        year = 2029,
        summary = "Forecast: a RISC-V laptop runs a mainstream Linux distribution at normal speed, and most desktop software is built for it.",
        details = "Forecast. RISC-V International ratified the RVA23 profile in October 2024, and it makes vector and hypervisor support mandatory for application processors. The forecast is that laptops with RVA23 processors appear by 2029, with software support trailing the hardware. The year is a guess.",
    ),

    entry(
        databaseId = 9,
        kind = Kind.DEVICE,
        section = "Networking",
        title = "Co-packaged optical switch in the data centre",
        originalTitle = "Co-packaged optics in switch silicon",
        author = "Forecast written for this database",
        year = 2030,
        summary = "Forecast: optical links sit inside the switch chip, so a large training cluster needs fewer electrical cables.",
        details = "Forecast. Switch vendors have announced co-packaged optics for AI clusters. The forecast is that by 2030 most new large clusters use it for part of the network, which changes how software sees link failures. The year is a guess.",
    ),

    entry(
        databaseId = 9,
        kind = Kind.DEVICE,
        section = "Wearables",
        title = "A wristband for input",
        originalTitle = "Wristbands that read muscle signals, shipped with AR glasses",
        author = "Forecast written for this database",
        year = 2036,
        summary = "Forecast: a wristband that reads muscle signals becomes the usual input for glasses and phones, and gestures replace most taps.",
        details = "Forecast. Wristbands that read muscle signals have been shipped with AR glasses. The forecast is that by 2036 such a wristband is the usual input for glasses and phones, and that gestures replace most taps. The year is a guess.",
    ),
)
