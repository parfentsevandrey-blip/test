package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1950 to 1960: small programs in the spirit of the early machines, with instructions and code to run. */
val db6Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 6,
        kind = Kind.PROJECT,
        section = "Simulation",
        title = "Metropolis sampler in Python",
        originalTitle = "Metropolis method (1953)",
        author = "Nicholas Metropolis and four co-authors",
        year = 1953,
        summary = "A random walk that accepts or rejects each move, so that the particle spends its time where the energy is low, with the right weight for each state.",
        details = "The method was published in 1953 for simulating the states of a fluid, and its calculations ran on MANIAC. The example uses a particle in a harmonic well at temperature one, where the average energy should come out near one half.",
        instructions = """
            1. Save the example as metropolis.py.
            2. Run it: python3 metropolis.py
            3. Change the number of steps or the seed to see how the average settles.
        """,
        example = """
            import math
            import random

            # Metropolis sampling of a particle in a harmonic well, at temperature 1.
            random.seed(7)
            steps = 200000
            burn_in = 1000          # the first steps are discarded while the walk settles

            def energy(x):
                return 0.5 * x * x

            x = 0.0
            total = 0.0
            for step in range(steps):
                trial = x + random.uniform(-1.0, 1.0)
                change = energy(trial) - energy(x)
                if change <= 0 or random.random() < math.exp(-change):
                    x = trial
                if step >= burn_in:
                    total += energy(x)

            mean = total / (steps - burn_in)
            print(f"mean energy: {mean:.2f} (exact value 0.50)")
        """,
    ),

    entry(
        databaseId = 6,
        kind = Kind.PROJECT,
        section = "Assemblers",
        title = "Drum placement planner",
        originalTitle = "Optimum placement on a drum, as in SOAP (1955)",
        author = "Written as a teaching example, after SOAP",
        year = 1955,
        summary = "A planner for a drum memory: each instruction goes in the slot where the head will be when it is needed, and the planned order is compared with a naive one.",
        details = "SOAP did the same job for the IBM 650's drum. The numbers here come from a model with ten slots and three time units of execution, but the idea is the same.",
        instructions = """
            1. Save the example as planner.py.
            2. Run it: python3 planner.py
            3. Change SLOTS or EXEC to see how the planned slots move.
        """,
        example = """
            SLOTS = 10   # positions around the drum
            EXEC = 3     # time an instruction needs before the next one can be read

            def read_time(now, slot):
                t = now
                while t % SLOTS != slot:
                    t += 1
                return t

            def run(slots):
                now = 0
                last_read = 0
                for slot in slots:
                    last_read = read_time(now, slot)
                    now = last_read + EXEC
                return last_read, now

            def plan(count):
                # Put each instruction in the slot that the head reaches when the CPU is ready for it.
                slots = []
                now = 0
                for _ in range(count):
                    slot = now % SLOTS
                    slots.append(slot)
                    now = read_time(now, slot) + EXEC
                return slots

            naive = list(range(5))
            planned = plan(5)
            print("planned slots:", planned)
            naive_last, naive_done = run(naive)
            planned_last, planned_done = run(planned)
            print(f"naive: last read at {naive_last}, finished at {naive_done}")
            print(f"planned: last read at {planned_last}, finished at {planned_done}")
            print(f"time saved: {naive_done - planned_done}")
        """,
        rare = true,
    ),

    entry(
        databaseId = 6,
        kind = Kind.PROJECT,
        section = "Games",
        title = "Noughts and crosses after OXO",
        originalTitle = "OXO, noughts and crosses on EDSAC (1952)",
        author = "Written as a teaching example, after A. S. Douglas",
        year = 1952,
        summary = "A perfect noughts and crosses player built with minimax search. Two copies play each other, and the game always ends in a draw.",
        details = "A. S. Douglas wrote OXO for the EDSAC at Cambridge in 1952, as part of his research on how people and computers interact. This version searches every position, and a cache of positions keeps the search quick.",
        instructions = """
            1. Save the example as oxo.py.
            2. Run it: python3 oxo.py
            3. The last line reports the result. Both players are perfect, so it is always a draw.
        """,
        example = """
            from functools import lru_cache

            WINS = [(0, 1, 2), (3, 4, 5), (6, 7, 8), (0, 3, 6), (1, 4, 7), (2, 5, 8), (0, 4, 8), (2, 4, 6)]

            def winner(board):
                for a, b, c in WINS:
                    if board[a] != " " and board[a] == board[b] == board[c]:
                        return board[a]
                return None

            @lru_cache(maxsize=None)
            def best(board, player):
                # Returns (score, move) for the player to move: 1 is a win, 0 a draw, -1 a loss.
                result = winner(board)
                if result is not None:
                    return (1 if result == player else -1), None
                if " " not in board:
                    return 0, None
                other = "O" if player == "X" else "X"
                best_score, best_move = -2, None
                for i in range(9):
                    if board[i] == " ":
                        score, _ = best(board[:i] + (player,) + board[i + 1:], other)
                        if -score > best_score:
                            best_score, best_move = -score, i
                return best_score, best_move

            board = (" ",) * 9
            player = "X"
            while winner(board) is None and " " in board:
                _, move = best(board, player)
                board = board[:move] + (player,) + board[move + 1:]
                player = "O" if player == "X" else "X"

            for row in range(3):
                print(" ".join(board[row * 3:row * 3 + 3]))
            print("winner:", winner(board) or "draw")
        """,
        rare = true,
    ),

    entry(
        databaseId = 6,
        kind = Kind.PROJECT,
        section = "Assemblers",
        title = "Two-pass assembler for a toy 704",
        originalTitle = "Two-pass assembly, as in SAP (1956)",
        author = "Written as a teaching example, after SAP",
        year = 1956,
        summary = "A two-pass assembler for a toy machine with 36-bit words, in the spirit of SAP. The first pass finds the labels and the second writes the words.",
        details = "Early assemblers did this work from punched cards. This one reads a list of lines, resolves the labels and prints each word in octal, the way a listing of the time did.",
        instructions = """
            1. Save the example as assemble.py.
            2. Run it: python3 assemble.py
            3. A dash in the label column means the line has no label.
        """,
        example = """
            OPCODES = {"HLT": 0, "LDA": 1, "ADD": 2, "STO": 3}

            source = [
                "START LDA TEN",
                "- ADD ELEVEN",
                "- STO SUM",
                "- HLT 0",
                "TEN DEC 5",
                "ELEVEN DEC 7",
                "SUM DEC 0",
            ]

            def split(line):
                label, op, operand = line.split()
                return (None if label == "-" else label), op, operand

            def assemble(lines):
                # Pass 1: give every label the address of its line.
                labels = {}
                for address, line in enumerate(lines):
                    label, _, _ = split(line)
                    if label is not None:
                        labels[label] = address
                # Pass 2: encode each line, looking up the labels used as operands.
                words = []
                for line in lines:
                    _, op, operand = split(line)
                    value = labels[operand] if operand in labels else int(operand)
                    if op == "DEC":
                        words.append(value)
                    else:
                        words.append((OPCODES[op] << 15) | value)
                return words

            for address, word in enumerate(assemble(source)):
                print(f"{address:04o} {word:012o}")
        """,
        rare = true,
    ),
)
