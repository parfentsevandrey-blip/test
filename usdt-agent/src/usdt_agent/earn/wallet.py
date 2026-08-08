"""Watch-only, keyless USDT wallet across EVM chains and Tron.

This is the module that makes the earning agent honest. It never holds a
private key, never signs anything, and cannot move a cent — it only *reads*
public chain state to answer one question: **did the money actually arrive?**

Two independent detection paths, because public RPCs are flaky:

1. ``transfers()`` — enumerate incoming USDT ``Transfer`` logs. Precise (gives
   tx hash, sender, amount) but needs an RPC that will serve ``eth_getLogs``
   over a useful block range, which many free endpoints will not.
2. ``balances()`` — read ``balanceOf``. Always works. Combined with the last
   known balance it yields a delta, which proves money arrived even when the
   log query fails.

Path 2 is the fallback for path 1 and is never skipped, so a channel can always
be reconciled even against a hostile RPC.
"""

from __future__ import annotations

import logging
from dataclasses import dataclass, field
from typing import Any

from .. import http
from .models import OnChainTransfer

log = logging.getLogger(__name__)

#: keccak256("Transfer(address,address,uint256)")
TRANSFER_TOPIC = "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef"
#: keccak256("balanceOf(address)")[:4]
BALANCE_OF_SELECTOR = "0x70a08231"

TRON_USDT = "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t"


@dataclass(frozen=True, slots=True)
class ChainSpec:
    """Everything needed to read USDT on one chain, with no key."""

    name: str
    kind: str                      # "evm" | "tron"
    token: str                     # USDT contract address
    decimals: int
    rpcs: tuple[str, ...] = ()
    explorer: str = ""
    max_log_span: int = 2_000      # blocks per eth_getLogs call


CHAINS: dict[str, ChainSpec] = {
    "ethereum": ChainSpec(
        "ethereum", "evm", "0xdAC17F958D2ee523a2206206994597C13D831ec7", 6,
        ("https://ethereum-rpc.publicnode.com", "https://eth.drpc.org",
         "https://1rpc.io/eth", "https://eth.merkle.io"),
        "https://etherscan.io/tx/",
    ),
    "bsc": ChainSpec(
        "bsc", "evm", "0x55d398326f99059fF775485246999027B3197955", 18,
        ("https://bsc-dataseed.binance.org", "https://bsc-rpc.publicnode.com"),
        "https://bscscan.com/tx/",
    ),
    "arbitrum": ChainSpec(
        "arbitrum", "evm", "0xFd086bC7CD5C481DCC9C85ebE478A1C0b69FCbb9", 6,
        ("https://arb1.arbitrum.io/rpc", "https://arbitrum-one-rpc.publicnode.com"),
        "https://arbiscan.io/tx/", max_log_span=10_000,
    ),
    "base": ChainSpec(
        # Base has no canonical Tether USDT; USDC is the stable of record there.
        "base", "evm", "0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913", 6,
        ("https://mainnet.base.org", "https://base-rpc.publicnode.com"),
        "https://basescan.org/tx/", max_log_span=10_000,
    ),
    "polygon": ChainSpec(
        "polygon", "evm", "0xc2132D05D31c914a87C6611C10748AEb04B58e8F", 6,
        ("https://polygon-bor-rpc.publicnode.com", "https://polygon-rpc.com"),
        "https://polygonscan.com/tx/",
    ),
    "tron": ChainSpec(
        "tron", "tron", TRON_USDT, 6,
        ("https://api.trongrid.io",),
        "https://tronscan.org/#/transaction/",
    ),
}

#: Chains worth defaulting to when receiving small payments: cheap and fast.
RECOMMENDED_CHAINS = ("tron", "bsc", "arbitrum", "polygon")


class ChainError(RuntimeError):
    pass


def _pad_address(address: str) -> str:
    return address.lower().replace("0x", "").rjust(64, "0")


@dataclass
class Wallet:
    """A read-only view of USDT held at one address per chain."""

    addresses: dict[str, str] = field(default_factory=dict)
    timeout: float = 15.0
    min_confirmations: int = 12

    # -- helpers ---------------------------------------------------------
    def chains(self) -> list[str]:
        return [c for c in self.addresses if c in CHAINS]

    def _rpc(self, spec: ChainSpec, method: str, params: list[Any]) -> Any:
        """Call an EVM RPC, failing over between public endpoints."""
        last: Exception | None = None
        for url in spec.rpcs:
            try:
                resp = http.post_json(
                    url, {"jsonrpc": "2.0", "id": 1, "method": method, "params": params},
                    timeout=self.timeout, retries=1,
                )
                if isinstance(resp, dict) and resp.get("error"):
                    last = ChainError(f"{url}: {resp['error']}")
                    continue
                return (resp or {}).get("result")
            except Exception as e:
                last = e
        raise ChainError(f"all RPCs failed for {spec.name}: {last}")

    # -- balances --------------------------------------------------------
    def balance(self, chain: str) -> float:
        """USDT balance on one chain. Raises :class:`ChainError` on failure."""
        spec = CHAINS.get(chain)
        address = self.addresses.get(chain, "")
        if spec is None:
            raise ChainError(f"unknown chain {chain!r}")
        if not address:
            raise ChainError(f"no address configured for {chain}")

        if spec.kind == "tron":
            data = http.get_json(
                f"{spec.rpcs[0]}/v1/accounts/{address}", timeout=self.timeout, retries=2
            ) or {}
            records = data.get("data") or []
            if not records:
                return 0.0
            total = 0
            for entry in records[0].get("trc20", []) or []:
                for token, raw in entry.items():
                    if token == spec.token:
                        total += int(raw)
            return total / (10**spec.decimals)

        result = self._rpc(spec, "eth_call", [
            {"to": spec.token, "data": BALANCE_OF_SELECTOR + _pad_address(address)},
            "latest",
        ])
        if not result or result == "0x":
            return 0.0
        return int(result, 16) / (10**spec.decimals)

    def balances(self) -> tuple[dict[str, float], dict[str, str]]:
        """Balances per chain plus per-chain errors. Never raises."""
        out: dict[str, float] = {}
        errors: dict[str, str] = {}
        for chain in self.chains():
            try:
                out[chain] = self.balance(chain)
            except Exception as e:
                errors[chain] = str(e)[:160]
                log.warning("balance check failed for %s: %s", chain, e)
        return out, errors

    def total(self) -> float:
        balances, _ = self.balances()
        return sum(balances.values())

    # -- transfers -------------------------------------------------------
    def block_number(self, chain: str) -> int:
        spec = CHAINS[chain]
        if spec.kind == "tron":
            data = http.get_json(f"{spec.rpcs[0]}/wallet/getnowblock", timeout=self.timeout) or {}
            return int((data.get("block_header") or {}).get("raw_data", {}).get("number", 0))
        return int(self._rpc(spec, "eth_blockNumber", []) or "0x0", 16)

    def transfers(self, chain: str, lookback_blocks: int = 2_000) -> list[OnChainTransfer]:
        """Incoming USDT transfers in the recent window.

        Returns an empty list — never raises — when the RPC refuses the range,
        because :meth:`balance` remains available as the fallback proof.
        """
        spec = CHAINS.get(chain)
        address = self.addresses.get(chain, "")
        if spec is None or not address:
            return []

        try:
            if spec.kind == "tron":
                return self._tron_transfers(spec, address)
            head = self.block_number(chain)
            span = min(lookback_blocks, spec.max_log_span)
            logs = self._rpc(spec, "eth_getLogs", [{
                "fromBlock": hex(max(0, head - span)),
                "toBlock": "latest",
                "address": spec.token,
                "topics": [TRANSFER_TOPIC, None, "0x" + _pad_address(address)],
            }]) or []
        except Exception as e:
            log.info("transfer scan unavailable on %s (%s); using balance deltas", chain, str(e)[:90])
            return []

        out: list[OnChainTransfer] = []
        for entry in logs:
            try:
                topics = entry.get("topics") or []
                amount = int(entry.get("data") or "0x0", 16) / (10**spec.decimals)
                out.append(OnChainTransfer(
                    chain=chain,
                    to_address=address,
                    from_address="0x" + topics[1][-40:] if len(topics) > 1 else "",
                    amount_usdt=amount,
                    tx_hash=entry.get("transactionHash", ""),
                    block=int(entry.get("blockNumber") or "0x0", 16),
                ))
            except (ValueError, TypeError, IndexError):
                continue
        return out

    def _tron_transfers(self, spec: ChainSpec, address: str) -> list[OnChainTransfer]:
        data = http.get_json(
            f"{spec.rpcs[0]}/v1/accounts/{address}/transactions/trc20",
            params={"contract_address": spec.token, "only_to": "true", "limit": 50},
            timeout=self.timeout, retries=2,
        ) or {}
        out: list[OnChainTransfer] = []
        for tx in data.get("data") or []:
            try:
                # TronGrid's contract_address filter is advisory: the response can
                # still carry other TRC20s, including address-poisoning spam that
                # mimics USDT. Verify the contract and take decimals from the
                # token itself — assuming 6 turns an 18-decimal scam token into a
                # billion-dollar "payment".
                info = tx.get("token_info") or {}
                if info.get("address") != spec.token:
                    continue
                decimals = int(info.get("decimals", spec.decimals))
                if tx.get("to") != address:
                    continue  # only_to is not always honoured either
                out.append(OnChainTransfer(
                    chain="tron",
                    to_address=tx.get("to", address),
                    from_address=tx.get("from", ""),
                    amount_usdt=int(tx.get("value") or 0) / (10**decimals),
                    tx_hash=tx.get("transaction_id", ""),
                    block=int(tx.get("block_timestamp") or 0),
                    ts=float(tx.get("block_timestamp") or 0) / 1000.0,
                ))
            except (ValueError, TypeError):
                continue
        return out

    def all_transfers(self, lookback_blocks: int = 2_000) -> list[OnChainTransfer]:
        out: list[OnChainTransfer] = []
        for chain in self.chains():
            out.extend(self.transfers(chain, lookback_blocks))
        out.sort(key=lambda t: (t.ts, t.block), reverse=True)
        return out

    # -- diagnostics -----------------------------------------------------
    def explorer_url(self, chain: str, tx_hash: str) -> str:
        spec = CHAINS.get(chain)
        return f"{spec.explorer}{tx_hash}" if spec and spec.explorer else ""

    def status(self) -> dict[str, Any]:
        balances, errors = self.balances()
        return {
            "addresses": dict(self.addresses),
            "balances": {k: round(v, 6) for k, v in balances.items()},
            "total_usdt": round(sum(balances.values()), 6),
            "errors": errors,
            "chains_configured": len(self.chains()),
        }


def wallet_from_env_or_config(configured: dict[str, str] | None = None) -> Wallet:
    """Build a wallet from config, letting ``USDT_WALLET_<CHAIN>`` override.

    Only addresses are ever read — there is deliberately no code path in this
    project that reads a private key or a seed phrase.
    """
    import os

    addresses = dict(configured or {})
    for chain in CHAINS:
        env = os.environ.get(f"USDT_WALLET_{chain.upper()}", "").strip()
        if env:
            addresses[chain] = env
    return Wallet(addresses={k: v for k, v in addresses.items() if v})
