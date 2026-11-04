"""Reproduce the one reviewed normal-furnace recipe from its offline Wiki snapshot."""
import argparse
import hashlib
import json
import re
from pathlib import Path
from html.parser import HTMLParser

ROOT = Path(__file__).resolve().parents[1]
PIN = ROOT / "tools/fixtures/bronze_production.json"
OUTPUT = ROOT / "src/main/resources/production_recipes.json"

class Rows(HTMLParser):
    def __init__(self):
        super().__init__()
        self.rows, self.row, self.cell = [], None, None

    def handle_starttag(self, tag, attrs):
        if tag == "tr":
            self.row = []
        elif tag in ("th","td") and self.row is not None:
            self.cell = []

    def handle_data(self, data):
        if self.cell is not None:
            self.cell.append(data)

    def handle_endtag(self, tag):
        if tag in ("th","td") and self.cell is not None:
            self.row.append(" ".join(" ".join(self.cell).split()))
            self.cell = None
        if tag == "tr" and self.row is not None:
            self.rows.append(self.row)
            self.row = None

def build(pin):
    raw, rendered = pin["wikitext"], pin["standardRenderedHtml"]
    if pin["revisionId"] != 15315107 or hashlib.sha256(raw.encode()).hexdigest() != "722c1841621c30fc066dc6c66c354e5e0734761bb683e2b36046b6063392174e":
        raise ValueError("Wrong Bronze bar article revision or content")
    if hashlib.sha256(rendered.encode()).hexdigest() != pin["standardHtmlSha256"]:
        raise ValueError("Changed saved rendering")
    standard = raw.split("Standard=", 1)[1].split("|-|", 1)[0]
    params = dict(re.findall(r"^\|(\w+)\s*=\s*(.+?)\s*$", standard, re.M))
    if [params[k] for k in ("skill1","skill1lvl","members","facilities","output1subtxt")] != ["Smithing","1","No","Furnace","Normal furnace"]:
        raise ValueError("Unreviewed recipe branch")
    parsed = Rows()
    parsed.feed(rendered)
    if ["Tools","None","Facilities","Furnace"] not in parsed.rows:
        raise ValueError("Tool/facility facts missing")
    def exact_amount(name):
        amounts = [r[2] for r in parsed.rows if len(r) == 4 and r[1] == name and r[2].isdigit()]
        if amounts != ["1"]:
            raise ValueError("Ambiguous or changed rendered quantity for " + name)
        return {"itemName":name,"quantity":int(amounts[0])}
    return {"schemaVersion":1,"recipes":[{
        "id":"production:bronze-bar:normal-furnace", "output":exact_amount(params["output1"]),
        "inputs":[exact_amount(params[k]) for k in ("mat1","mat2")], "action":"Smelt",
        "facility":params["output1subtxt"], "skill":params["skill1"],"level":int(params["skill1lvl"]),
        "tools":[],"membersOnly":False,"locationCoverage":"UNREVIEWED",
        "evidence":[{"page":"Bronze bar","revisionId":pin["revisionId"],"sourceUrl":"https://oldschool.runescape.wiki/w/Bronze_bar?oldid=15315107"}]}]}

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    data = build(json.loads(PIN.read_text(encoding="utf-8")))
    result = json.dumps(data,indent=2,ensure_ascii=False)+"\n"
    if args.check:
        if OUTPUT.read_text(encoding="utf-8") != result:
            raise SystemExit("Production resource differs from its saved evidence")
    else:
        OUTPUT.write_text(result,encoding="utf-8")
