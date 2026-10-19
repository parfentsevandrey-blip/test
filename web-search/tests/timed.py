"""Запускает команду, пишет её вывод в лог и в конце -- время и пиковую память. usage: timed.py LOG -- CMD..."""
import resource, subprocess, sys, time

log, cmd = sys.argv[1], sys.argv[sys.argv.index("--") + 1:]
t0 = time.time()
with open(log, "w") as fh:
    code = subprocess.call(cmd, stdout=fh, stderr=subprocess.STDOUT)
    ru = resource.getrusage(resource.RUSAGE_CHILDREN)
    fh.write(f"\nTIMED exit={code} wall_s={time.time() - t0:.0f} max_rss_mb={ru.ru_maxrss / 1024:.0f} cpu_s={ru.ru_utime + ru.ru_stime:.0f}\n")
