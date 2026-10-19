"""A human pace for one site (L49): a random gap between requests, a cap on requests in any 60 minutes, and a longer break
after every 15–25 requests, the way a person reads a few pages and then stops. The clock and sleep are injectable for tests.

p = Pacer(gap=(25, 55), hour_cap=80, break_every=(15, 25), break_s=(180, 480))
p.wait()        # before each request: sleeps as needed, returns the seconds slept
"""
import random
import time
from collections import deque


class Pacer:
    def __init__(self, gap=(25, 55), hour_cap=80, break_every=(15, 25), break_s=(180, 480), clock=time.time, sleep=time.sleep,
                 rnd=None, log=None):
        self.gap, self.hour_cap, self.break_every, self.break_s = gap, hour_cap, break_every, break_s
        self.clock, self.sleep, self.rnd, self.log = clock, sleep, rnd or random.Random(), log
        self.times = deque()
        self.since_break = 0
        self.next_break = self.rnd.randint(*break_every)

    def wait(self):
        now = self.clock()
        need = 0.0
        if self.times:
            need = max(need, self.rnd.uniform(*self.gap) - (now - self.times[-1]))
        if self.since_break >= self.next_break:              # a longer pause, then a new streak
            need = max(need, self.rnd.uniform(*self.break_s))
            self.since_break, self.next_break = 0, self.rnd.randint(*self.break_every)
            if self.log:
                self.log("pace: break", round(need), "s")
        while self.times and self.times[0] <= now + need - 3600:
            self.times.popleft()
        if len(self.times) >= self.hour_cap:                 # the oldest request of the window must leave it first
            need = max(need, self.times[0] + 3600 - now + 1)
            if self.log:
                self.log("pace: hourly cap", self.hour_cap, "— waiting", round(need), "s")
        if need > 0:
            self.sleep(need)
        self.times.append(self.clock())
        self.since_break += 1
        return need
