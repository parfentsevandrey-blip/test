# Running it unattended

Two units. The earning loop hunts and reconciles; the dashboard is how you
answer it. Both run as your own user — nothing here needs root, and nothing here
holds a private key.

```bash
sudo cp deploy/usdt-agent-earn.service /etc/systemd/system/
sudo cp deploy/usdt-agent-web.service  /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now usdt-agent-earn usdt-agent-web
journalctl -u usdt-agent-earn -f
```

Edit `User=`, `WorkingDirectory=` and the `Environment=` lines first — they are
written for `/opt/usdt-agent` owned by `usdt`.

## The environment file

Put secrets in `/etc/usdt-agent.env`, mode `600`, owned by the same user. The
TOML config is safe to commit; this file is not.

```ini
# Where you get paid. Addresses only — the agent never sees a key.
USDT_WALLET_TRON=T...
USDT_WALLET_BSC=0x...

# Raises the GitHub search rate limit from 10/min to 30/min.
GITHUB_TOKEN=ghp_...

# So the agent can reach you. Without one of these it hunts in silence and
# time-sensitive claims expire in the queue.
TELEGRAM_BOT_TOKEN=123456:ABC...
TELEGRAM_CHAT_ID=987654321
# or: USDT_AGENT_WEBHOOK=https://hooks.slack.com/services/...
```

```bash
sudo install -m 600 -o usdt -g usdt /dev/null /etc/usdt-agent.env
sudo -e /etc/usdt-agent.env
```

## Without systemd

A cron line does the same job less tidily. `earn run --cycles 1` does one pass
and exits, which is what cron wants:

```cron
*/15 * * * * cd /opt/usdt-agent && /usr/bin/env -S PYTHONPATH=src python3 -m usdt_agent \
    -q -c config/agent.toml earn run --cycles 1 >> /var/log/usdt-agent.log 2>&1
```

## What it will and will not do while you sleep

It will: scan for paid work, rank it by USDT per hour after the odds of being
paid, keep the pipeline within `max_open_orders`, watch your address on every
configured chain, match arriving transfers to what was expected, release a
digital deliverable the moment its invoice settles, and message you when money
lands, when a decision is waiting, or when a gig worth interrupting your day
appears.

It will not: claim a bounty in your name, write the code, sign a transaction, or
move a cent. Those stay yours. The queue in `usdt-agent earn approvals` is where
it stops and waits.

## Checking on it

```bash
usdt-agent -c config/agent.toml earn report      # expected vs confirmed
usdt-agent -c config/agent.toml earn collect     # force a reconciliation now
usdt-agent -c config/agent.toml verify           # journal integrity
systemctl status usdt-agent-earn
```

If the treasury is not moving, read the channel blockers first —
`usdt-agent earn channels` — before assuming the agent is broken. "No
opportunities" is usually "no wallet configured" or "GitHub rate-limited".
