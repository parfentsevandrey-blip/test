package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1930 to 1940: small simulations of the machines and methods of the decade, runnable today. */
val db11Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 11,
        kind = Kind.PROJECT,
        section = "Machines",
        title = "A Turing machine that adds one to a binary number",
        originalTitle = "On computable numbers, with an application to the Entscheidungsproblem (Turing, 1936)",
        author = "Written as a teaching example",
        year = 1936,
        summary = "A machine with a small table of rules reads and writes one cell at a time, and this one adds one to a binary number by moving left and carrying.",
        details = "Turing described the machine in 1936, and the paper was printed in 1937. This is a Python simulation of a machine with six rules, written for this example, not a copy of Turing's notation. It adds one to four binary numbers and prints the result and the number of steps, which can be counted by hand.",
        instructions = """
            1. Save the example as turing_increment.py.
            2. Run it: python3 turing_increment.py
        """,
        example = """
            BLANK = "_"

            # (state, symbol read) -> (symbol to write, head move, next state). Move 1 is right, -1 is left.
            PROGRAM = {
                ("right", "0"): ("0", 1, "right"),
                ("right", "1"): ("1", 1, "right"),
                ("right", BLANK): (BLANK, -1, "carry"),
                ("carry", "1"): ("0", -1, "carry"),
                ("carry", "0"): ("1", -1, "halt"),
                ("carry", BLANK): ("1", -1, "halt"),
            }

            def run(text):
                tape = {i: c for i, c in enumerate(text)}
                head, state, steps = 0, "right", 0
                while state != "halt":
                    symbol = tape.get(head, BLANK)
                    write, move, state = PROGRAM[(state, symbol)]
                    tape[head] = write
                    head += move
                    steps += 1
                cells = [i for i, c in tape.items() if c != BLANK]
                lo, hi = min(cells), max(cells)
                result = "".join(tape.get(i, BLANK) for i in range(lo, hi + 1))
                return result, steps

            for text in ["1011", "111", "0", "1"]:
                result, steps = run(text)
                print(f"{text} -> {result} in {steps} steps")
        """,
    ),

    entry(
        databaseId = 11,
        kind = Kind.PROJECT,
        section = "Cipher machines",
        title = "A three-rotor Enigma round trip",
        originalTitle = "The army Enigma I, in use with the Reichswehr from 1930",
        author = "Written as a teaching example",
        year = 1930,
        summary = "A reflector cipher machine with the same settings encrypts and decrypts with the same motion, and no letter ever encrypts to itself.",
        details = "The example models the army Enigma with rotors I, II and III, reflector B and all settings at A, with no plugboard and no ring offsets. Rotor stepping includes the double step. AAAAA enciphers to BDZGO, and encrypting the result again gives back AAAAA. Reflector B has no fixed points, so the example also checks that no letter encrypts to itself.",
        instructions = """
            1. Save the example as enigma.py.
            2. Run it: python3 enigma.py
        """,
        example = """
            ROTORS = {
                "I": ("EKMFLGDQVZNTOWYHXUSPAIBRCJ", "Q"),
                "II": ("AJDKSIRUXBLHWTMCQGZNPYFVOE", "E"),
                "III": ("BDFHJLCPRTXVZNYEIWGAKMUSQO", "V"),
            }
            REFLECTOR_B = "YRUHQSLDPXNGOKMIEBFZCWVJAT"

            def idx(c):
                return ord(c) - ord("A")

            def ch(i):
                return chr(ord("A") + i % 26)

            class Enigma:
                def __init__(self, order, start):
                    self.order = order
                    self.pos = [idx(c) for c in start]

                def step(self):
                    # Double stepping: the middle rotor moves again when it sits at its own notch.
                    left, middle, right = 0, 1, 2
                    if self.pos[middle] == idx(ROTORS[self.order[middle]][1]):
                        self.pos[left] = (self.pos[left] + 1) % 26
                        self.pos[middle] = (self.pos[middle] + 1) % 26
                    elif self.pos[right] == idx(ROTORS[self.order[right]][1]):
                        self.pos[middle] = (self.pos[middle] + 1) % 26
                    self.pos[right] = (self.pos[right] + 1) % 26

                def through(self, c):
                    x = idx(c)
                    for r in (2, 1, 0):  # right to left, forward wiring
                        wiring = ROTORS[self.order[r]][0]
                        off = self.pos[r]
                        x = (idx(wiring[(x + off) % 26]) - off) % 26
                    x = idx(REFLECTOR_B[x])
                    for r in (0, 1, 2):  # left to right, backward wiring
                        wiring = ROTORS[self.order[r]][0]
                        off = self.pos[r]
                        x = (wiring.index(ch(x + off)) - off) % 26
                    return ch(x)

                def encipher(self, text):
                    out = []
                    for c in text:
                        self.step()
                        out.append(self.through(c))
                    return "".join(out)

            def new_machine():
                return Enigma(["I", "II", "III"], "AAA")

            print("AAAAA ->", new_machine().encipher("AAAAA"))
            cipher = new_machine().encipher("AAAAA")
            print("round trip:", new_machine().encipher(cipher) == "AAAAA")

            long_text = "THEQUICKBROWNFOXJUMPSOVERTHELAZYDOG"
            long_cipher = new_machine().encipher(long_text)
            print("round trip of the long text:", new_machine().encipher(long_cipher) == long_text)
            print("no letter encrypts to itself:", all(p != c for p, c in zip(long_text, long_cipher)))
        """,
    ),

    entry(
        databaseId = 11,
        kind = Kind.PROJECT,
        section = "Linear algebra",
        title = "Solving linear equations by elimination, as the ABC did",
        originalTitle = "The Atanasoff-Berry Computer, built from 1939 to 1942 for systems of linear equations",
        author = "Written as a teaching example",
        year = 1939,
        summary = "Elimination removes one unknown at a time from the equations below, then back substitution finds the unknowns, which is the job the ABC was built for.",
        details = "The Atanasoff-Berry Computer solved systems of linear equations by successive elimination. Sources give its capacity as 29 equations, and some give 30. The example eliminates a three-by-three system exactly with fractions, then substitutes the answer back into each equation. The solution is x=1, y=2, z=3.",
        instructions = """
            1. Save the example as elimination.py.
            2. Run it: python3 elimination.py
        """,
        example = """
            from fractions import Fraction as F

            def solve(rows):
                # Forward elimination with exact fractions, then back substitution.
                n = len(rows)
                m = [[F(v) for v in row] for row in rows]
                for col in range(n):
                    pivot = next(r for r in range(col, n) if m[r][col] != 0)
                    m[col], m[pivot] = m[pivot], m[col]
                    for r in range(col + 1, n):
                        factor = m[r][col] / m[col][col]
                        m[r] = [a - factor * b for a, b in zip(m[r], m[col])]
                x = [F(0)] * n
                for i in reversed(range(n)):
                    s = m[i][n] - sum(m[i][j] * x[j] for j in range(i + 1, n))
                    x[i] = s / m[i][i]
                return x

            A = [[1, 2, 3, 14], [2, 5, 2, 18], [3, 1, 4, 17]]
            x, y, z = solve(A)
            print(f"solution: x={x} y={y} z={z}")
            residuals = [sum(F(c) * v for c, v in zip(row[:3], (x, y, z))) - row[3] for row in A]
            print("residuals all zero:", all(r == 0 for r in residuals))
        """,
    ),

    entry(
        databaseId = 11,
        kind = Kind.PROJECT,
        section = "Arithmetic",
        title = "Multiplying by shifts and adds, as a multiplying punch does",
        originalTitle = "IBM 601 multiplying punch (1931)",
        author = "Written as a teaching example",
        year = 1931,
        summary = "A product is built from one partial product per digit of the multiplier, each shifted by its place value, which is how a multiplying punch works.",
        details = "The IBM 601 read two factors of up to eight digits from a card and punched the product into the same card. The example adds one partial product per digit of the multiplier, each shifted by its place value, and checks the results against ordinary multiplication. The largest product, 99999999 times 99999999, has sixteen digits.",
        instructions = """
            1. Save the example as shift_add.py.
            2. Run it: python3 shift_add.py
        """,
        example = """
            def multiply_by_adding(multiplicand, multiplier):
                # One partial product per digit of the multiplier, each shifted by its place value.
                total = 0
                place = 1
                for digit in reversed(str(multiplier)):
                    total += multiplicand * int(digit) * place
                    place *= 10
                return total

            for a, b in [(1234, 56), (99999999, 99999999)]:
                product = multiply_by_adding(a, b)
                print(f"{a} x {b} = {product}")
            print("agrees with direct multiplication:",
                  multiply_by_adding(1234, 56) == 1234 * 56
                  and multiply_by_adding(99999999, 99999999) == 99999999 * 99999999)
        """,
    ),
)
