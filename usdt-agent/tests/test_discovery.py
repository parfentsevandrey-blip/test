"""Tests for source discovery: reading a stranger's JSON, and refusing to.

Discovery is the only module that aims the agent at a server nobody here
controls, so most of what is asserted below is a *refusal*. The schema and
mapping tests check that a guessed integration produces real, priced gigs or
none at all; everything after them checks that the guards in front of the wire
cannot be talked out of a "no" — not by a private address wearing an IPv6
costume, not by a redirect, not by a fresh object, and not by a keyword
argument, because none of the public functions has one to offer.

Nothing here touches a socket. ``socket.getaddrinfo`` and the opener that
``_raw_get`` builds are both replaced for every test, so an assertion that a
guard fired *before* the network is an assertion about a mock that was never
called, not a hope about a request that went nowhere.
"""

from __future__ import annotations

import inspect
import io
import ipaddress
import json
import socket
import sys
import time
import unittest
import urllib.error
import urllib.request
from dataclasses import dataclass, field
from email.message import Message
from pathlib import Path
from typing import Any
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from usdt_agent.earn import discovery
from usdt_agent.earn.discovery import (
    MAX_SCHEMA_LEAVES,
    MIN_INTERVAL_S,
    ConfiguredSource,
    DiscoveryRegistry,
    ProbeStatus,
    find_item_array,
    gigs_from_source,
    infer_schema,
    mapping_is_workable,
    normalise_host,
    suggest_gig_mapping,
)
from usdt_agent.earn.models import Gig

#: A routable address the offline resolver hands out for every ``*.test`` name.
PUBLIC_IP = "93.184.216.34"

ROBOTS_ALLOW_ALL = b"User-agent: *\nAllow: /\n"


# --------------------------------------------------------------------------
# The fake wire
# --------------------------------------------------------------------------


@dataclass
class Reply:
    """One canned HTTP response. ``status >= 400`` is raised, as urllib would."""

    body: bytes = b"{}"
    status: int = 200
    content_type: str = "application/json"
    headers: dict[str, str] = field(default_factory=dict)

    def message(self) -> Message:
        msg = Message()
        msg["Content-Type"] = self.content_type
        for key, value in self.headers.items():
            msg[key] = value
        return msg


class _FakeResponse:
    def __init__(self, reply: Reply) -> None:
        self.status = reply.status
        self.headers = reply.message()
        self._buffer = io.BytesIO(reply.body)

    def read(self, size: int = -1) -> bytes:
        return self._buffer.read(size)

    def __enter__(self) -> _FakeResponse:
        return self

    def __exit__(self, *exc: object) -> bool:
        return False


class FakeHttp:
    """Stands in for every byte the module could put on the wire, and counts them.

    A URL with no route is unreachable rather than quietly successful: a test
    that forgets to declare an endpoint should fail, not pass on a default.
    """

    def __init__(self) -> None:
        self.routes: dict[str, Reply] = {}
        self.calls: list[str] = []
        self.request_headers: list[dict[str, str]] = []
        self.openers_built = 0

    def reset(self) -> None:
        """Forget every route and every call, without changing object identity.

        The patched ``build_opener`` is bound to this instance, so a subtest that
        wants a clean wire has to empty this one rather than build another.
        """
        self.routes.clear()
        self.calls.clear()
        self.request_headers.clear()
        self.openers_built = 0

    def route(self, url: str, reply: Reply) -> None:
        self.routes[url] = reply

    def route_json(self, url: str, payload: Any) -> None:
        self.route(url, Reply(body=json.dumps(payload).encode()))

    def allow_robots(self, origin: str) -> None:
        self.route(f"{origin}/robots.txt", Reply(body=ROBOTS_ALLOW_ALL, content_type="text/plain"))

    def count(self, url: str) -> int:
        return self.calls.count(url)

    # -- the urllib seam ---------------------------------------------------

    def build_opener(self, *handlers: object) -> FakeHttp:
        self.openers_built += 1
        return self

    def open(self, request: urllib.request.Request, timeout: float | None = None) -> _FakeResponse:
        url = request.full_url
        self.calls.append(url)
        self.request_headers.append(dict(request.headers))
        reply = self.routes.get(url)
        if reply is None:
            raise urllib.error.URLError(socket.gaierror(-2, "Name or service not known"))
        if reply.status >= 400:
            raise urllib.error.HTTPError(url, reply.status, "denied", reply.message(),
                                         io.BytesIO(reply.body))
        return _FakeResponse(reply)


def offline_resolver(host: str, port: int, *args: object, **kwargs: object) -> list[Any]:
    """A resolver with no resolver behind it.

    Literal addresses answer as themselves — which is what makes the SSRF cases
    honest, since the guard judges exactly the address it was given — and
    ``*.test`` names answer with one public address. Everything else is NXDOMAIN.
    """
    try:
        ip = ipaddress.ip_address(host)
    except ValueError:
        if host.endswith(".test"):
            return [(socket.AF_INET, socket.SOCK_STREAM, 6, "", (PUBLIC_IP, port))]
        raise socket.gaierror(-2, "Name or service not known") from None
    family = socket.AF_INET6 if ip.version == 6 else socket.AF_INET
    return [(family, socket.SOCK_STREAM, 6, "", (str(ip), port))]


class DiscoveryTestCase(unittest.TestCase):
    """Base case: module state reset, wire replaced, clock unhooked.

    The limiter, the blocked-host set and the probe budget deliberately live at
    module scope so no caller can reset them — which means the tests have to,
    or the first blocked host would leak into every case after it.
    """

    def reset_module_state(self) -> None:
        """Forget the run: budget, blocked hosts, cached robots and pacing."""
        discovery._probes_used = 0
        discovery._BLOCKED_HOSTS.clear()
        discovery._ROBOTS.clear()
        discovery._LIMITER = discovery._HostRateLimiter()

    def setUp(self) -> None:
        self.reset_module_state()

        # Every refusal below is logged at WARNING by design. Expected output is
        # not test output, so it is silenced rather than left to bury a failure.
        discovery.log.disabled = True
        self.addCleanup(setattr, discovery.log, "disabled", False)

        self.http = FakeHttp()
        self.sleeps: list[float] = []
        patches = [
            mock.patch.object(discovery.socket, "getaddrinfo", offline_resolver),
            mock.patch.object(urllib.request, "build_opener", self.http.build_opener),
            mock.patch.object(discovery.time, "sleep", self.sleeps.append),
        ]
        for patch in patches:
            patch.start()
            self.addCleanup(patch.stop)

    def assertNothingSent(self) -> None:
        self.assertEqual(self.http.calls, [], "a guard let a request through")
        self.assertEqual(self.http.openers_built, 0, "an opener was built past a guard")


# --------------------------------------------------------------------------
# Schema inference
# --------------------------------------------------------------------------


class TestSchemaInference(DiscoveryTestCase):
    def test_bare_list_of_dicts(self) -> None:
        schema = infer_schema([{"id": 1, "title": "a"}, {"id": 2, "title": "b"}])
        self.assertEqual(schema, {"[].id": "int", "[].title": "str"})

    def test_list_wrapped_under_a_key(self) -> None:
        schema = infer_schema({"results": [{"id": 1}], "total": 1})
        self.assertEqual(schema, {"results[].id": "int", "total": "int"})

    def test_nested_dicts_get_dotted_paths(self) -> None:
        schema = infer_schema({"data": {"reward": {"amount": 12.5, "currency": "USD"}}})
        self.assertEqual(schema, {"data.reward.amount": "float", "data.reward.currency": "str"})

    def test_a_scalar_payload_is_still_described(self) -> None:
        self.assertEqual(infer_schema(42), {"$": "int"})
        self.assertEqual(infer_schema("hello"), {"$": "str"})
        self.assertEqual(infer_schema(None), {"$": "null"})

    def test_empty_containers_are_named_not_guessed(self) -> None:
        self.assertEqual(infer_schema([]), {"[]": "empty"})
        self.assertEqual(infer_schema({}), {"$": "dict"})

    def test_mixed_types_at_one_path_are_unioned(self) -> None:
        self.assertEqual(infer_schema([{"a": 1}, {"a": "x"}])["[].a"], "int|str")

    def test_the_longest_list_of_dicts_wins(self) -> None:
        """The item array is the longest list of objects, whatever it is called."""
        payload = {
            "tags": [{"name": "a"}, {"name": "b"}],
            "featured": [{"title": "f"}],
            "payload": {"records": [{"title": f"r{i}"} for i in range(7)]},
        }
        path, items = find_item_array(payload)
        self.assertEqual(path, "payload.records[]")
        self.assertEqual(len(items), 7)

    def test_a_list_inside_an_item_never_outranks_the_item_list(self) -> None:
        """One issue with many labels must not make a label look like a gig."""
        payload = {"items": [
            {"title": "only issue", "labels": [{"name": str(n)} for n in range(9)]},
        ]}
        path, items = find_item_array(payload)
        self.assertEqual(path, "items[]")
        self.assertEqual(items[0]["title"], "only issue")

    def test_a_hostile_payload_is_bounded_not_walked(self) -> None:
        """Depth, width and leaf count are all ceilings; the payload is a stranger's."""
        deep: Any = {"leaf": 1}
        for _ in range(5000):
            deep = {"nest": deep}
        wide = {f"k{i}": {f"j{j}": j for j in range(200)} for i in range(200)}
        enormous = {"items": [{"a": i, "b": "x" * 64} for i in range(20_000)]}

        started = time.monotonic()
        deep_schema = infer_schema(deep)
        wide_schema = infer_schema(wide)
        enormous_schema = infer_schema(enormous)
        elapsed = time.monotonic() - started

        # The walk stops a handful of levels down and says so, rather than
        # recursing 5000 deep and taking the interpreter's stack with it.
        ((deep_path, deep_kind),) = deep_schema.items()
        self.assertEqual(deep_kind, "...")
        self.assertLessEqual(deep_path.count("nest"), 8)
        self.assertLessEqual(len(wide_schema), MAX_SCHEMA_LEAVES)
        self.assertEqual(enormous_schema, {"items[].a": "int", "items[].b": "str"})
        self.assertLess(elapsed, 5.0, "a bounded walk should not take seconds")

    def test_keys_that_break_the_path_grammar_are_dropped(self) -> None:
        """A dotted key cannot be expressed as a path, so it is omitted, not mangled."""
        self.assertEqual(infer_schema({"a.b": 1, "ok": 2}), {"ok": "int"})


# --------------------------------------------------------------------------
# Mapping onto Gig
# --------------------------------------------------------------------------


ALGORA_SHAPE = {"bounties": [
    {"uuid": "b-1", "name": "Fix the retry loop", "html_url": "https://board.test/b/1",
     "bounty_amount": 500, "expires_at": "2026-09-01T00:00:00Z",
     "labels": [{"id": 3, "name": "backend", "color": "ff0000"}]},
    {"uuid": "b-2", "name": "Write the migration guide", "html_url": "https://board.test/b/2",
     "bounty_amount": 120, "expires_at": "2026-09-02T00:00:00Z", "labels": []},
]}

BOARD_SHAPE = [
    {"id": 7, "title": "Ship the exporter", "link": "https://jobs.test/7", "reward_usd": "$500"},
    {"id": 8, "title": "Unpaid glory", "link": "https://jobs.test/8", "reward_usd": "negotiable"},
]


def mapping_for(payload: Any) -> dict[str, Any]:
    _, items = find_item_array(payload)
    return suggest_gig_mapping(infer_schema(payload), items[0] if items else payload)


class TestGigMapping(unittest.TestCase):
    def test_finds_the_fields_under_one_set_of_names(self) -> None:
        mapping = mapping_for(ALGORA_SHAPE)
        self.assertEqual(mapping["title"], "bounties[].name")
        self.assertEqual(mapping["url"], "bounties[].html_url")
        self.assertEqual(mapping["external_id"], "bounties[].uuid")
        self.assertEqual(mapping["reward_usdt"], "bounties[].bounty_amount")
        self.assertEqual(mapping["deadline_ts"], "bounties[].expires_at")
        self.assertGreaterEqual(mapping["confidence"], 0.5)

    def test_finds_the_same_fields_under_another(self) -> None:
        mapping = mapping_for(BOARD_SHAPE)
        self.assertEqual(mapping["title"], "[].title")
        self.assertEqual(mapping["url"], "[].link")
        self.assertEqual(mapping["external_id"], "[].id")
        self.assertEqual(mapping["reward_usdt"], "[].reward_usd")

    def test_an_avatar_url_does_not_become_the_gig_url(self) -> None:
        payload = [{"title": "t", "avatar_url": "https://cdn.test/a.png", "amount": 90}]
        self.assertNotEqual(mapping_for(payload).get("url"), "[].avatar_url")

    def test_a_created_timestamp_does_not_become_a_deadline(self) -> None:
        payload = [{"title": "t", "amount": 90, "created_at": "2026-01-01T00:00:00Z"}]
        self.assertNotIn("deadline_ts", mapping_for(payload))

    def test_a_mapping_without_a_reward_is_not_workable(self) -> None:
        self.assertTrue(mapping_is_workable({"title": "a", "reward_usdt": "b"}))
        self.assertFalse(mapping_is_workable({"title": "a", "url": "b"}))
        self.assertFalse(mapping_is_workable({"title": "", "reward_usdt": "b"}))

    def test_gigs_carry_the_reward_the_source_published(self) -> None:
        gigs = gigs_from_source(ALGORA_SHAPE, mapping_for(ALGORA_SHAPE), source="algora")
        self.assertEqual(len(gigs), 2)
        self.assertTrue(all(isinstance(g, Gig) for g in gigs))
        self.assertAlmostEqual(gigs[0].reward_usdt, 500.0)
        self.assertEqual(gigs[0].title, "Fix the retry loop")
        self.assertEqual(gigs[0].external_id, "b-1")
        self.assertEqual(gigs[0].url, "https://board.test/b/1")
        self.assertEqual(gigs[0].tags, ("backend",))
        self.assertGreater(gigs[0].deadline_ts, 0.0)

    def test_money_written_as_a_string_parses(self) -> None:
        gigs = gigs_from_source(BOARD_SHAPE, mapping_for(BOARD_SHAPE))
        self.assertEqual([g.title for g in gigs], ["Ship the exporter"])
        self.assertAlmostEqual(gigs[0].reward_usdt, 500.0)

    def test_a_bare_numeric_string_is_arithmetic_not_a_guess(self) -> None:
        payload = [{"title": "t", "link": "https://x.test/1", "amount": "1,250.00"}]
        gigs = gigs_from_source(payload, mapping_for(payload))
        self.assertAlmostEqual(gigs[0].reward_usdt, 1250.0)

    def test_unparseable_money_drops_the_gig_rather_than_pricing_it(self) -> None:
        payload = [
            {"title": "real", "amount": "$40"},
            {"title": "vague", "amount": "competitive"},
            {"title": "equity", "amount": "DOE"},
            {"title": "empty", "amount": ""},
            {"title": "structured", "amount": {"value": 100}},
        ]
        gigs = gigs_from_source(payload, {"title": "[].title", "reward_usdt": "[].amount"})
        self.assertEqual([g.title for g in gigs], ["real"])

    def test_a_money_field_that_will_not_parse_is_not_even_proposed(self) -> None:
        """A path whose sample reads 'competitive' is not a reward path."""
        payload = [{"title": "vague", "link": "https://jobs.test/1", "amount": "competitive"}]
        mapping = mapping_for(payload)
        self.assertNotIn("reward_usdt", mapping)
        self.assertFalse(mapping_is_workable(mapping))
        self.assertEqual(gigs_from_source(payload, mapping), [])

    def test_zero_and_negative_rewards_are_not_gigs(self) -> None:
        payload = [{"title": "free", "amount": 0}, {"title": "owed", "amount": -5},
                   {"title": "paid", "amount": 9}]
        mapping = {"title": "[].title", "reward_usdt": "[].amount"}
        self.assertEqual([g.title for g in gigs_from_source(payload, mapping)], ["paid"])

    def test_a_titleless_item_is_dropped(self) -> None:
        payload = [{"title": "", "amount": 100}, {"title": "kept", "amount": 100}]
        mapping = {"title": "[].title", "reward_usdt": "[].amount"}
        self.assertEqual([g.title for g in gigs_from_source(payload, mapping)], ["kept"])

    def test_no_reward_path_yields_nothing_at_all(self) -> None:
        payload = [{"title": "a"}, {"title": "b"}]
        self.assertEqual(gigs_from_source(payload, {"title": "[].title", "confidence": 0.9}), [])

    def test_gigs_are_labelled_unverified_and_conservatively_priced(self) -> None:
        gig = gigs_from_source(ALGORA_SHAPE, mapping_for(ALGORA_SHAPE))[0]
        self.assertTrue(gig.meta["source_is_unverified"])
        self.assertTrue(gig.meta["effort_is_a_placeholder"])
        self.assertLessEqual(gig.payout_probability, 0.2)

    def test_a_non_http_url_is_dropped_from_the_gig(self) -> None:
        payload = [{"title": "t", "link": "javascript:alert(1)", "amount": 90}]
        self.assertEqual(gigs_from_source(payload, mapping_for(payload))[0].url, "")

    def test_a_configured_source_reparses_without_io(self) -> None:
        source = ConfiguredSource(name="algora", url="https://board.test/api",
                                  mapping=mapping_for(ALGORA_SHAPE))
        self.assertFalse(source.verified)
        self.assertEqual(len(source.gigs(ALGORA_SHAPE)), 2)
        self.assertEqual(source.to_dict()["verified"], False)


# --------------------------------------------------------------------------
# Guards that fire before anything leaves the process
# --------------------------------------------------------------------------


class TestStaticRefusals(DiscoveryTestCase):
    def probe(self, url: str) -> discovery.SourceProbe:
        return DiscoveryRegistry().probe(url)

    def test_userinfo_is_refused(self) -> None:
        probe = self.probe("http://user:pass@board.test/api")
        self.assertIs(probe.status, ProbeStatus.REFUSED)
        self.assertTrue(any("userinfo" in note for note in probe.notes))
        self.assertNothingSent()

    def test_non_http_schemes_are_refused(self) -> None:
        for url in ("file:///etc/passwd", "ftp://board.test/pub", "gopher://board.test/1"):
            with self.subTest(url=url):
                probe = self.probe(url)
                self.assertIs(probe.status, ProbeStatus.REFUSED)
                self.assertTrue(any("not http or https" in note for note in probe.notes))
        self.assertNothingSent()

    def test_credential_query_parameters_are_refused(self) -> None:
        for url in ("https://board.test/api?api_key=abc",
                    "https://board.test/api?access_token=abc",
                    "https://board.test/api?signature=abc"):
            with self.subTest(url=url):
                self.assertIs(self.probe(url).status, ProbeStatus.REFUSED)
        self.assertNothingSent()

    def test_a_malformed_url_degrades_instead_of_raising(self) -> None:
        for url in ("", "https://", "http://board.test:notaport/x", "nonsense"):
            with self.subTest(url=url):
                self.assertIs(self.probe(url).status, ProbeStatus.REFUSED)
        self.assertNothingSent()

    def test_host_normalisation_folds_one_server_into_one_bucket(self) -> None:
        for variant in ("API.example.com", "api.example.com.", "API.example.com.:443",
                        "www.api.example.com", "api.example.com:8080"):
            with self.subTest(host=variant):
                self.assertEqual(normalise_host(variant), "api.example.com")
        # IP literals canonicalise, so one address cannot wear three identities.
        self.assertEqual(normalise_host("[::1]:8080"), "::1")
        self.assertEqual(normalise_host("::ffff:127.0.0.1"), "127.0.0.1")
        self.assertEqual(normalise_host("2001:DB8::1"), "2001:db8::1")
        # A unicode host and its punycode spelling are one bucket, not two.
        self.assertEqual(normalise_host("ex\u00e4mple.com"), normalise_host("xn--exmple-cua.com"))
        # A bucket key must never carry credentials, however the caller writes them.
        self.assertEqual(normalise_host("user:pass@api.example.com"), "api.example.com")


class TestSsrfGuard(DiscoveryTestCase):
    PRIVATE_URLS = (
        "http://127.0.0.1/",
        "http://10.0.0.1/",
        "http://192.168.1.1/",
        "http://[::1]/",
        "http://169.254.169.254/latest/meta-data/iam/security-credentials/",
        "http://[::ffff:127.0.0.1]/",          # IPv4 loopback in an IPv6 costume
        "http://[::ffff:169.254.169.254]/",    # and the metadata service in one
    )

    def test_private_and_link_local_targets_are_refused_before_the_wire(self) -> None:
        registry = DiscoveryRegistry()
        for url in self.PRIVATE_URLS:
            with self.subTest(url=url):
                probe = registry.probe(url)
                self.assertIs(probe.status, ProbeStatus.REFUSED)
                self.assertTrue(any("SSRF guard" in note for note in probe.notes), probe.notes)
        self.assertNothingSent()

    def test_a_name_resolving_to_a_private_address_is_refused(self) -> None:
        """The address decides, not the name — a public-looking host can point inward."""
        def inward(host: str, port: int, *a: object, **kw: object) -> list[Any]:
            return [(socket.AF_INET, socket.SOCK_STREAM, 6, "", ("127.0.0.1", port))]

        with mock.patch.object(discovery.socket, "getaddrinfo", inward):
            probe = DiscoveryRegistry().probe("https://board.test/api")
        self.assertIs(probe.status, ProbeStatus.REFUSED)
        self.assertNothingSent()

    def test_every_resolved_address_is_judged_not_just_the_first(self) -> None:
        """One public answer plus one loopback answer is still a refusal."""
        def split_horizon(host: str, port: int, *a: object, **kw: object) -> list[Any]:
            return [
                (socket.AF_INET, socket.SOCK_STREAM, 6, "", (PUBLIC_IP, port)),
                (socket.AF_INET, socket.SOCK_STREAM, 6, "", ("127.0.0.1", port)),
            ]

        with mock.patch.object(discovery.socket, "getaddrinfo", split_horizon):
            probe = DiscoveryRegistry().probe("https://board.test/api")
        self.assertIs(probe.status, ProbeStatus.REFUSED)
        self.assertNothingSent()

    def test_an_unresolvable_host_is_refused_quietly(self) -> None:
        probe = DiscoveryRegistry().probe("https://nope.invalid/api")
        self.assertIs(probe.status, ProbeStatus.REFUSED)
        self.assertNothingSent()


# --------------------------------------------------------------------------
# robots.txt
# --------------------------------------------------------------------------


class TestRobots(DiscoveryTestCase):
    def test_a_disallowed_path_is_never_fetched(self) -> None:
        self.http.route("https://board.test/robots.txt",
                        Reply(body=b"User-agent: *\nDisallow: /private\n", content_type="text/plain"))
        self.http.route_json("https://board.test/private/feed", {"items": []})

        probe = DiscoveryRegistry().probe("https://board.test/private/feed")

        self.assertIs(probe.status, ProbeStatus.DISALLOWED)
        self.assertFalse(probe.robots_allowed)
        self.assertEqual(self.http.count("https://board.test/private/feed"), 0)
        self.assertEqual(self.http.calls, ["https://board.test/robots.txt"])

    def test_a_disallow_aimed_at_this_agent_by_name_is_obeyed(self) -> None:
        """The whole point of a truthful UA: a site can name this agent and be obeyed.

        The nameable token is the product token — everything before the ``/`` —
        which is what ``robotparser`` matches on, so a site writes ``usdt-agent``
        rather than the full version-and-URL string the agent actually sends.
        """
        token = discovery.USER_AGENT.split("/")[0]
        self.assertEqual(token, "usdt-agent")
        rule = f"User-agent: {token}\nDisallow: /\n\nUser-agent: *\nAllow: /\n".encode()
        self.http.route("https://board.test/robots.txt", Reply(body=rule, content_type="text/plain"))
        self.http.route_json("https://board.test/api", ALGORA_SHAPE)

        probe = DiscoveryRegistry().probe("https://board.test/api")

        self.assertIs(probe.status, ProbeStatus.DISALLOWED)
        self.assertEqual(self.http.count("https://board.test/api"), 0)

    def test_an_access_controlled_robots_closes_the_whole_host(self) -> None:
        """RFC 9309: if robots.txt itself is behind a 403, nothing on the host is public."""
        self.http.route("https://board.test/robots.txt", Reply(status=403, body=b"no"))
        self.http.route_json("https://board.test/api", {"items": []})

        probe = DiscoveryRegistry().probe("https://board.test/api")

        self.assertIs(probe.status, ProbeStatus.DISALLOWED)
        self.assertEqual(self.http.count("https://board.test/api"), 0)

    def test_an_allowed_path_is_fetched_and_parsed(self) -> None:
        self.http.allow_robots("https://board.test")
        self.http.route_json("https://board.test/api", ALGORA_SHAPE)

        probe = DiscoveryRegistry().probe("https://board.test/api")

        self.assertIs(probe.status, ProbeStatus.OK)
        self.assertTrue(probe.robots_allowed)
        self.assertEqual(probe.item_count, 2)
        self.assertTrue(probe.usable)
        self.assertTrue(probe.probed)

    def test_crawl_delay_raises_the_interval_for_that_host(self) -> None:
        self.http.route("https://board.test/robots.txt",
                        Reply(body=b"User-agent: *\nCrawl-delay: 30\n", content_type="text/plain"))
        self.http.route_json("https://board.test/api", ALGORA_SHAPE)

        DiscoveryRegistry().probe("https://board.test/api")

        self.assertTrue(self.sleeps, "the second request to a host must be paced")
        self.assertGreaterEqual(max(self.sleeps), 25.0)


# --------------------------------------------------------------------------
# "No" is terminal
# --------------------------------------------------------------------------


class TestBlocking(DiscoveryTestCase):
    def setUp(self) -> None:
        super().setUp()
        self.http.allow_robots("https://board.test")

    def test_a_403_blocks_and_is_never_retried(self) -> None:
        self.http.route("https://board.test/api", Reply(status=403, body=b"Forbidden"))

        probe = DiscoveryRegistry().probe("https://board.test/api")

        self.assertIs(probe.status, ProbeStatus.BLOCKED)
        self.assertEqual(self.http.count("https://board.test/api"), 1, "the module retried a refusal")
        self.assertIn("board.test", discovery.blocked_hosts())

    def test_a_401_blocks_too(self) -> None:
        self.http.route("https://board.test/api", Reply(status=401, body=b"Unauthorized"))
        self.assertIs(DiscoveryRegistry().probe("https://board.test/api").status, ProbeStatus.BLOCKED)
        self.assertEqual(self.http.count("https://board.test/api"), 1)

    def test_a_429_blocks_rather_than_backing_off(self) -> None:
        self.http.route("https://board.test/api", Reply(status=429, body=b"slow down"))
        self.assertIs(DiscoveryRegistry().probe("https://board.test/api").status, ProbeStatus.BLOCKED)
        self.assertEqual(self.http.count("https://board.test/api"), 1)

    def test_an_anti_bot_body_blocks_without_a_second_attempt(self) -> None:
        for marker in (b"<html>Please complete the CAPTCHA to continue</html>",
                       b"<html>Checking your browser before accessing</html>",
                       b"<html>Attention Required! | Cloudflare</html>"):
            with self.subTest(marker=marker[:30]):
                # A blocked host is remembered for the run, so each marker needs
                # a clean one — otherwise only the first assertion means anything.
                self.reset_module_state()
                self.http.reset()
                self.http.allow_robots("https://board.test")
                self.http.route("https://board.test/api",
                                Reply(body=marker, content_type="text/html"))

                probe = DiscoveryRegistry().probe("https://board.test/api")

                self.assertIs(probe.status, ProbeStatus.BLOCKED)
                self.assertEqual(self.http.count("https://board.test/api"), 1)
                self.assertTrue(any("challenge marker" in n for n in probe.notes), probe.notes)

    def test_an_anti_bot_header_blocks(self) -> None:
        self.http.route("https://board.test/api",
                        Reply(body=b'{"items":[]}', headers={"cf-mitigated": "challenge"}))
        self.assertIs(DiscoveryRegistry().probe("https://board.test/api").status, ProbeStatus.BLOCKED)

    def test_a_bounty_about_captchas_is_not_a_captcha(self) -> None:
        """Body markers are only read when the response is not successful JSON."""
        payload = {"items": [{"id": 1, "title": "Fix the CAPTCHA widget", "reward": 200,
                              "html_url": "https://board.test/1"}]}
        self.http.route_json("https://board.test/api", payload)
        probe = DiscoveryRegistry().probe("https://board.test/api")
        self.assertIs(probe.status, ProbeStatus.OK)

    def test_a_blocked_host_stays_blocked_for_a_brand_new_registry(self) -> None:
        self.http.route("https://board.test/api", Reply(status=403, body=b"no"))
        self.http.route_json("https://board.test/other", ALGORA_SHAPE)

        self.assertIs(DiscoveryRegistry().probe("https://board.test/api").status, ProbeStatus.BLOCKED)
        sent = len(self.http.calls)

        probe = DiscoveryRegistry().probe("https://board.test/other")

        self.assertIs(probe.status, ProbeStatus.REFUSED)
        self.assertEqual(len(self.http.calls), sent, "a fresh registry re-probed a blocked host")

    def test_probe_all_stops_the_run_at_the_first_block(self) -> None:
        self.http.route("https://board.test/api", Reply(status=403, body=b"no"))
        self.http.allow_robots("https://other.test")
        self.http.route_json("https://other.test/api", ALGORA_SHAPE)

        results = DiscoveryRegistry().probe_all(["https://board.test/api", "https://other.test/api"])

        self.assertEqual(len(results), 1)
        self.assertEqual(self.http.count("https://other.test/api"), 0)


# --------------------------------------------------------------------------
# Pacing
# --------------------------------------------------------------------------


class TestRateLimiting(DiscoveryTestCase):
    def test_the_limiter_spaces_requests_to_one_host(self) -> None:
        limiter = discovery._HostRateLimiter(interval=2.0)
        self.assertEqual(limiter.acquire("board.test"), 0.0)      # the first is free
        waited = limiter.acquire("board.test")
        self.assertGreater(waited, 1.0)
        self.assertLessEqual(waited, 2.0)

    def test_each_host_has_its_own_allowance(self) -> None:
        limiter = discovery._HostRateLimiter(interval=2.0)
        self.assertEqual(limiter.acquire("board.test"), 0.0)
        self.assertEqual(limiter.acquire("other.test"), 0.0)

    def test_spelling_a_host_differently_does_not_buy_a_second_allowance(self) -> None:
        limiter = discovery._HostRateLimiter(interval=2.0)
        self.assertEqual(limiter.acquire("board.test"), 0.0)
        self.assertGreater(limiter.acquire("WWW.board.test.:443"), 1.0)

    def test_an_absurd_wait_is_refused_rather_than_slept(self) -> None:
        limiter = discovery._HostRateLimiter(interval=86400.0)
        self.assertEqual(limiter.acquire("board.test"), 0.0)
        self.assertEqual(limiter.acquire("board.test"), -1.0)
        # Nothing is consumed by a refusal, so the next caller is not punished twice.
        self.assertAlmostEqual(limiter.acquire("board.test", max_wait=90000.0), 86400.0, delta=1.0)

    def test_a_fresh_registry_does_not_reset_the_interval(self) -> None:
        """The limiter lives at module scope precisely so ``DiscoveryRegistry()`` cannot reset it."""
        self.http.allow_robots("https://board.test")
        self.http.route_json("https://board.test/one", ALGORA_SHAPE)
        self.http.route_json("https://board.test/two", ALGORA_SHAPE)

        DiscoveryRegistry().probe("https://board.test/one")
        after_first = len(self.sleeps)
        self.assertGreaterEqual(after_first, 1, "robots.txt and the target share one host bucket")

        DiscoveryRegistry().probe("https://board.test/two")   # a brand new object

        self.assertGreater(len(self.sleeps), after_first, "a fresh registry skipped the rate limit")
        self.assertGreaterEqual(self.sleeps[-1], MIN_INTERVAL_S * 0.5)


# --------------------------------------------------------------------------
# Failures degrade
# --------------------------------------------------------------------------


class TestDegradation(DiscoveryTestCase):
    def setUp(self) -> None:
        super().setUp()
        self.http.allow_robots("https://board.test")

    def test_a_non_json_body_is_reported_not_raised(self) -> None:
        self.http.route("https://board.test/api",
                        Reply(body=b"<html><body>a listing page</body></html>",
                              content_type="text/html"))
        probe = DiscoveryRegistry().probe("https://board.test/api")
        self.assertIs(probe.status, ProbeStatus.NOT_JSON)
        self.assertFalse(probe.usable)

    def test_json_served_under_the_wrong_content_type_is_still_read(self) -> None:
        self.http.route("https://board.test/api",
                        Reply(body=json.dumps(ALGORA_SHAPE).encode(), content_type="text/plain"))
        probe = DiscoveryRegistry().probe("https://board.test/api")
        self.assertIs(probe.status, ProbeStatus.OK)
        self.assertEqual(probe.item_count, 2)

    def test_an_unreachable_host_is_reported_not_raised(self) -> None:
        probe = DiscoveryRegistry().probe("https://board.test/missing")
        self.assertIs(probe.status, ProbeStatus.UNREACHABLE)
        self.assertFalse(probe.usable)

    def test_an_unexpected_status_is_unreachable_not_a_crash(self) -> None:
        self.http.route("https://board.test/api", Reply(status=500, body=b"boom"))
        self.assertIs(DiscoveryRegistry().probe("https://board.test/api").status,
                      ProbeStatus.UNREACHABLE)

    def test_a_bug_in_inference_cannot_kill_the_caller(self) -> None:
        self.http.route_json("https://board.test/api", ALGORA_SHAPE)
        with mock.patch.object(discovery, "infer_schema", side_effect=RuntimeError("boom")):
            probe = DiscoveryRegistry().probe("https://board.test/api")
        self.assertIs(probe.status, ProbeStatus.UNREACHABLE)
        self.assertTrue(probe.probed)

    def test_an_oversized_body_is_capped_not_parsed(self) -> None:
        blob = b'{"items":[' + b'{"a":1},' * 400_000 + b'{"a":1}]}'
        self.assertGreater(len(blob), discovery.MAX_BODY_BYTES)
        self.http.route("https://board.test/api", Reply(body=blob))
        probe = DiscoveryRegistry().probe("https://board.test/api")
        self.assertIs(probe.status, ProbeStatus.NOT_JSON)
        self.assertTrue(any("cap" in note for note in probe.notes), probe.notes)

    def test_an_unprobed_candidate_never_looks_like_a_working_source(self) -> None:
        known = DiscoveryRegistry().known()
        self.assertTrue(known)
        self.assertTrue(all(not p.probed and not p.usable for p in known))
        self.assertEqual(self.http.calls, [], "building the candidate table fetched something")

    def test_the_summary_counts_probes_and_never_money(self) -> None:
        self.http.route_json("https://board.test/api", ALGORA_SHAPE)
        registry = DiscoveryRegistry()
        registry.probe("https://board.test/api")
        summary = registry.summary()
        self.assertEqual(summary["by_status"], {"ok": 1})
        self.assertEqual(summary["usable"], 1)
        self.assertEqual(summary["probed"], 1)
        # "usable" counts probes worth showing a human, never money. Discovery
        # produces candidates; only the collector may say anything was earned.
        for key in summary:
            self.assertFalse(any(word in key for word in ("usdt", "earn", "income", "treasury")))

    def test_a_probe_is_cached_rather_than_repeated(self) -> None:
        self.http.route_json("https://board.test/api", ALGORA_SHAPE)
        registry = DiscoveryRegistry()
        first = registry.probe("https://board.test/api")
        second = registry.probe("https://board.test/api")
        self.assertIs(first, second)
        self.assertEqual(self.http.count("https://board.test/api"), 1)

    def test_the_probe_budget_binds_and_has_no_reset(self) -> None:
        self.assertFalse(hasattr(discovery, "reset_budget"))
        discovery._probes_used = discovery.MAX_PROBES_PER_RUN
        self.assertEqual(discovery.remaining_probe_budget(), 0)
        probe = DiscoveryRegistry().probe("https://board.test/api")
        self.assertIs(probe.status, ProbeStatus.REFUSED)
        self.assertNothingSent()


# --------------------------------------------------------------------------
# There is no seam for a credential
# --------------------------------------------------------------------------

#: Substrings that would betray a way to authenticate. If one of these ever
#: appears in a public parameter name, the module has grown the seam its own
#: docstring says it does not have.
FORBIDDEN_PARAM_MARKERS = (
    "cookie", "header", "auth", "token", "secret", "password", "passwd",
    "credential", "session", "bearer", "apikey", "api_key", "signature",
)


def public_callables() -> list[tuple[str, Any]]:
    """Every function a caller can reach, including constructors and classmethods."""
    found: list[tuple[str, Any]] = []
    for name, obj in vars(discovery).items():
        if name.startswith("_") or getattr(obj, "__module__", None) != discovery.__name__:
            continue
        if inspect.isfunction(obj):
            found.append((name, obj))
        elif inspect.isclass(obj):
            for attr, member in inspect.getmembers(obj, callable):
                if attr.startswith("_") and attr != "__init__":
                    continue
                if getattr(member, "__module__", None) != discovery.__name__:
                    continue
                found.append((f"{name}.{attr}", member))
    return found


class TestNoCredentialSurface(DiscoveryTestCase):
    def test_the_public_surface_is_worth_inspecting(self) -> None:
        names = {name for name, _ in public_callables()}
        for expected in ("infer_schema", "suggest_gig_mapping", "gigs_from_source",
                         "DiscoveryRegistry.probe", "DiscoveryRegistry.__init__",
                         "ConfiguredSource.from_probe", "ConfiguredSource.__init__"):
            self.assertIn(expected, names)

    def test_no_public_parameter_accepts_a_credential_or_a_header(self) -> None:
        for name, func in public_callables():
            for param in inspect.signature(func).parameters:
                with self.subTest(callable=name, param=param):
                    lowered = param.lower()
                    for marker in FORBIDDEN_PARAM_MARKERS:
                        self.assertNotIn(marker, lowered,
                                         f"{name}({param}=...) is a way to authenticate")

    def test_no_public_parameter_takes_an_arbitrary_mapping_of_strings(self) -> None:
        """``**kwargs`` would smuggle back everything the named parameters refuse."""
        for name, func in public_callables():
            kinds = [p.kind for p in inspect.signature(func).parameters.values()]
            with self.subTest(callable=name):
                self.assertNotIn(inspect.Parameter.VAR_KEYWORD, kinds)

    def test_what_actually_goes_on_the_wire_carries_no_credential(self) -> None:
        self.http.allow_robots("https://board.test")
        self.http.route_json("https://board.test/api", ALGORA_SHAPE)

        DiscoveryRegistry().probe("https://board.test/api")

        self.assertTrue(self.http.request_headers)
        for sent in self.http.request_headers:
            lowered = {key.lower(): value for key, value in sent.items()}
            self.assertEqual(set(lowered), {"user-agent", "accept", "accept-encoding"})
            # Truthful and identifying, so a site can name this agent in robots.txt.
            self.assertIn("usdt-agent", lowered["user-agent"])
            self.assertNotIn("Mozilla", lowered["user-agent"])
            # Uncompressed on purpose: the byte cap is only honest uncompressed.
            self.assertEqual(lowered["accept-encoding"], "identity")

    def test_a_configured_source_cannot_carry_one_either(self) -> None:
        fields = inspect.signature(ConfiguredSource.__init__).parameters
        for name in fields:
            self.assertFalse(any(marker in name.lower() for marker in FORBIDDEN_PARAM_MARKERS))
        # ...and a source pointed at an authenticated URL says so before it is used.
        source = ConfiguredSource(name="x", url="https://board.test/api?token=abc")
        self.assertIn("credential", source.refusal)


if __name__ == "__main__":
    unittest.main(verbosity=2)
