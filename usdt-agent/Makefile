PY ?= python3
export PYTHONPATH := src

.PHONY: help test scan doctor backtest run report verify strategies lint clean

help:
	@echo "make test       — run the test suite (99 tests, no network needed)"
	@echo "make doctor     — check data sources, config and live interlocks"
	@echo "make scan       — one-shot: what is on the table right now"
	@echo "make backtest   — 1000-cycle replay against the simulator"
	@echo "make run        — paper-trade against live market data"
	@echo "make report     — build a markdown report from the ledger"
	@echo "make verify     — verify the ledger hash chain"
	@echo "make lint       — ruff + mypy (if installed)"

test:
	$(PY) -m unittest discover -s tests -p 'test_*.py' -v

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
