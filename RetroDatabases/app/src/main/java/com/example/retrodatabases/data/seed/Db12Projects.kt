package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1920 to 1930: a truth-table checker, a combinator reducer, a process chart and a sampling plan. */
val db12Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 12,
        kind = Kind.PROJECT,
        section = "Logic",
        title = "Truth-table checker for propositional formulas",
        originalTitle = "Truth tables (Wittgenstein and Post, 1921)",
        author = "Written as a teaching example",
        year = 1921,
        summary = "Prints the truth table of two formulas and reports whether each one is a tautology.",
        details = "Wittgenstein's Tractatus and Post's thesis both reached truth tables in 1921, and the priority between them is disputed. The example writes each formula as a Python expression over two variables, enumerates the four assignments, prints the table with 1 for true and 0 for false, and counts the true rows to decide whether the formula is a tautology.",
        instructions = """
            1. Save the example as truth_table.py.
            2. Run it: python3 truth_table.py
        """,
        example = """
            from itertools import product

            # Two formulas written as Python expressions over the variables p and q.
            FORMULAS = [
                ("(not p) or q", lambda p, q: (not p) or q),
                ("p or (not p)", lambda p, q: p or (not p)),
            ]

            print("p q  " + "  ".join(name for name, _ in FORMULAS))
            for p, q in product([True, False], repeat=2):
                row = " ".join(str(int(f(p, q))) for _, f in FORMULAS)
                print(f"{int(p)} {int(q)}  {row}")

            for name, f in FORMULAS:
                rows = sum(1 for p, q in product([True, False], repeat=2) if f(p, q))
                print(f"{name}: true in {rows} of 4 rows, tautology: {rows == 4}")
        """,
    ),

    entry(
        databaseId = 12,
        kind = Kind.PROJECT,
        section = "Combinators",
        title = "Combinator reducer with a step limit",
        originalTitle = "Combinatory logic (Schonfinkel, 1924)",
        author = "Written as a teaching example",
        year = 1924,
        summary = "Reduces a term built from the combinators S, K and I by their three rewriting rules, and prints each step until no rule applies.",
        details = "Schonfinkel gave the lecture behind the theory in December 1920 and published it in Mathematische Annalen in 1924, so the year is 1924. Curry developed the theory later. The example reduces S K K a, which equals a, in two steps. It prints each term, the way a reduction is written on paper, and it stops when no rule applies.",
        instructions = """
            1. Save the example as combinators.py.
            2. Run it: python3 combinators.py
        """,
        example = """
            # Terms are atoms (S, K, I or a variable name) or pairs (function, argument).
            def app(f, x):
                return (f, x)

            def show(term):
                if isinstance(term, str):
                    return term
                f, x = term
                left = show(f)
                right = show(x)
                if isinstance(x, tuple):
                    right = "(" + right + ")"
                return left + " " + right

            def step(term):
                # Leftmost-outermost rewriting: returns the reduced term, or None if no rule applies.
                if not isinstance(term, tuple):
                    return None
                f, x = term
                if f == "I":
                    return x
                if isinstance(f, tuple) and f[0] == "K":
                    return f[1]
                if isinstance(f, tuple) and isinstance(f[0], tuple) and f[0][0] == "S":
                    a, b = f[0][1], f[1]
                    return app(app(a, x), app(b, x))
                reduced = step(f)
                if reduced is not None:
                    return app(reduced, x)
                reduced = step(x)
                if reduced is not None:
                    return app(f, reduced)
                return None

            def run(term, limit=10):
                print(show(term))
                for _ in range(limit):
                    term = step(term)
                    if term is None:
                        print("normal form reached")
                        return
                    print(show(term))
                print("stopped after", limit, "steps")

            run(app(app(app("S", "K"), "K"), "a"))
        """,
    ),

    entry(
        databaseId = 12,
        kind = Kind.PROJECT,
        section = "Process charts",
        title = "Flow process chart in text",
        originalTitle = "Process charts, presented to ASME (Gilbreth, 1921)",
        author = "Frank and Lillian Gilbreth",
        year = 1921,
        summary = "Lists each step of a job with a letter for its kind, then totals the minutes by kind, as a flow process chart does.",
        details = "Frank and Lillian Gilbreth presented the process chart to the American Society of Mechanical Engineers in 1921, and the 1947 ASME standard formalised its symbols. Accounts differ on whether Frank alone or both spouses are credited. The example uses letters for the five kinds of step: operation, transport, inspection, delay and storage. It prints the steps, the minutes for each kind and the total.",
        instructions = """
            1. Save the example as flow_chart.py.
            2. Run it: python3 flow_chart.py
        """,
        example = """
            # Each step: a letter for its kind (O operation, T transport, I inspection, D delay, S storage), a description and minutes.
            STEPS = [
                ("O", "Pick up part", 0.5),
                ("T", "Carry to press", 1.0),
                ("I", "Check size", 0.25),
                ("D", "Wait for press", 2.0),
                ("S", "Put in bin", 0.5),
            ]
            NAMES = {"O": "operation", "T": "transport", "I": "inspection", "D": "delay", "S": "storage"}

            print("step sym description minutes")
            for number, (sym, text, minutes) in enumerate(STEPS, start=1):
                print(f"{number} {sym} {text} {minutes:.2f}")

            totals = {}
            for sym, _, minutes in STEPS:
                totals[sym] = totals.get(sym, 0.0) + minutes
            for sym in "OTIDS":
                print(f"{NAMES[sym]}: {totals.get(sym, 0.0):.2f} minutes")
            print(f"total: {sum(m for _, _, m in STEPS):.2f} minutes")
        """,
    ),

    entry(
        databaseId = 12,
        kind = Kind.PROJECT,
        section = "Sampling inspection",
        title = "Single sampling plan acceptance probability",
        originalTitle = "A Method of Sampling Inspection (Dodge and Romig, 1929)",
        author = "Written as a teaching example",
        year = 1929,
        summary = "Computes the chance that a lot is accepted under a single sampling plan, which samples five items and accepts the lot if at most one is defective.",
        details = "Dodge and Romig's method appeared in the October 1929 Bell System Technical Journal. It judged lots by probabilities and kept the exposure to defective goods low at minimal inspection cost. The example uses a simple single plan rather than their tables: it draws five items and accepts the lot if at most one is defective, and it computes the acceptance probability with exact fractions at two fractions defective.",
        instructions = """
            1. Save the example as sampling.py.
            2. Run it: python3 sampling.py
        """,
        example = """
            from fractions import Fraction
            from math import comb

            # Single sampling plan: draw n items from a large lot and accept the lot if at most c are defective.
            def accept_probability(n, c, p):
                return sum(comb(n, k) * p ** k * (1 - p) ** (n - k) for k in range(c + 1))

            n, c = 5, 1
            for p in (Fraction(1, 10), Fraction(3, 10)):
                prob = accept_probability(n, c, p)
                print(f"fraction defective {float(p):.2f}: accept with probability {float(prob):.5f}")
        """,
    ),
)
