package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 2010 to 2020: phones, tablets, boards, wearables and gadgets that programmers wrote code for. */
val db4Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 4,
        kind = Kind.DEVICE,
        section = "Laptops",
        title = "Apple M1 Mac",
        originalTitle = "Mac with Apple M1",
        author = "Apple",
        year = 2020,
        summary = "The first Macs built around Apple's own ARM chip, which made the Mac an Apple silicon platform for developers.",
        details = "Apple announced the M1 in November 2020 in a MacBook Air, a 13-inch MacBook Pro and a Mac mini. Developers had to rebuild their tools for ARM, " +
            "and Rosetta 2 ran the Intel versions of their apps while the native builds were prepared.",
    ),

    entry(
        databaseId = 4,
        kind = Kind.DEVICE,
        section = "Tablets",
        title = "iPad (first generation)",
        originalTitle = "iPad (1st generation)",
        author = "Apple",
        year = 2010,
        summary = "Apple's first tablet, which ran iPhone apps and the iOS touch frameworks on a 9.7-inch screen.",
        details = "The first iPad went on sale in April 2010. Developers wrote its apps with the same iOS SDK as the iPhone, so existing iPhone apps ran on it, " +
            "and many were rebuilt for the larger screen over the following months.",
    ),

    entry(
        databaseId = 4,
        kind = Kind.DEVICE,
        section = "Phones",
        title = "Nexus One",
        originalTitle = "Nexus One",
        author = "Google and HTC",
        year = 2010,
        summary = "A phone that Google sold unlocked on its own web store, running stock Android without carrier changes.",
        details = "The Nexus One was announced in January 2010 and sold without a carrier contract. Because it ran stock Android, " +
            "developers received system updates straight from Google instead of waiting for a carrier to pass them on.",
        rare = true,
    ),

    entry(
        databaseId = 4,
        kind = Kind.DEVICE,
        section = "Microcontroller boards",
        title = "Arduino Uno",
        originalTitle = "Arduino Uno",
        author = "Massimo Banzi, David Cuartielles and the Arduino team",
        year = 2010,
        summary = "A board with a microcontroller, USB and fourteen digital pins, programmed in short C++ sketches from a simple IDE.",
        details = "The Arduino Uno was shown in September 2010, and its name means one in Italian. It became the reference board of the platform. " +
            "A sketch has a setup function that runs once and a loop function that runs forever, and the IDE uploads it over USB.",
        instructions = """
            1. Install the Arduino IDE and connect the board with a USB cable.
            2. Open File, Examples, Basics, Blink.
            3. Choose the Uno in the Tools menu and press Upload. The onboard LED blinks.
        """,
        example = """
            void setup() {
              pinMode(13, OUTPUT);
            }

            void loop() {
              digitalWrite(13, HIGH);
              delay(500);
              digitalWrite(13, LOW);
              delay(500);
            }
        """,
    ),

    entry(
        databaseId = 4,
        kind = Kind.DEVICE,
        section = "Single-board computers",
        title = "Raspberry Pi Model B",
        originalTitle = "Raspberry Pi Model B",
        author = "Raspberry Pi Foundation",
        year = 2012,
        summary = "A computer the size of a credit card, with an ARM processor, HDMI and USB, that schools used to learn Linux and Python.",
        details = "The Raspberry Pi Model B went on sale in February 2012. Its ARM processor, HDMI output and USB ports made it a cheap way to run Linux, " +
            "and the Foundation aimed it at teaching programming. Millions of boards sold over the following years.",
    ),

    entry(
        databaseId = 4,
        kind = Kind.DEVICE,
        section = "Single-board computers",
        title = "Raspberry Pi Zero",
        originalTitle = "Raspberry Pi Zero",
        author = "Raspberry Pi Foundation",
        year = 2015,
        summary = "A very small Raspberry Pi that cost five dollars and was first given away on a magazine cover.",
        details = "The Pi Zero was announced in November 2015 for five dollars, and the first boards came free with a magazine. " +
            "It had a single-core processor and a mini HDMI port, and it became a common base for small embedded projects.",
        rare = true,
    ),

    entry(
        databaseId = 4,
        kind = Kind.DEVICE,
        section = "Wearables",
        title = "Pebble",
        originalTitle = "Pebble smartwatch",
        author = "Pebble Technology",
        year = 2013,
        summary = "A smartwatch with an e-paper screen, whose apps were written in C with a JavaScript layer and a free SDK.",
        details = "Pebble began shipping its watches in January 2013, after a crowdfunding campaign. Its e-paper screen lasted days on one charge, " +
            "and the free SDK let developers write watch faces and apps.",
    ),

    entry(
        databaseId = 4,
        kind = Kind.DEVICE,
        section = "Wearables",
        title = "Google Glass Explorer Edition",
        originalTitle = "Google Glass Explorer Edition",
        author = "Google",
        year = 2013,
        summary = "A head-mounted display that showed cards on a small screen and ran apps built with the Glass Development Kit.",
        details = "Google began shipping the Explorer Edition to developers in 2013. Its apps used the Glass Development Kit with Android tools. " +
            "The consumer version was withdrawn in 2015, and an enterprise edition followed in 2017.",
        rare = true,
    ),

    entry(
        databaseId = 4,
        kind = Kind.DEVICE,
        section = "Virtual reality",
        title = "Oculus Rift Development Kit 1",
        originalTitle = "Oculus Rift DK1",
        author = "Oculus VR",
        year = 2013,
        summary = "The first Rift developer kit, a headset with head tracking that developers used for the first wave of VR demos.",
        details = "Backers began receiving the DK1 in late March 2013, after the delivery date slipped from the original plan. " +
            "Its SDK exposed the head-tracking data to C and C++ code, and developers built the first VR demos on top of it.",
    ),

    entry(
        databaseId = 4,
        kind = Kind.DEVICE,
        section = "Input devices",
        title = "Leap Motion Controller",
        originalTitle = "Leap Motion Controller",
        author = "Leap Motion (David Holz and Michael Buckwald)",
        year = 2013,
        summary = "A small USB sensor that tracks the hands above it, which programs read through a native SDK.",
        details = "The Leap Motion controller reached customers in July 2013, after a delay from its first target date. Developers read hand and finger positions " +
            "through the SDK in several languages, and a common demo mapped a fingertip to the mouse cursor.",
    ),

    entry(
        databaseId = 4,
        kind = Kind.DEVICE,
        section = "Input devices",
        title = "Kinect for Windows",
        originalTitle = "Kinect for Windows sensor",
        author = "Microsoft",
        year = 2012,
        summary = "The Kinect sensor sold for PCs, with an SDK for C# and C++ and a licence for commercial use.",
        details = "Microsoft released Kinect for Windows in February 2012. Its skeleton tracking let a program follow the body of a person in front of the sensor, " +
            "and the licence allowed businesses to build products on it.",
    ),

    entry(
        databaseId = 4,
        kind = Kind.DEVICE,
        section = "Tablets",
        title = "Microsoft Surface RT",
        originalTitle = "Surface RT",
        author = "Microsoft",
        year = 2012,
        summary = "A Windows RT tablet that could run only apps from the Windows Store, which made it a rare curiosity.",
        details = "Surface RT went on sale in October 2012 with Windows RT, a version of Windows for ARM chips that ran store apps and no classic desktop programs. " +
            "Its apps were written with the WinRT API, and the tablet sold poorly.",
        rare = true,
    ),

    entry(
        databaseId = 4,
        kind = Kind.DEVICE,
        section = "Laptops",
        title = "Samsung Series 5 Chromebook",
        originalTitle = "Samsung Series 5 Chromebook",
        author = "Samsung",
        year = 2011,
        summary = "One of the first Chromebooks, a laptop that started into a browser and ran web apps as its only programs.",
        details = "The Series 5 Chromebook went on sale in June 2011 with Chrome OS. Its apps were web pages and packaged web apps, " +
            "and the machine kept most of its files in the cloud.",
        rare = true,
    ),

    entry(
        databaseId = 4,
        kind = Kind.DEVICE,
        section = "Game consoles",
        title = "Ouya",
        originalTitle = "Ouya console",
        author = "Ouya Inc.",
        year = 2013,
        summary = "A small Android console for games, whose developers could publish without signing a publisher's contract.",
        details = "Ouya shipped in June 2013 after a crowdfunding campaign. Its games ran on Android, " +
            "and the open policy attracted small studios that had no route onto the big consoles.",
        rare = true,
    ),

    entry(
        databaseId = 4,
        kind = Kind.DEVICE,
        section = "Streaming",
        title = "Chromecast",
        originalTitle = "Chromecast",
        author = "Google",
        year = 2013,
        summary = "A small HDMI dongle that plays a stream started by an app on a phone or laptop.",
        details = "Google released the first Chromecast in July 2013 for 35 dollars. It did not run apps itself: a sending app started a stream, " +
            "and the later Google Cast SDK let developers add the cast button to their own apps.",
    ),

    entry(
        databaseId = 4,
        kind = Kind.DEVICE,
        section = "Smart speakers",
        title = "Amazon Echo",
        originalTitle = "Amazon Echo",
        author = "Amazon",
        year = 2014,
        summary = "A voice-controlled speaker whose features are skills that developers write and run in Amazon's cloud.",
        details = "Amazon released the Echo in November 2014 with the Alexa voice service. Developers added skills through the Alexa Skills Kit, " +
            "which Amazon launched in 2015, and the skill code ran on Amazon's servers while speech handling stayed with Alexa.",
    ),

    entry(
        databaseId = 4,
        kind = Kind.DEVICE,
        section = "Wearables",
        title = "Apple Watch",
        originalTitle = "Apple Watch (first generation)",
        author = "Apple",
        year = 2015,
        summary = "A watch whose first apps ran as extensions of iPhone apps, through the WatchKit framework.",
        details = "Apple Watch shipped in April 2015. WatchKit had been introduced with iOS 8 in 2014, and at first the phone did most of the work. " +
            "Later watchOS versions let apps run on the watch itself.",
    ),

    entry(
        databaseId = 4,
        kind = Kind.DEVICE,
        section = "Wi-Fi chips",
        title = "ESP8266 and the ESP-01 module",
        originalTitle = "ESP8266 (ESP-01 module)",
        author = "Espressif Systems and Ai-Thinker",
        year = 2014,
        summary = "A cheap Wi-Fi chip whose small ESP-01 module let hobbyists add Wi-Fi to their own microcontroller projects.",
        details = "The ESP8266 is a Wi-Fi chip from Espressif Systems. The ESP-01 module, made by Ai-Thinker, drew hobbyists in August 2014, " +
            "and an SDK in October 2014 let the chip run programs on its own. Firmware could be written in C or Lua, and a module cost only a few dollars.",
        rare = true,
    ),

    entry(
        databaseId = 4,
        kind = Kind.DEVICE,
        section = "Drones",
        title = "Parrot AR.Drone",
        originalTitle = "Parrot AR.Drone",
        author = "Parrot",
        year = 2010,
        summary = "A Wi-Fi quadcopter that an iPhone app flew, with an SDK that let outside programmers send it flight commands.",
        details = "Parrot showed the AR.Drone at CES in January 2010, and it went on sale later that year for about 300 dollars. " +
            "Its SDK let outside programmers send commands over Wi-Fi, and a small community made games and tracking demos with it.",
        rare = true,
    ),
)
