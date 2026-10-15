package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 2026 to 2036: short recipes for checks that agents and post-quantum migrations will need. */
val db9Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 9,
        kind = Kind.EXAMPLE,
        section = "Security",
        title = "A post-quantum inventory of settings",
        originalTitle = "Inventory of cryptographic algorithms in configuration",
        author = "Written as a teaching example",
        year = 2027,
        summary = "Walks through a list of settings and flags the algorithms that a large quantum computer could break, with the replacement the standards name.",
        details = "A real inventory reads configuration files and certificates. This example works from a dictionary, so it runs anywhere, and it prints one line for each setting.",
        instructions = """
            1. Save the example as pq_inventory.py.
            2. Run it: python3 pq_inventory.py
        """,
        example = """
            # Algorithms that a large quantum computer could break, with the replacement that the standards name.
            REPLACEMENTS = {
                "X25519": "hybrid X25519+ML-KEM-768",
                "RSA-2048": "ML-DSA-65",
                "ECDSA-P256": "ML-DSA-65",
            }

            settings = {
                "web TLS key exchange": "X25519",
                "web TLS signature": "RSA-2048",
                "backup encryption": "AES-256-GCM",
                "code signing": "ECDSA-P256",
                "internal RPC": "X25519+ML-KEM-768",
                "password hashing": "bcrypt",
            }

            for name, algorithm in settings.items():
                if algorithm in REPLACEMENTS:
                    print(f"{name}: replace {algorithm} with {REPLACEMENTS[algorithm]}")
                elif "ML-" in algorithm:
                    print(f"{name}: already post-quantum ({algorithm})")
                else:
                    print(f"{name}: no known quantum break")
        """,
    ),

    entry(
        databaseId = 9,
        kind = Kind.EXAMPLE,
        section = "Agents",
        title = "An agent loop with a step limit",
        originalTitle = "Plan, act and observe loop with a budget",
        author = "Written as a teaching example",
        year = 2028,
        summary = "A loop that calls one tool per step and stops at a step limit or when the plan says stop.",
        details = "A scripted planner stands in for the model, so the example needs no account. It runs the same plan with a generous limit and then with a limit of two, and shows where each run stops.",
        instructions = """
            1. Save the example as agent_loop.py.
            2. Run it: python3 agent_loop.py
        """,
        example = """
            TOOLS = {
                "search": lambda query: f"3 results for {query}",
                "read": lambda name: f"contents of {name}",
            }

            # A scripted planner stands in for a model: it names the next action at each step.
            SCRIPT = [
                ("search", "retry policy"),
                ("read", "retry.md"),
                ("search", "backoff"),
                ("stop", "the answer is in retry.md"),
            ]

            def run(max_steps):
                for step, (action, argument) in enumerate(SCRIPT, start=1):
                    if step > max_steps:
                        print(f"stopped: step limit {max_steps} reached")
                        return
                    if action == "stop":
                        print(f"step {step}: stop, {argument}")
                        return
                    print(f"step {step}: {action}({argument!r}) -> {TOOLS[action](argument)}")

            run(max_steps=10)
            print("---")
            run(max_steps=2)
        """,
    ),

    entry(
        databaseId = 9,
        kind = Kind.EXAMPLE,
        section = "Verification",
        title = "An exhaustive check of a small sort",
        originalTitle = "Checking a function on every small input",
        author = "Written as a teaching example",
        year = 2027,
        summary = "Runs a sort on every list of up to four values from a set of three, and compares each result with the sorted list.",
        details = "Exhaustive checks are a cheap way to test small functions, and draft code can be checked this way before a review. The example also runs a function that does nothing, to show that the check catches a wrong result.",
        instructions = """
            1. Save the example as sort_check.py.
            2. Run it: python3 sort_check.py
        """,
        example = """
            from itertools import product

            def insertion_sort(values):
                result = list(values)
                for i in range(1, len(result)):
                    key = result[i]
                    j = i - 1
                    while j >= 0 and result[j] > key:
                        result[j + 1] = result[j]
                        j -= 1
                    result[j + 1] = key
                return result

            def do_nothing(values):
                return list(values)

            def failures(sort_function, max_length=4):
                count = 0
                for n in range(max_length + 1):
                    for values in product(range(3), repeat=n):
                        if sort_function(values) != sorted(values):
                            count += 1
                return count

            print(f"lists checked: {sum(3 ** n for n in range(5))}")
            print(f"insertion sort: {failures(insertion_sort)} failures")
            print(f"do nothing: {failures(do_nothing)} failures")
        """,
    ),

    entry(
        databaseId = 9,
        kind = Kind.EXAMPLE,
        section = "Specs",
        title = "A spec table turned into tests",
        originalTitle = "Table-driven tests, written from a spec",
        author = "Written as a teaching example",
        year = 2027,
        summary = "Each row of a spec table is one test case, and the run reports the rows that disagree with the code.",
        details = "The spec is a table of prices and tax rates in cents. The example keeps the table as data and prints a line for each failing row, so a wrong row in the spec or a wrong function shows up at once.",
        instructions = """
            1. Save the example as spec_tests.py.
            2. Run it: python3 spec_tests.py
        """,
        example = """
            def add_tax(price_cents, rate_percent):
                return price_cents + price_cents * rate_percent // 100

            # Each row is one test case from the spec: price in cents, rate in percent, expected total in cents.
            ROWS = [
                (10000, 20, 12000),
                (0, 5, 0),
                (1999, 0, 1999),
                (250, 10, 275),
                (500, 10, 510),   # a wrong row in the spec
            ]

            passed = failed = 0
            for price, rate, total in ROWS:
                actual = add_tax(price, rate)
                if actual == total:
                    passed += 1
                else:
                    failed += 1
                    print(f"FAIL: {price} at {rate}% should be {total}, got {actual}")
            print(f"{passed} passed, {failed} failed")
        """,
    ),

    entry(
        databaseId = 9,
        kind = Kind.EXAMPLE,
        section = "Agents",
        title = "A typed check for a tool call",
        originalTitle = "Checking tool arguments against a schema",
        author = "Written as a teaching example",
        year = 2029,
        summary = "Checks the arguments of a call against a schema: required names, types, allowed values and unknown names.",
        details = "A runtime that checks a call before it runs saves a failed call and an unclear error. The example prints ok for a good call and lists every problem for the two bad ones.",
        instructions = """
            1. Save the example as tool_check.py.
            2. Run it: python3 tool_check.py
        """,
        example = """
            SCHEMA = {
                "name": "send_email",
                "params": {
                    "to": {"type": str, "required": True},
                    "subject": {"type": str, "required": True},
                    "priority": {"type": int, "required": False, "allowed": [1, 2, 3]},
                },
            }

            def check_call(schema, arguments):
                problems = []
                for name, rule in schema["params"].items():
                    if name not in arguments:
                        if rule["required"]:
                            problems.append(f"missing {name}")
                        continue
                    value = arguments[name]
                    if not isinstance(value, rule["type"]):
                        problems.append(f"{name} must be {rule['type'].__name__}")
                    elif "allowed" in rule and value not in rule["allowed"]:
                        problems.append(f"{name} must be one of {rule['allowed']}")
                for name in arguments:
                    if name not in schema["params"]:
                        problems.append(f"unknown argument {name}")
                return problems

            calls = [
                {"to": "ann@example.com", "subject": "Build passed", "priority": 2},
                {"to": "ann@example.com", "priority": 9},
                {"to": 42, "subject": "Hi", "cc": "bob@example.com"},
            ]
            for arguments in calls:
                problems = check_call(SCHEMA, arguments)
                print("ok" if not problems else "rejected: " + "; ".join(problems))
        """,
    ),

    entry(
        databaseId = 9,
        kind = Kind.EXAMPLE,
        section = "Audit",
        title = "A hash chain for an audit log",
        originalTitle = "Hash-chained logs, an old idea that agent platforms need",
        author = "Written as a teaching example",
        year = 2028,
        summary = "Each log entry includes a hash of the entry before it, so a change to any entry breaks every hash after it.",
        details = "The example writes three events, verifies the chain, changes one event and verifies again. The second check reports the first broken entry.",
        instructions = """
            1. Save the example as hash_chain.py.
            2. Run it: python3 hash_chain.py
        """,
        example = """
            import hashlib

            def digest(previous, event):
                return hashlib.sha256((previous + event).encode()).hexdigest()[:8]

            def append(log, event):
                previous = log[-1]["hash"] if log else "0" * 8
                log.append({"event": event, "hash": digest(previous, event)})

            def verify(log):
                previous = "0" * 8
                for index, entry in enumerate(log):
                    if entry["hash"] != digest(previous, entry["event"]):
                        return f"broken at entry {index}"
                    previous = entry["hash"]
                return "chain intact"

            log = []
            for event in ["agent read retry.md", "agent wrote notes.txt", "agent sent report"]:
                append(log, event)
            print(verify(log))
            log[1]["event"] = "agent deleted notes.txt"
            print(verify(log))
        """,
    ),
)
