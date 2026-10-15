package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1840 to 1850: a functional determinant, a Doppler shift, Binet's formula, consumer surplus, Weber's force and Fizeau's wheel. */
val db20Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 20,
        kind = Kind.EXAMPLE,
        section = "Functional determinants",
        title = "Jacobi's functional determinant of the squaring map",
        originalTitle = "De determinantibus functionalibus",
        author = "Carl Gustav Jacob Jacobi",
        year = 1841,
        summary = "The functional determinant of a pair of functions is the determinant of their partial derivatives, and it measures how a change of variables scales area.",
        details = "Jacobi studied these determinants in 1841 in Crelle's Journal, and they were later named Jacobians. The example takes the map that sends x plus iy to its square and computes the determinant at three points, where it equals four times the squared distance from the origin.",
        instructions = """
            1. Save the example as jacobian.py.
            2. Run it: python3 jacobian.py
        """,
        example = """
            # Jacobian of the squaring map z = x + iy, which sends z to (x*x - y*y, 2*x*y), at a point (x, y)
            def jacobian(x, y):
                du_dx, du_dy = 2 * x, -2 * y
                dv_dx, dv_dy = 2 * y, 2 * x
                return du_dx * dv_dy - du_dy * dv_dx

            points = [(1, 2), (3, 0), (0, 1)]
            for point in points:
                print("point", point, "Jacobian", jacobian(*point))
            print("equals 4 times x squared plus y squared:", all(jacobian(x, y) == 4 * (x * x + y * y) for x, y in points))
        """,
    ),

    entry(
        databaseId = 20,
        kind = Kind.EXAMPLE,
        section = "Moving sources",
        title = "Doppler's shifted tone from a moving source",
        originalTitle = "Ueber das farbige Licht der Doppelsterne und einiger anderer Gestirne des Himmels",
        author = "Christian Doppler",
        year = 1842,
        summary = "A source moving toward a listener raises the heard frequency by the ratio of the sound speed to the sound speed minus the source speed.",
        details = "Doppler proposed the effect in 1842 for coloured light from double stars. The example uses 440 Hz and 340 metres per second and prints the heard pitch for a moving source, a moving listener and both together.",
        instructions = """
            1. Save the example as doppler.py.
            2. Run it: python3 doppler.py
        """,
        example = """
            from fractions import Fraction as F

            # Sound speed 340 m/s and a source frequency of 440 Hz
            v = F(340)
            f = F(440)

            def heard_moving_source(v_source):
                # the source moves toward the listener at v_source metres per second
                return f * v / (v - v_source)

            def heard_moving_listener(v_listener):
                # the listener moves toward the source at v_listener metres per second
                return f * (v + v_listener) / v

            print("source at 68 m/s toward listener:", heard_moving_source(F(68)))
            print("listener at 34 m/s toward source:", heard_moving_listener(F(34)))
            print("both at once:", f * (v + F(34)) / (v - F(68)))
        """,
    ),

    entry(
        databaseId = 20,
        kind = Kind.EXAMPLE,
        section = "Recurrences",
        title = "Binet's formula for Fibonacci numbers",
        originalTitle = "Binet's formula, printed in 1843 in a memoir on linear difference equations",
        author = "Jacques Philippe Marie Binet",
        year = 1843,
        summary = "The nth Fibonacci number is the nearest whole number to phi to the power n minus psi to the power n, divided by the square root of five.",
        details = "Binet printed the closed form in 1843, although de Moivre and Euler had found equivalent expressions earlier. The example evaluates the formula in 50-digit decimal arithmetic and checks it against a plain loop for three Fibonacci numbers.",
        instructions = """
            1. Save the example as binet.py.
            2. Run it: python3 binet.py
        """,
        example = """
            from decimal import Decimal, getcontext

            getcontext().prec = 50
            sqrt5 = Decimal(5).sqrt()
            phi = (1 + sqrt5) / 2
            psi = (1 - sqrt5) / 2

            def fib_binet(n):
                # round the closed form to the nearest whole number
                return int(round((phi ** n - psi ** n) / sqrt5))

            def fib_loop(n):
                a, b = 0, 1
                for _ in range(n):
                    a, b = b, a + b
                return a

            for n in (10, 20, 30):
                print(n, fib_binet(n), fib_loop(n), fib_binet(n) == fib_loop(n))
        """,
    ),

    entry(
        databaseId = 20,
        kind = Kind.EXAMPLE,
        section = "Public works",
        title = "Dupuit's consumer surplus for a toll bridge",
        originalTitle = "De la mesure de l'utilite des travaux publics",
        author = "Jules Dupuit",
        year = 1844,
        summary = "Dupuit measured the value of a bridge by the area under its demand curve, and a toll takes away part of that area as deadweight loss.",
        details = "Dupuit wrote on the measure of the utility of public works in 1844, and his toll example introduced the idea of consumer surplus. The example uses a linear demand curve, computes the surplus with and without a toll of 2, and splits the lost surplus into toll revenue and deadweight loss.",
        instructions = """
            1. Save the example as dupuit.py.
            2. Run it: python3 dupuit.py
        """,
        example = """
            from fractions import Fraction as F

            # Demand for trips per day: Q = 1000 - 100 p, where p is the toll; demand is zero at a price of 10.
            def quantity(p):
                return 1000 - 100 * p

            def surplus(toll):
                # area between the demand curve and the toll line, from zero trips up to quantity(toll)
                q = quantity(toll)
                return (F(10) - toll) * q / 2

            for toll in [F(0), F(2)]:
                print("toll", toll, "trips", quantity(toll), "surplus", surplus(toll))
            lost = surplus(F(0)) - surplus(F(2))
            revenue = F(2) * quantity(F(2))
            print("surplus lost:", lost, "revenue:", revenue, "deadweight loss:", lost - revenue)
        """,
    ),

    entry(
        databaseId = 20,
        kind = Kind.EXAMPLE,
        section = "Electrodynamics",
        title = "Weber's electrodynamic force law",
        originalTitle = "Elektrodynamische Maassbestimmungen",
        author = "Wilhelm Eduard Weber",
        year = 1846,
        summary = "Weber's law gives the force between two moving charges as an inverse-square force corrected by their relative speed and acceleration.",
        details = "Weber published the law in 1846. The example evaluates it at rest, at relative speed 1 and under acceleration 2, with the constant c set to 10 in the example's units.",
        instructions = """
            1. Save the example as weber.py.
            2. Run it: python3 weber.py
        """,
        example = """
            from fractions import Fraction as F

            # Force between charges e1 and e2 at distance r, with relative speed rdot and acceleration rddot.
            # c is the constant speed in the law; the example uses c = 10 in arbitrary units.
            def weber_force(e1, e2, r, rdot, rddot, c):
                return (e1 * e2 / r**2) * (1 - rdot**2 / (2 * c**2) + r * rddot / c**2)

            print("at rest:", weber_force(F(1), F(1), F(2), F(0), F(0), F(10)))
            print("receding at speed 1:", weber_force(F(1), F(1), F(2), F(1), F(0), F(10)))
            print("accelerating at 2:", weber_force(F(1), F(1), F(2), F(0), F(2), F(10)))
        """,
    ),

    entry(
        databaseId = 20,
        kind = Kind.EXAMPLE,
        section = "Light speed",
        title = "Fizeau's toothed wheel and the speed of light",
        originalTitle = "Note on the speed of light in the Comptes rendus (1849)",
        author = "Hippolyte Fizeau",
        year = 1849,
        summary = "Light sent through a gap in a toothed wheel, reflected from a distant mirror and returned, is blocked by the next tooth when the wheel turns at the right speed, which gives its speed.",
        details = "Fizeau made the measurement in 1849 over a path of about 8.6 kilometres between Suresnes and Montmartre near Paris. The example uses the first-extinction formula c = 4 L N f, with a path of 8633 metres, 720 teeth and 63/5 turns per second, and prints the resulting speed.",
        instructions = """
            1. Save the example as fizeau.py.
            2. Run it: python3 fizeau.py
        """,
        example = """
            from fractions import Fraction as F

            # Fizeau's toothed wheel: light goes out and back over the path L; the first dark view comes when
            # the round trip takes half a tooth period, so c = 2 L / (half tooth period) = 4 L N f.
            L = 8633          # metres, from the gap to the distant mirror
            N = 720           # teeth on the wheel
            f = F(63, 5)      # revolutions per second, 12.6

            tooth_period = 1 / (N * f)          # seconds from one tooth to the next at the gap
            round_trip = tooth_period / 2       # the first extinction
            c = 2 * L / round_trip
            print("half tooth period:", round_trip)
            print("speed of light in metres per second:", c)
        """,
    ),
)
