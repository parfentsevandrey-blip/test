"""Lab experiments go to the network only through the pipeline's client policy (research/ndr/lots/lib/clientpolicy.py, L37):
    from guard import check, shield;  check(url, "headless");  shield(context)
CloakBrowser / Camoufox / Crawlee browsers count as «headless»: protected sites (ЦИАН, наш.дом.рф, lsr.ru) refuse them."""
import importlib.util
from pathlib import Path

_SCR = Path("/tmp/claude-0/-home-user-test/ea57c60a-b461-5b01-9fed-bf21bf3c9920/scratchpad/lots/lib/clientpolicy.py")
_REPO = Path(__file__).resolve().parents[1] / "ndr" / "lots" / "lib" / "clientpolicy.py"
_spec = importlib.util.spec_from_file_location("clientpolicy", _SCR if _SCR.exists() else _REPO)
_cp = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_cp)
check, shield, Refused = _cp.check, _cp.shield, _cp.Refused
