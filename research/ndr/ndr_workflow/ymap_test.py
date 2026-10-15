import sys; from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parent))
import ndr_workflow as wf
from scrapling.fetchers import StealthySession
st = wf.load_state(); kw = wf.session_kwargs(st)
kw["user_data_dir"] = str(Path(__file__).resolve().parent / "profile_yandex")
kw["additional_args"] = dict(kw["additional_args"], viewport={"width": 1400, "height": 900})
pts = "~".join(["37.6270,55.7970,pm2rdl", "37.6215,55.8020,pm2blm1", "37.6050,55.7930,pm2blm2"])
url = f"https://yandex.ru/maps/?ll=37.6270,55.7970&z=14&l=map&pt={pts}"
out = {}
def act(p):
    p.wait_for_timeout(9000)
    out["url"] = p.url[:120]; out["title"] = p.title()
    p.screenshot(path="/tmp/claude-0/-home-user-test/ea57c60a-b461-5b01-9fed-bf21bf3c9920/scratchpad/report/maps/test.png")
with StealthySession(**kw) as s: s.fetch(url, network_idle=False, page_action=act)
print(out)
