package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 2026 to 2036: small programs for local-first sync, carbon-aware scheduling, agent permissions and bills of materials. */
val db9Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 9,
        kind = Kind.PROJECT,
        section = "Local-first",
        title = "A to-do list that syncs two replicas",
        originalTitle = "A last-writer-wins map, as in local-first apps",
        author = "Written as a teaching example",
        year = 2030,
        summary = "Two replicas of a to-do list change offline and merge in either direction, and both end with the same list.",
        details = "Each item keeps the clock of its last change and whether it is on the list. A merge keeps the newer entry for each item, so the result is the same in either direction. The example runs the scenario and prints both lists.",
        instructions = """
            1. Save the example as todo_sync.py.
            2. Run it: python3 todo_sync.py
        """,
        example = """
            class Replica:
                def __init__(self, name):
                    self.name = name
                    self.items = {}      # item -> (clock of its last change, on the list?)
                    self.clock = 0

                def set(self, item, on_list):
                    self.clock += 1
                    self.items[item] = (self.clock, on_list)

                def merge(self, other):
                    # The entry with the newer clock wins, whichever replica it came from.
                    for item, entry in other.items.items():
                        if item not in self.items or entry > self.items[item]:
                            self.items[item] = entry

                def todo(self):
                    return sorted(item for item, (_, on_list) in self.items.items() if on_list)

            phone = Replica("phone")
            laptop = Replica("laptop")

            phone.set("milk", True)
            laptop.merge(phone)              # the laptop has seen the milk
            phone.set("bread", True)         # offline edits from here on
            phone.set("milk", False)         # bought it
            laptop.set("eggs", True)
            laptop.set("tea", True)
            laptop.set("bread", False)       # crossed off on the laptop

            phone.merge(laptop)
            laptop.merge(phone)
            print("phone:", phone.todo())
            print("laptop:", laptop.todo())
            print("same:", phone.todo() == laptop.todo())
        """,
    ),

    entry(
        databaseId = 9,
        kind = Kind.PROJECT,
        section = "Green software",
        title = "A carbon-aware job scheduler",
        originalTitle = "Scheduling jobs into the cleanest hours",
        author = "Written as a teaching example",
        year = 2029,
        summary = "Places each job in the window with the lowest grid carbon intensity that still meets its deadline, then reports the plan.",
        details = "The example takes 24 hourly intensity values and three jobs with deadlines. It places the heaviest job first, then the others, and prints the start hour and the carbon estimate of each.",
        instructions = """
            1. Save the example as scheduler.py.
            2. Run it: python3 scheduler.py
        """,
        example = """
            # Grid carbon intensity in grams of CO2 per kWh, for each hour of the day starting at midnight.
            intensity = [400, 380, 300, 260, 240, 230, 250, 290, 360, 400, 420, 430,
                         410, 390, 350, 310, 280, 270, 300, 350, 400, 420, 430, 440]

            # Each job: name, hours it needs, kWh used per hour, and the hour by which it must finish.
            jobs = [("report", 1, 0.5, 6), ("backup", 2, 1.5, 8), ("training", 3, 4.0, 24)]

            free = [True] * 24
            schedule = []
            total = 0.0
            for name, hours, kwh, deadline in sorted(jobs, key=lambda job: -job[2]):   # heaviest first
                best = None
                for start in range(deadline - hours + 1):
                    block = range(start, start + hours)
                    if all(free[h] for h in block):
                        cost = sum(intensity[h] for h in block) * kwh
                        if best is None or cost < best[0]:
                            best = (cost, start)
                cost, start = best
                for h in range(start, start + hours):
                    free[h] = False
                total += cost
                schedule.append((name, start, round(cost)))

            for name, start, grams in sorted(schedule, key=lambda entry: entry[1]):
                print(f"{name}: start at {start:02d}:00, {grams} g CO2")
            print(f"total: {round(total)} g CO2")
        """,
    ),

    entry(
        databaseId = 9,
        kind = Kind.PROJECT,
        section = "Agents",
        title = "A permission checker for agents",
        originalTitle = "Deny-first policies for tool calls",
        author = "Written as a teaching example",
        year = 2028,
        summary = "Evaluates tool calls against a policy in which a deny rule always beats an allow rule, and a call with no matching allow is refused.",
        details = "The policy is a list of rules with a scope. The checker prints the decision and the reason for each request, so the policy can be reviewed like code.",
        instructions = """
            1. Save the example as permissions.py.
            2. Run it: python3 permissions.py
        """,
        example = """
            # Each rule: effect, tool, and scope (None means any scope).
            POLICY = [
                ("deny", "send_email", "external"),   # no mail to outside addresses
                ("allow", "search", None),
                ("allow", "read_file", "project"),    # only inside the project folder
                ("allow", "send_email", "internal"),
                ("allow", "send_email", None),        # a broad allow does not beat a deny
            ]

            def decide(tool, scope):
                matches = [effect for effect, rule_tool, rule_scope in POLICY
                           if rule_tool == tool and rule_scope in (None, scope)]
                if "deny" in matches:
                    return "deny: matched a deny rule"
                if "allow" in matches:
                    return "allow"
                return "deny: no rule allows it"

            requests = [
                ("search", "web"),
                ("read_file", "project"),
                ("read_file", "home"),
                ("send_email", "internal"),
                ("send_email", "external"),
                ("delete_file", "project"),
            ]
            for tool, scope in requests:
                print(f"{tool} in {scope}: {decide(tool, scope)}")
        """,
    ),

    entry(
        databaseId = 9,
        kind = Kind.PROJECT,
        section = "Supply chain",
        title = "An SBOM writer for a lock file",
        originalTitle = "Writing a CycloneDX bill of materials",
        author = "Written as a teaching example",
        year = 2027,
        summary = "Reads a simplified lock file and writes a CycloneDX bill of materials, sorted by name, with the hash of each component.",
        details = "The lock file here is a dictionary with placeholder hashes, so the example runs without a network. The output has the shape of a real CycloneDX document, and the example prints the first component in full.",
        instructions = """
            1. Save the example as sbom.py.
            2. Run it: python3 sbom.py
        """,
        example = """
            import json

            # A simplified lock file. The hashes are placeholders, not real digests.
            LOCK = {
                "packages": [
                    {"name": "requests", "version": "2.32.3", "sha256": "ab12"},
                    {"name": "certifi", "version": "2024.8.30", "sha256": "cd34"},
                    {"name": "idna", "version": "3.7", "sha256": "ef56"},
                ]
            }

            def sbom(lock, project):
                components = [
                    {
                        "type": "library",
                        "name": package["name"],
                        "version": package["version"],
                        "hashes": [{"alg": "SHA-256", "content": package["sha256"]}],
                    }
                    for package in sorted(lock["packages"], key=lambda package: package["name"])
                ]
                return {
                    "bomFormat": "CycloneDX",
                    "specVersion": "1.5",
                    "metadata": {"component": {"type": "application", "name": project}},
                    "components": components,
                }

            bom = sbom(LOCK, "example-app")
            print(bom["bomFormat"], bom["specVersion"], "with", len(bom["components"]), "components")
            print(json.dumps(bom["components"][0], sort_keys=True))
            for component in bom["components"]:
                print(f"  {component['name']} {component['version']} {component['hashes'][0]['content']}")
        """,
    ),
)
