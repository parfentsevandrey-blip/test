import pypdfium2 as p, json, re, subprocess, sys
S="/tmp/claude-0/-home-user-test/46229b36-afd1-51c4-9c08-cd0102cd677b/scratchpad"
heads=json.loads(subprocess.check_output(["node","-e","""
const L=require('%s/doclib.js');
['part1','part2','part3','part4'].forEach(f=>require('%s/'+f+'.js'));
console.log(JSON.stringify(L.headings));"""%(S,S)]).decode())
d=p.PdfDocument(S+"/report.pdf")
pages=[re.sub(r"\s+"," ",d[i].get_textpage().get_text_range()) for i in range(len(d))]
toc={i for i,t in enumerate(pages) if "Содержание" in t}
if toc:  # страницы оглавления идут подряд от первой
    st=min(toc); i=st
    while i+1<len(pages) and sum(1 for h in heads if h["t"] in pages[i+1])>=4: i+=1
    toc=set(range(st,i+1))
cur=(max(toc)+1) if toc else 0
res={}; miss=[]
for h in heads:                       # заголовки идут в порядке документа
    t=re.sub(r"\s+"," ",h["t"]).strip()
    found=next((i for i in range(cur,len(pages)) if t in pages[i]), None)
    if found is None: miss.append(h["t"])
    else: res[h["t"]]=found+1; cur=found
json.dump(res,open(S+"/toc_pages.json","w"),ensure_ascii=False,indent=0)
print("оглавление на стр.",sorted(x+1 for x in toc),"| найдено",len(res),"/",len(heads))
if miss: print("НЕ НАЙДЕНЫ:",miss); sys.exit(1)
