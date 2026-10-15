package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1970 to 1980: how public-key exchange, RSA, logical clocks, joins and message lookup work, each with a small program to try. */
val db8Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 8,
        kind = Kind.GUIDE,
        section = "Cryptography",
        title = "How Diffie–Hellman key agreement works",
        originalTitle = "New directions in cryptography (Diffie and Hellman, 1976)",
        author = "Whitfield Diffie and Martin Hellman",
        year = 1976,
        summary = "Two people agree on a secret number over a public channel by exchanging powers of a shared base modulo a prime, without sending the secret itself.",
        details = "Each side keeps a private exponent and sends the public value that results. Both can then compute the same shared value, while someone who hears only the public values has to solve a discrete logarithm to find it. " +
            "The example uses a tiny prime so that every step can be checked by hand.",
        instructions = """
            1. Save the example as diffie_hellman.py.
            2. Run it: python3 diffie_hellman.py
        """,
        example = """
            p, g = 23, 5
            a, b = 6, 15            # private exponents, kept secret
            A = pow(g, a, p)        # Alice sends A
            B = pow(g, b, p)        # Bob sends B
            print("public values:", A, B)
            print("shared secret seen by Alice:", pow(B, a, p))
            print("shared secret seen by Bob:", pow(A, b, p))
        """,
    ),

    entry(
        databaseId = 8,
        kind = Kind.GUIDE,
        section = "Cryptography",
        title = "How RSA works",
        originalTitle = "RSA public-key cryptosystem (Rivest, Shamir and Adleman, 1977)",
        author = "Ronald Rivest, Adi Shamir and Leonard Adleman",
        year = 1977,
        summary = "Anyone can encrypt with the public exponent and the modulus, but only the holder of the private exponent can decrypt, because that exponent depends on the secret factors.",
        details = "The modulus is the product of two secret primes. The private exponent is the inverse of the public exponent modulo (p - 1) times (q - 1). " +
            "The example uses the textbook primes 61 and 53, so the numbers stay small.",
        instructions = """
            1. Save the example as rsa.py.
            2. Run it: python3 rsa.py
        """,
        example = """
            p, q = 61, 53
            n = p * q
            phi = (p - 1) * (q - 1)
            e = 17
            d = pow(e, -1, phi)
            message = 65
            cipher = pow(message, e, n)
            print(f"n = {n}, phi = {phi}, private exponent d = {d}")
            print("encrypted", message, "->", cipher)
            print("decrypted", cipher, "->", pow(cipher, d, n))
        """,
    ),

    entry(
        databaseId = 8,
        kind = Kind.GUIDE,
        section = "Distributed systems",
        title = "How Lamport clocks order events",
        originalTitle = "Time, clocks, and the ordering of events in a distributed system (1978)",
        author = "Leslie Lamport",
        year = 1978,
        summary = "Each process counts its events, and messages carry the counter, so that the counts respect cause and effect even without a shared clock.",
        details = "A process adds one before each event and each send. On receiving a message, it takes the larger of its own counter and the message's counter, then adds one. " +
            "The example runs three processes, where the first sends to the second and the second sends to the third, and prints the counters.",
        instructions = """
            1. Save the example as lamport.py.
            2. Run it: python3 lamport.py
        """,
        example = """
            clock = {"P1": 0, "P2": 0, "P3": 0}

            def local(p):
                clock[p] += 1
                print(f"{p} local event: clock {clock[p]}")

            def send(p):
                clock[p] += 1
                print(f"{p} sends with clock {clock[p]}")
                return clock[p]

            def receive(p, stamp):
                clock[p] = max(clock[p], stamp) + 1
                print(f"{p} receives, clock {clock[p]}")

            local("P1")
            m1 = send("P1")
            local("P2")
            receive("P2", m1)
            m2 = send("P2")
            receive("P3", m2)
            local("P3")
        """,
    ),

    entry(
        databaseId = 8,
        kind = Kind.GUIDE,
        section = "Relational databases",
        title = "How a relational join works",
        originalTitle = "Nested-loop join, the simplest method in System R",
        author = "Written as a teaching example",
        year = 1974,
        summary = "A join matches the rows of two tables on a shared column. The simplest method checks every pair of rows, and System R's optimiser chose cheaper methods when it could.",
        details = "The example joins two small tables by checking each employee against each department. The result has one row for each matching pair, with the columns of both tables.",
        instructions = """
            1. Save the example as join.py.
            2. Run it: python3 join.py
        """,
        example = """
            employees = [
                {"name": "Ann", "dept": 10},
                {"name": "Bob", "dept": 20},
                {"name": "Cy", "dept": 10},
            ]
            departments = [
                {"dept": 10, "title": "Research"},
                {"dept": 20, "title": "Sales"},
            ]

            def nested_loop_join(left, right, key):
                # For every row on the left, scan the right table for rows with the same key.
                result = []
                for l in left:
                    for r in right:
                        if l[key] == r[key]:
                            result.append({**l, **r})
                return result

            for row in nested_loop_join(employees, departments, "dept"):
                print(row["name"], "works in", row["title"])
        """,
    ),

    entry(
        databaseId = 8,
        kind = Kind.GUIDE,
        section = "Object-oriented",
        title = "How a message finds its method",
        originalTitle = "Message lookup through the superclass chain (Smalltalk, 1972)",
        author = "Written as a teaching example",
        year = 1972,
        summary = "A message is looked up in the receiver's class, then in its superclass, and so on up the chain, which is how inherited methods are found.",
        details = "Each class keeps a table of its own methods and a pointer to its superclass. The example builds three classes and sends them messages, and it reports which class answered each one.",
        instructions = """
            1. Save the example as lookup.py.
            2. Run it: python3 lookup.py
        """,
        example = """
            class Cls:
                def __init__(self, name, superclass=None):
                    self.name = name
                    self.superclass = superclass
                    self.methods = {}

                def lookup(self, selector):
                    cls = self
                    while cls is not None:
                        if selector in cls.methods:
                            return cls.methods[selector], cls.name
                        cls = cls.superclass
                    return None, None

            def send(receiver_class, selector, *args):
                method, found_in = receiver_class.lookup(selector)
                if method is None:
                    return f"{selector} is not understood by {receiver_class.name}"
                return f"{selector} is answered by {found_in}: {method(*args)}"

            Object = Cls("Object")
            Number = Cls("Number", Object)
            Integer = Cls("Integer", Number)

            Object.methods["describe"] = lambda x: "something"
            Number.methods["double"] = lambda x: x * 2
            Integer.methods["isEven"] = lambda x: x % 2 == 0

            print(send(Integer, "describe", 4))
            print(send(Integer, "double", 4))
            print(send(Integer, "isEven", 4))
            print(send(Number, "isEven", 4))
        """,
    ),
)
