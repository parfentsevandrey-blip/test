package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1950 to 1960: how core memory, instructions, drums and lists worked, with a small program to try where the sandbox allows. */
val db6Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 6,
        kind = Kind.GUIDE,
        section = "Memory",
        title = "How a magnetic core remembers a bit",
        originalTitle = "Coincident-current core memory",
        author = "Written as a teaching explanation",
        year = 1953,
        summary = "A tiny ferrite ring is magnetised one way or the other, and two wires carrying half the current needed to flip it select the one ring to change.",
        details = "A core memory is a grid of ferrite rings threaded by wires. Current in one x wire and one y wire together is enough to flip the core where they cross, while either wire alone is not. " +
            "A third sense wire through every core detects whether the core flipped, which is how the bit is read. Reading a core also erases it, so the value is written back afterwards.",
        rare = true,
    ),

    entry(
        databaseId = 6,
        kind = Kind.GUIDE,
        section = "Architecture",
        title = "How a stored-program computer runs one instruction",
        originalTitle = "The fetch, decode and execute cycle",
        author = "Written as a teaching example",
        year = 1954,
        summary = "The machine fetches a word from memory, decodes it into an operation and an address, carries the operation out and moves on to the next word.",
        details = "A stored-program machine keeps its instructions in the same memory as its data. The program below is a toy with 36-bit words, as on the IBM 704, and a 15-bit address field. " +
            "It runs four instructions: load, add, store and halt. Each pass through the loop is one fetch, one decode and one execution.",
        instructions = """
            1. Save the example as cycle.py.
            2. Run it: python3 cycle.py
        """,
        example = """
            OPCODES = {"HLT": 0, "LDA": 1, "ADD": 2, "STO": 3}
            NAMES = {code: name for name, code in OPCODES.items()}
            WORD = (1 << 36) - 1                        # 36-bit words, as on the IBM 704

            def encode(op, address):
                return (OPCODES[op] << 15) | address    # opcode in the top bits, address in the low 15

            def decode(word):
                return NAMES[word >> 15], word & 0x7FFF

            memory = [0] * 32
            memory[0] = encode("LDA", 10)
            memory[1] = encode("ADD", 11)
            memory[2] = encode("STO", 12)
            memory[3] = encode("HLT", 0)
            memory[10] = 5
            memory[11] = 7

            accumulator = 0
            counter = 0
            while True:
                word = memory[counter] & WORD           # fetch the word at the counter
                op, address = decode(word)              # decode it into an operation and an address
                print(f"{counter}: {op} {address}")
                counter += 1
                if op == "HLT":                         # halt
                    break
                if op == "LDA":                         # execute
                    accumulator = memory[address]
                elif op == "ADD":
                    accumulator = (accumulator + memory[address]) & WORD
                elif op == "STO":
                    memory[address] = accumulator
            print("accumulator", accumulator, "stored at 12:", memory[12])
        """,
    ),

    entry(
        databaseId = 6,
        kind = Kind.GUIDE,
        section = "Debugging",
        title = "Reading an octal dump of 36-bit words",
        originalTitle = "Octal listings of core memory",
        author = "Written as a teaching example",
        year = 1955,
        summary = "A memory printout in octal turns each 36-bit word into twelve digits, and the top bit is the sign.",
        details = "Octal digits group three bits each, so a 36-bit word becomes twelve octal digits, and programmers read dumps this way on the 704 and its relatives. " +
            "The example prints five words with their addresses, also in octal, and shows the top bit of each.",
        instructions = """
            1. Save the example as dump.py.
            2. Run it: python3 dump.py
        """,
        example = """
            words = [5, 7, 12, 1 << 35, (1 << 36) - 1]
            for address, word in enumerate(words):
                print(f"{address:04o} {word:012o} top bit {word >> 35}")
        """,
    ),

    entry(
        databaseId = 6,
        kind = Kind.GUIDE,
        section = "Storage",
        title = "How a drum delivers the next instruction",
        originalTitle = "Rotational timing on a magnetic drum",
        author = "Written as a teaching example",
        year = 1955,
        summary = "The drum turns past a fixed read head, so the next instruction has to be placed where the head will be when the machine needs it.",
        details = "The example models ten positions around the drum and three time units of execution. Placed one after another, five instructions finish at time 47; placed where the head will be, they finish at time 15. " +
            "The planned placement is the idea behind SOAP.",
        instructions = """
            1. Save the example as drum_guide.py.
            2. Run it: python3 drum_guide.py
        """,
        example = """
            SLOTS = 10   # positions around the drum
            EXEC = 3     # time units an instruction needs before the next one can be read

            def read_time(now, slot):
                # The head passes slot s at every time t where t % SLOTS equals s.
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

            naive = [0, 1, 2, 3, 4]        # instructions placed one slot after another
            planned = [0, 3, 6, 9, 2]      # each one placed where the head will be when it is needed
            for name, slots in [("naive", naive), ("planned", planned)]:
                last, done = run(slots)
                print(f"{name}: last read at {last}, finished at {done}")
        """,
    ),

    entry(
        databaseId = 6,
        kind = Kind.GUIDE,
        section = "List processing",
        title = "How IPL stores a list: cells, links and the free list",
        originalTitle = "List structures in IPL",
        author = "Written as a teaching example",
        year = 1956,
        summary = "A list is a chain of cells, each holding a value and the address of the next cell. IPL kept its unused cells on a list of their own.",
        details = "The example stores the cells in an array, takes cells from the free list and pushes words onto a list. Reading the list back gives the words in the reverse of the order they were pushed, because each new cell goes to the front.",
        instructions = """
            1. Save the example as ipl.py.
            2. Run it: python3 ipl.py
        """,
        example = """
            # Memory is a plain array of cells. Each cell holds a value and the index of the next cell.
            SIZE = 6
            value = [None] * SIZE
            link = list(range(1, SIZE)) + [-1]     # every free cell points to the next free one
            free = 0                               # the free list starts at cell 0

            def new_cell():
                global free
                cell = free
                free = link[cell]
                return cell

            def push(item, head):
                cell = new_cell()
                value[cell] = item
                link[cell] = head
                return cell

            def read_list(head):
                items = []
                while head != -1:
                    items.append(value[head])
                    head = link[head]
                return items

            head = -1
            for word in ["ADD", "LOAD", "STORE"]:
                head = push(word, head)
            print(read_list(head))
            print("next free cell:", free)
        """,
        rare = true,
    ),

    entry(
        databaseId = 6,
        kind = Kind.GUIDE,
        section = "Memory",
        title = "Why a drum needs careful placement",
        originalTitle = "Drum memory and access time",
        author = "Written as a teaching explanation",
        year = 1955,
        summary = "A drum memory makes a program wait for the part it needs, so the wait depends on where each word is stored, not only on how fast the drum turns.",
        details = "A word on the drum can be read only when it passes the head. A program that stores its next instruction in the next slot may have to wait almost a full turn for it. " +
            "Storing the instructions in the slots the head reaches at the right moment cuts the wait, and this is the reason assemblers for drum machines were built with timing in mind.",
        rare = true,
    ),
)
