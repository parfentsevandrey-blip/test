package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1700 to 1710: an electrical globe, a steam pump, a paddle boat, a piano, a seed drill, a coke furnace, a stepped calculator and a lighthouse. */
val db34Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 34,
        kind = Kind.DEVICE,
        section = "Electricity",
        title = "Hauksbee's electrical machine",
        originalTitle = "Physico-Mechanical Experiments on Various Subjects",
        author = "Francis Hauksbee",
        year = 1705,
        summary = "A glass globe turned by hand against a rubbing pad, which produced static charge and, in evacuated vessels, a faint glow.",
        details = "Hauksbee described his machine in the Royal Society's Philosophical Transactions in 1705, and he gathered his experiments into a book in 1709. The globe was spun by a wheel and rubbed with a pad, so the machine supplied a steady charge for experiments that earlier devices could only do in short bursts.",
    ),

    entry(
        databaseId = 34,
        kind = Kind.DEVICE,
        section = "Pumping",
        title = "Savery's Miner's Friend",
        originalTitle = "The Miner's Friend",
        author = "Thomas Savery",
        year = 1702,
        summary = "A steam device that drew water up a pipe by condensing steam in a closed vessel, which was meant to pump water out of mines.",
        details = "Savery patented the device in 1698 and described it in The Miner's Friend of 1702, a book aimed at mine owners. The lift was limited by the air pressure that pushed the water up, and the device was used mainly for raising water from shallow pits.",
    ),

    entry(
        databaseId = 34,
        kind = Kind.DEVICE,
        section = "Ship propulsion",
        title = "Papin's paddle-wheel boat on the Fulda",
        originalTitle = "Paddle-wheel boat on the river Fulda",
        author = "Denis Papin",
        year = 1707,
        summary = "A boat with paddle wheels that Papin took down the river Fulda from Kassel toward Munden in 1707.",
        details = "The trip of 1707 is agreed, but accounts differ on whether the boat was steam-driven, since some sources say that Papin never built a steam boat. Accounts also differ on how the trip ended, with some blaming boatmen and others customs and bargemen opposition.",
        rare = true,
    ),

    entry(
        databaseId = 34,
        kind = Kind.DEVICE,
        section = "Thermometry",
        title = "Amontons's air thermometer",
        originalTitle = "Air-pressure thermometer",
        author = "Guillaume Amontons",
        year = 1702,
        summary = "A thermometer in which a fixed quantity of air is heated, so that its rising pressure shows the temperature.",
        details = "The instrument dates from 1702, and it measured temperature by the rise of pressure in a fixed amount of air. The same work led to his law of pressure at fixed volume.",
    ),

    entry(
        databaseId = 34,
        kind = Kind.DEVICE,
        section = "Agriculture",
        title = "Tull's seed drill",
        originalTitle = "Horse-Hoeing Husbandry",
        author = "Jethro Tull",
        year = 1701,
        summary = "A machine with a seed box and a wheel that dropped seed in rows at an even spacing, instead of scattering it by hand.",
        details = "Tull made the drill in 1701, and he described his method of drilling rows and hoeing between them in Horse-Hoeing Husbandry, published in 1731. Its gears set how often the seed was dropped, so the spacing of the rows followed from the gearing.",
    ),

    entry(
        databaseId = 34,
        kind = Kind.DEVICE,
        section = "Iron",
        title = "Darby's coke furnace at Coalbrookdale",
        originalTitle = "Coalbrookdale blast furnace",
        author = "Abraham Darby I",
        year = 1709,
        summary = "A blast furnace that smelted iron with coke instead of charcoal, which made iron production less dependent on woodland.",
        details = "Darby ran the furnace at Coalbrookdale in Shropshire in 1709, and accounts agree on the date. The iron from coke was at first used mainly for thin castings, and coke smelting became common only after several decades.",
    ),

    entry(
        databaseId = 34,
        kind = Kind.DEVICE,
        section = "Calculating machines",
        title = "Leibniz's stepped reckoner",
        originalTitle = "Brevis descriptio machinae arithmeticae",
        author = "Gottfried Wilhelm Leibniz",
        year = 1710,
        summary = "A calculating machine with a stepped drum, which multiplied by repeated addition and shifted its carriage one place for each digit.",
        details = "Leibniz's short description of the machine was printed in 1710 in the Berlin society's Miscellanea, under the title Brevis descriptio machinae arithmeticae. The one surviving prototype of the machine is held in Hanover.",
        rare = true,
    ),

    entry(
        databaseId = 34,
        kind = Kind.DEVICE,
        section = "Lighthouses",
        title = "The Eddystone lighthouse of 1709",
        originalTitle = "Eddystone lighthouse",
        author = "John Rudyerd",
        year = 1709,
        summary = "A timber lighthouse on the Eddystone reef off Plymouth, built to replace an earlier tower that had been swept away.",
        details = "The first tower on the reef was built by Winstanley and was destroyed in the great storm of 1703, with its builder inside. Rudyerd's tower of 1709 was built of timber and served the reef for several decades.",
    ),

)
