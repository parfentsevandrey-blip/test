"""Extract ЦИАН page state: window._cianConfig['<project>'] = (...).concat([...]) -> {key: value}."""
import json
import re

_DEC = json.JSONDecoder()


def configs(html):
    out = {}
    for m in re.finditer(r"_cianConfig\['([\w\-]+)'\]\s*=\s*\(.*?\)\.concat\(", html):
        try:
            arr, _ = _DEC.raw_decode(html, m.end())
        except ValueError:
            continue
        out.setdefault(m.group(1), {}).update({x["key"]: x["value"] for x in arr if isinstance(x, dict) and "key" in x})
    return out


def initial_state(html, project=None):
    cfg = configs(html)
    for p, kv in cfg.items():
        if (project is None or p == project) and "initialState" in kv:
            return kv["initialState"]
    return None
