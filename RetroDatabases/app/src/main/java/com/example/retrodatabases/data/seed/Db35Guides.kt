package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1690 to 1700: the brachistochrone, a limit rule, Rolle's theorem, Raphson's root iteration and Leibniz's vis viva. */
val db35Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 35,
        kind = Kind.GUIDE,
        section = "Calculus of variations",
        title = "Bernoulli's brachistochrone and the cycloid's lead over a chord",
        originalTitle = "Problema novum, ad cujus solutionem Mathematici invitantur",
        author = "Johann Bernoulli",
        year = 1696,
        summary = "Johann Bernoulli challenged mathematicians in 1696 to find the curve of quickest descent, and the answer was a cycloid.",
        details = "The challenge appeared in the June 1696 Acta Eruditorum, and Leibniz replied with a full solution dated 16 June 1696. Newton's anonymous answer was printed in the Philosophical Transactions in 1697, and the answers were printed together in the Acta of May 1697; accounts differ on how the deadline was extended.",
        instructions = """
            1. Save the example as brachistochrone.py.
            2. Run it: python3 brachistochrone.py
        """,
        example = """
            import math

            g = 1.0
            a = 1.0
            end_x = math.pi * a
            end_y = 2.0 * a
            chord = math.sqrt(end_x ** 2 + end_y ** 2)
            t_line = chord / math.sqrt(g)
            t_cycloid = math.pi * math.sqrt(a / g)
            print(f"straight line: {t_line:.3f}")
            print(f"cycloid: {t_cycloid:.3f}")
            print("cycloid is faster:", t_cycloid < t_line)
        """,
    ),

    entry(
        databaseId = 35,
        kind = Kind.GUIDE,
        section = "Calculus",
        title = "How l'Hopital's rule settles a limit of zero over zero",
        originalTitle = "Analyse des infiniment petits",
        author = "Guillaume de l'Hopital",
        year = 1696,
        summary = "When a fraction has zero on top and bottom, its limit can be found by dividing the derivatives of the two parts.",
        details = "The rule appears in the 1696 Analyse, and accounts differ on whether the author or Johann Bernoulli found it first. The example takes the fraction (x cubed minus 8) over (x minus 2), which is zero over zero at x equal to 2, and checks the rule against values close to 2.",
        instructions = """
            1. Save the example as lhopital.py.
            2. Run it: python3 lhopital.py
        """,
        example = """
            def quotient(x):
                return (x ** 3 - 8.0) / (x - 2.0)

            def derivative_ratio(x):
                return (3.0 * x * x) / 1.0

            print(f"rule at x = 2: {derivative_ratio(2.0):.3f}")
            print(f"quotient at 2.001: {quotient(2.001):.2f}")
            print(f"quotient at 1.999: {quotient(1.999):.2f}")
        """,
    ),

    entry(
        databaseId = 35,
        kind = Kind.GUIDE,
        section = "Curves",
        title = "Rolle's theorem: a zero slope between two roots",
        originalTitle = "Traite d'algebre",
        author = "Michel Rolle",
        year = 1691,
        summary = "If a smooth curve crosses the axis at two points, its slope is zero at some point between them.",
        details = "Rolle worked on equations in his Traite d'algebre of 1690, and the result was stated in print in 1691. The example finds the zero of the slope between the roots of x cubed minus 3x, by repeated halving of an interval.",
        rare = true,
        instructions = """
            1. Save the example as rolle.py.
            2. Run it: python3 rolle.py
        """,
        example = """
            def p(x):
                return x ** 3 - 3 * x

            def dp(x):
                return 3 * x * x - 3

            def bisect(f, lo, hi):
                f_lo = f(lo)
                for _ in range(200):
                    mid = (lo + hi) / 2
                    f_mid = f(mid)
                    if (f_mid > 0) == (f_lo > 0):
                        lo, f_lo = mid, f_mid
                    else:
                        hi = mid
                return (lo + hi) / 2

            first = bisect(dp, -1.5, -0.5)
            second = bisect(dp, 0.5, 1.5)
            print(f"zero of the slope between the first pair of roots: {first:.3f}")
            print(f"zero of the slope between the second pair of roots: {second:.3f}")
            print("p is zero at the first root:", abs(p(-3 ** 0.5)) < 1e-9)
        """,
    ),

    entry(
        databaseId = 35,
        kind = Kind.GUIDE,
        section = "Equation solving",
        title = "Raphson's iteration for a cubic root",
        originalTitle = "Analysis aequationum universalis",
        author = "Joseph Raphson",
        year = 1690,
        summary = "Raphson's 1690 book gave a way to improve a rough root of a polynomial by repeating a simple correction.",
        details = "The method is a form of what is now called Newton's method, presented in London in 1690 in the Analysis aequationum universalis. The example applies the correction to x cubed minus 2x minus 5, whose root is about 2.0946.",
        rare = true,
        instructions = """
            1. Save the example as raphson.py.
            2. Run it: python3 raphson.py
        """,
        example = """
            def f(x):
                return x ** 3 - 2 * x - 5

            def df(x):
                return 3 * x * x - 2

            x = 2.0
            for _ in range(50):
                step = f(x) / df(x)
                x = x - step
                if abs(step) < 1e-13:
                    break
            print(f"root of x cubed minus 2x minus 5: {x:.4f}")
            print("residual is tiny:", abs(f(x)) < 1e-9)
        """,
    ),


    entry(
        databaseId = 35,
        kind = Kind.GUIDE,
        section = "Mechanics",
        title = "Leibniz's vis viva and the speed of a fall",
        originalTitle = "Specimen dynamicum",
        author = "Gottfried Wilhelm Leibniz",
        year = 1695,
        summary = "In 1695 Leibniz argued that the force of a moving body is measured by its mass times the square of its speed.",
        details = "The Specimen dynamicum defended this measure against the rival view that speed alone counts, and Leibniz tied it to the height a body has fallen. The example uses a unit mass and a fixed pull, and checks that vis viva grows in step with the height of the fall.",
        instructions = """
            1. Save the example as vis_viva.py.
            2. Run it: python3 vis_viva.py
        """,
        example = """
            g = 2.0
            m = 1.0
            ratios = []
            for h in (1.0, 4.0, 9.0):
                v = (2 * g * h) ** 0.5
                vv = m * v * v
                ratios.append(vv / h)
                print(f"fall {h:.0f}: speed {v:.0f}, vis viva {vv:.0f}")
            print("same ratio at each height:", len(set(ratios)) == 1)
        """,
    ),

)
