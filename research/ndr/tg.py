import re,html,sys
t=open(sys.argv[1],'rb').read().decode('utf-8','ignore')
blocks=re.split(r'<div class="tgme_widget_message_wrap',t)[1:]
flt=sys.argv[2] if len(sys.argv)>2 else None
for b in blocks:
    post=re.search(r'data-post="([^"]+)"',b); d=re.search(r'<time datetime="([^"]+)"',b)
    m=re.search(r'<div class="tgme_widget_message_text[^>]*>(.*?)</div>',b,flags=re.S)
    if not m: continue
    s=re.sub(r'<br/?>','\n',m.group(1)); s=re.sub(r'<[^>]+>','',s); s=html.unescape(s)
    if flt and flt not in (post.group(1) if post else ''): continue
    print('---',post.group(1) if post else '',d.group(1) if d else ''); print(s[:int(sys.argv[3]) if len(sys.argv)>3 else 1500])
