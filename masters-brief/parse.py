import xml.etree.ElementTree as ET, json, sys
NS={'ss':'urn:schemas-microsoft-com:office:spreadsheet'}
p='/root/.claude/uploads/f10b7ab2-1385-5931-8285-d55741d60955/da6caa93-cian_______________________20260728_169_______________.xls'
t=ET.parse(p); r=t.getroot()
out={}
for ws in r.findall('ss:Worksheet',NS):
    name=ws.get('{urn:schemas-microsoft-com:office:spreadsheet}Name')
    rows=[]
    tab=ws.find('ss:Table',NS)
    if tab is None: continue
    for row in tab.findall('ss:Row',NS):
        cells=[];idx=0
        for c in row.findall('ss:Cell',NS):
            ix=c.get('{urn:schemas-microsoft-com:office:spreadsheet}Index')
            if ix:
                ix=int(ix)-1
                while idx<ix: cells.append(''); idx+=1
            dt=c.find('ss:Data',NS)
            v='' if dt is None else ''.join(dt.itertext())
            cells.append(v); idx+=1
        rows.append(cells)
    out[name]=rows
json.dump(out,open('data.json','w'),ensure_ascii=False)
for k,v in out.items(): print(k, len(v))
