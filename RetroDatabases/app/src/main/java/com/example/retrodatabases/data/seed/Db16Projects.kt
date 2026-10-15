package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1880 to 1890: a feeder with voltage drop, series transformers, an energy meter and a time clock, each a small program. */
val db16Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 16,
        kind = Kind.PROJECT,
        section = "Distribution",
        title = "A direct-current feeder with voltage drop",
        originalTitle = "Feeder mains of a direct-current station",
        author = "Edison central-station practice",
        year = 1882,
        summary = "A direct-current main fed at 110 volts loses voltage in each section by the current that section carries, and the program finds the voltage at each lamp group.",
        details = "Edison's direct-current stations of the early 1880s fed lamps at about 110 volts over mains that lost voltage along their length. The example works through three sections with loads at their far ends, using exact fractions for the resistances and currents. The program prints the voltage at each tap and the worst drop.",
        instructions = """
            1. Save the example as feeder.py.
            2. Run it: python3 feeder.py
        """,
        example = """
            from fractions import Fraction

            # A direct-current main fed at 110 V, with three sections and lamp groups tapped at their far ends.
            supply = Fraction(110)
            resistance = [Fraction(2, 100), Fraction(3, 100), Fraction(5, 100)]   # ohms in each section
            load = [Fraction(20), Fraction(10), Fraction(5)]                       # amperes drawn at each tap

            voltage = supply
            for k in range(3):
                # The current in section k is the sum of the loads at and beyond its far end.
                current = sum(load[k:])
                voltage -= current * resistance[k]
                print(f"node {k + 1}: {float(voltage):.2f} V")
            print(f"worst drop: {float(supply - voltage):.2f} V")
        """,
    ),

    entry(
        databaseId = 16,
        kind = Kind.PROJECT,
        section = "Transformers",
        title = "A series primary feeding transformer secondaries",
        originalTitle = "Gaulard and Gibbs secondary generator",
        author = "Lucien Gaulard and John Dixon Gibbs",
        year = 1882,
        summary = "Transformers with their primaries wired in series across one high-voltage line each take an equal share of the line voltage and step it down for their own lamp group.",
        details = "Gaulard and Gibbs proposed feeding transformer primaries in series from an alternating line, a design that Stanley's system later used. The dates of their patent and demonstrations are given differently by sources, so the record uses the year of the proposal. The example divides a 2000 volt line among four primaries and computes the secondary voltage.",
        instructions = """
            1. Save the example as series_primary.py.
            2. Run it: python3 series_primary.py
        """,
        example = """
            from fractions import Fraction

            # Transformers whose primary windings are wired in series across one high-voltage line.
            line_volts = 2000
            transformers = 4
            primary_volts = Fraction(line_volts, transformers)   # equal primaries share the line voltage
            turns_ratio = 5                                       # primary turns per secondary turn
            secondary_volts = primary_volts / turns_ratio
            print(f"each primary: {primary_volts} V")
            print(f"each secondary: {secondary_volts} V")
            print("lamp groups fed:", transformers)
        """,
    ),

    entry(
        databaseId = 16,
        kind = Kind.PROJECT,
        section = "Metering",
        title = "Energy from sampled volts and amps",
        originalTitle = "Alternating-current watt-hour meter",
        author = "Oliver Shallenberger",
        year = 1888,
        summary = "An alternating-current meter counts energy by averaging the product of voltage and current over whole cycles, so a current a quarter cycle out of step adds nothing.",
        details = "Shallenberger's alternating-current meter of 1888 was an early commercial meter for this purpose. The example samples one cycle of a sine wave at four points, once with the current in step and once a quarter cycle out. The program prints the average power in each case and the energy for three hours.",
        instructions = """
            1. Save the example as watthour.py.
            2. Run it: python3 watthour.py
        """,
        example = """
            from fractions import Fraction

            # One cycle of a sine wave sampled four times: voltage and current in step, then a quarter cycle apart.
            volts = [0, 100, 0, -100]
            amps_in_step = [0, 2, 0, -2]
            amps_quarter_out = [2, 0, -2, 0]

            def average_power(v, i):
                # The meter averages the product of voltage and current over whole cycles.
                return Fraction(sum(a * b for a, b in zip(v, i)), len(v))

            print("in step:", average_power(volts, amps_in_step), "W")
            print("a quarter cycle out:", average_power(volts, amps_quarter_out), "W")
            hours = 3
            energy = average_power(volts, amps_in_step) * hours / 1000
            print(f"energy in {hours} h: {float(energy):.3f} kWh")
        """,
    ),

    entry(
        databaseId = 16,
        kind = Kind.PROJECT,
        section = "Time keeping",
        title = "Hours worked from time-clock punches",
        originalTitle = "Time recorder",
        author = "Willard Legrand Bundy",
        year = 1888,
        summary = "A time recorder punched the clock time at each change of activity, and the day's hours are the sum of the intervals between the punches that start work and end it.",
        details = "Bundy's time recorder of 1888 recorded the clock time at each punch, so that the hours of a working day could be added up. The example takes four punches with a lunch break, converts them to minutes, and adds the two working intervals. The program prints the total in hours and minutes and as a decimal.",
        instructions = """
            1. Save the example as timeclock.py.
            2. Run it: python3 timeclock.py
        """,
        example = """
            # Punch times on one day: clock in, lunch out, lunch in, clock out.
            punches = ["08:55", "12:00", "12:30", "17:20"]

            def minutes(clock):
                hours, mins = clock.split(":")
                return int(hours) * 60 + int(mins)

            times = [minutes(p) for p in punches]
            worked = (times[1] - times[0]) + (times[3] - times[2])
            print(f"worked {worked // 60} h {worked % 60} min")
            print(f"decimal hours: {worked / 60:.2f}")
        """,
    ),
)
