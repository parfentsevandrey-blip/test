PY ?= python3
export PYTHONPATH := src

.PHONY: help test test-verbose dashboard-html web earn-setup earn-scan wallet collect serve channels \
        scan doctor backtest run report verify strategies lint clean

help:
	@echo "make test        — run the test suite (170 tests, no network needed)"
	@echo ""
	@echo "make web         — the operator dashboard in a browser"
	@echo "make dashboard-html — rebuild docs/dashboard.html (opens with no server)"
	@echo ""
	@echo "  earning half — acquiring USDT"
	@echo "make earn-setup  — where you are on the ladder and what to do next"
	@echo "make earn-scan   — paid work ranked by USDT per hour"
	@echo "make wallet      — watch-only USDT balances across chains"
	@echo "make collect     — reconcile: book only what arrived on-chain"
	@echo "make serve       — run the paid storefront (HTTP 402)"
	@echo ""
	@echo "  trading half — deploying USDT"
	@echo "make doctor     — check data sources, config and live interlocks"
	@echo "make scan       — one-shot: what is on the table right now"
	@echo "make backtest   — 1000-cycle replay against the simulator"
	@echo "make run        — paper-trade against live market data"
	@echo "make report     — build a markdown report from the ledger"
	@echo "make verify     — verify the ledger hash chain"
	@echo "make lint       — ruff + mypy (if installed)"

test:
	$(PY) -m unittest discover -s tests -p 'test_*.py'

test-verbose:
	$(PY) -m unittest discover -s tests -p 'test_*.py' -v

dashboard-html:
	$(PY) tools/build_standalone_dashboard.py

web:
	$(PY) -m usdt_agent -c config/agent.toml web

earn-setup:
	$(PY) -m usdt_agent -q -c config/agent.toml earn setup

earn-scan:
	$(PY) -m usdt_agent -q -c config/agent.toml earn scan

wallet:
	$(PY) -m usdt_agent -q -c config/agent.toml earn wallet

collect:
	$(PY) -m usdt_agent -q -c config/agent.toml earn collect

serve:
	$(PY) -m usdt_agent -c config/agent.toml earn serve

channels:
	$(PY) -m usdt_agent -q -c config/agent.toml earn channels

doctor:
	$(PY) -m usdt_agent -c config/agent.toml doctor

scan:
	$(PY) -m usdt_agent -q -c config/agent.toml scan

backtest:
	$(PY) -m usdt_agent -q -c config/agent.toml backtest --cycles 1000 --step 1800

run:
	$(PY) -m usdt_agent -c config/agent.toml run

report:
	$(PY) -m usdt_agent -c config/agent.toml report

verify:
	$(PY) -m usdt_agent -c config/agent.toml verify

strategies:
	$(PY) -m usdt_agent -c config/agent.toml strategies

lint:
	-ruff check src tests
	-mypy src/usdt_agent

clean:
	rm -rf data __pycache__ .mypy_cache .ruff_cache
	find . -name '__pycache__' -type d -prune -exec rm -rf {} +
