"""Оформление перечня ОКН: экран и печать A4."""

CSS = """
:root{
  --ground:#f2f0e9; --paper:#fffdf8; --ink:#1d222a; --ink-2:#565e6a; --ink-3:#8a929e;
  --hair:#e1ddd2; --hair-2:#edeade; --zebra:#faf8f2; --head:#232a34;
  --brick:#8a3a2d; --brick-2:#a44f3d; --gold:#b08d3c; --sand:#f6f2e6;
  --go:#186f43; --wait:#a06a25; --link:#1f5c99;
  --chip:#eae5d8; --chip-ink:#67604f; --yes:#186f43; --no:#9a5b52;
  --shadow:0 1px 2px rgba(29,34,42,.05),0 10px 32px rgba(29,34,42,.07);
}
@media (prefers-color-scheme:dark){
  :root:not([data-theme="light"]){
    --ground:#13161b; --paper:#1b2027; --ink:#e8eaed; --ink-2:#a6aeb9; --ink-3:#79818d;
    --hair:#2c343f; --hair-2:#232a33; --zebra:#1e242c; --head:#141920;
    --brick:#c8705d; --brick-2:#d8836f; --gold:#c9a55a; --sand:#232a33;
    --go:#4cc98a; --wait:#e0a33f; --link:#6fb2e8;
    --chip:#2a313b; --chip-ink:#bdb49f; --yes:#4cc98a; --no:#d98c80;
    --shadow:0 1px 2px rgba(0,0,0,.4),0 12px 34px rgba(0,0,0,.35);
  }
}
:root[data-theme="dark"]{
  --ground:#13161b; --paper:#1b2027; --ink:#e8eaed; --ink-2:#a6aeb9; --ink-3:#79818d;
  --hair:#2c343f; --hair-2:#232a33; --zebra:#1e242c; --head:#141920;
  --brick:#c8705d; --brick-2:#d8836f; --gold:#c9a55a; --sand:#232a33;
  --go:#4cc98a; --wait:#e0a33f; --link:#6fb2e8;
  --chip:#2a313b; --chip-ink:#bdb49f; --yes:#4cc98a; --no:#d98c80;
  --shadow:0 1px 2px rgba(0,0,0,.4),0 12px 34px rgba(0,0,0,.35);
}
*{box-sizing:border-box}
body{margin:0;background:var(--ground);color:var(--ink);
  font-family:"PT Serif",Georgia,"Times New Roman",serif;font-size:16px;line-height:1.6}
.wrap{max-width:1140px;margin:0 auto;padding:0 20px 70px}
h1,h2,h3,h4,h5,.ui,table,.bdg,.btn,.chip{font-family:"PT Sans","Helvetica Neue",Arial,sans-serif}

header.top{background:var(--paper);border-bottom:3px solid var(--brick);padding:40px 0 28px;
  margin-bottom:36px}
header.top .wrap{padding-bottom:0}
.kicker{font-size:11.5px;letter-spacing:.17em;text-transform:uppercase;color:var(--brick);
  font-weight:700;margin:0 0 10px;font-family:"PT Sans",Arial,sans-serif}
h1{margin:0;font-size:38px;line-height:1.12;font-weight:700;text-wrap:balance;letter-spacing:-.015em}
.lede{margin:14px 0 0;font-size:17px;color:var(--ink-2);max-width:68ch}
.meta{margin:18px 0 0;font-family:"PT Sans",Arial,sans-serif;font-size:12.5px;color:var(--ink-3)}
.stats{display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:1px;
  background:var(--hair);border:1px solid var(--hair);margin:26px 0 0}
.stats div{background:var(--paper);padding:13px 15px}
.stats dt{font-family:"PT Sans",Arial,sans-serif;font-size:10.5px;letter-spacing:.09em;
  text-transform:uppercase;color:var(--ink-3);font-weight:700;margin:0 0 3px}
.stats dd{margin:0;font-size:21px;font-weight:700;font-variant-numeric:tabular-nums;
  font-family:"PT Sans",Arial,sans-serif}
.strip{display:grid;grid-template-columns:repeat(3,1fr);gap:8px;margin:26px 0 0}
.strip figure{margin:0}
.strip img{width:100%;height:170px;object-fit:cover;display:block;background:var(--hair-2)}
.strip figcaption{font-family:"PT Sans",Arial,sans-serif;font-size:11px;color:var(--ink-3);
  padding-top:5px}

h2{font-size:25px;margin:54px 0 6px;letter-spacing:-.01em;text-wrap:balance}
h2 .n{color:var(--brick);font-variant-numeric:tabular-nums}
.gsub{margin:0 0 6px;font-family:"PT Sans",Arial,sans-serif;font-size:12px;letter-spacing:.09em;
  text-transform:uppercase;color:var(--ink-3);font-weight:700}
.gnote{margin:0 0 22px;color:var(--ink-2);max-width:68ch}
.tnote{font-family:"PT Sans",Arial,sans-serif;font-size:12.5px;color:var(--ink-3);
  margin:10px 0 0;max-width:86ch}
.scroll{overflow-x:auto;margin:0 0 10px}

/* ---- таблицы ---------------------------------------------------------- */
table{border-collapse:collapse;width:100%;font-size:13.5px}
table.sum{background:var(--paper);box-shadow:var(--shadow)}
table.sum th{background:var(--head);color:#f2f4f7;text-align:left;padding:10px 12px;
  font-size:10.5px;letter-spacing:.07em;text-transform:uppercase;white-space:nowrap}
table.sum td{padding:9px 12px;border-bottom:1px solid var(--hair-2);vertical-align:top}
table.sum tr:nth-child(even) td{background:var(--zebra)}
table.sum td.n,table.sum th.n{text-align:right;font-variant-numeric:tabular-nums;white-space:nowrap}
table.sum a{color:var(--link)}
td.cx{white-space:nowrap;color:var(--ink-2)}
.role{font-size:11px;font-weight:700;padding:2px 7px;border-radius:2px;background:var(--chip);
  color:var(--chip-ink);white-space:nowrap}
.role.ens{background:var(--brick);color:#fff}

table.kv tbody th{text-align:left;font-weight:400;color:var(--ink-3);font-size:12.5px;
  padding:6px 14px 6px 0;width:46%;vertical-align:top;border-bottom:1px solid var(--hair-2)}
table.kv tbody td{padding:6px 0;text-align:right;font-weight:600;
  border-bottom:1px solid var(--hair-2);vertical-align:top}
table.kv{table-layout:fixed}
.kvwrap{display:grid;grid-template-columns:1fr 1fr;gap:0 30px}
@media screen and (max-width:760px){.kvwrap{grid-template-columns:1fr;gap:0}}

table.grid thead th{background:var(--sand);color:var(--ink-2);text-align:left;
  font-size:10.5px;letter-spacing:.06em;text-transform:uppercase;font-weight:700;
  padding:7px 10px;border-bottom:1px solid var(--hair);white-space:nowrap}
table.grid thead th.n,table.grid td.n{text-align:right}
table.grid td{padding:7px 10px;border-bottom:1px solid var(--hair-2);vertical-align:top;
  font-variant-numeric:tabular-nums}
table.grid td:first-child{font-variant-numeric:normal}
table.grid .vri{font-variant-numeric:normal;color:var(--ink-2);font-size:12.5px}
table.grid a{color:var(--link)}
table.eng{max-width:420px}
.yn{font-weight:700}.yn.y{color:var(--yes)}.yn.n{color:var(--no)}
code{font-family:"PT Mono",ui-monospace,monospace;font-size:12.5px;background:var(--hair-2);
  padding:1px 5px;border-radius:2px}

/* ---- карточка --------------------------------------------------------- */
.okn{background:var(--paper);box-shadow:var(--shadow);margin:0 0 32px;
  border-top:3px solid var(--gold);padding:26px 28px 24px}
.ohead{display:flex;gap:16px;align-items:flex-start;margin:0 0 18px}
.oid{font-family:"PT Sans",Arial,sans-serif;font-size:13px;font-weight:700;color:#fff;
  background:var(--brick);width:30px;height:30px;flex:none;display:flex;align-items:center;
  justify-content:center;border-radius:50%}
.otitle{flex:1}
.ohead h3{margin:0;font-size:23px;line-height:1.24;text-wrap:balance}
.addr{margin:4px 0 0;color:var(--ink-2);font-size:14.5px}
.bdgs{display:flex;flex-wrap:wrap;gap:6px;margin-top:9px}
.bdg{font-size:11px;font-weight:700;padding:3px 9px;border-radius:2px;background:var(--chip);
  color:var(--chip-ink);letter-spacing:.02em}
.bdg.go{background:var(--go);color:#fff}
.bdg.wait{background:var(--wait);color:#fff}
.bdg.fed{background:var(--sand);color:var(--brick);border:1px solid var(--brick)}
.bdg.ens{background:var(--brick);color:#fff}
.idtag{margin-left:auto;font-family:"PT Sans",Arial,sans-serif;font-size:11px;color:var(--ink-3);
  white-space:nowrap;text-align:right;padding-top:4px;line-height:1.5}
.idtag span{font-family:"PT Mono",monospace;font-size:10.5px}
.cont{display:none}

.gal{display:grid;grid-template-columns:repeat(auto-fit,minmax(200px,1fr));gap:10px;margin:0 0 14px}
.gal figure{margin:0}
.gal img{width:100%;height:205px;object-fit:cover;display:block;background:var(--hair-2)}
.bigmap{margin:0 0 20px}
.bigmap img{width:100%;height:330px;object-fit:cover;display:block;border:1px solid var(--hair)}
.bigmap figcaption{font-family:"PT Sans",Arial,sans-serif;font-size:11.5px;color:var(--ink-3);
  padding-top:5px}
.bigmap a{color:var(--link)}

.block{margin:0 0 20px}
h4{margin:0 0 9px;font-size:11px;letter-spacing:.1em;text-transform:uppercase;color:var(--ink-3);
  font-weight:700;padding-bottom:5px;border-bottom:1px solid var(--hair)}
.digest{background:var(--sand);padding:18px 20px;border-left:3px solid var(--brick)}
.digest h4{border-bottom-color:var(--hair)}
.dg{margin:0 0 14px}
.dg:last-of-type{margin-bottom:0}
.dg h5{margin:0 0 6px;font-size:13px;font-weight:700;color:var(--brick);letter-spacing:.01em}
.dg ul{margin:0;padding-left:18px}
.dg li{margin:0 0 5px;color:var(--ink-2);font-size:14.5px;max-width:72ch}
.dgsrc{margin:12px 0 0;font-family:"PT Sans",Arial,sans-serif;font-size:11px;color:var(--ink-3)}
.photos{display:block}
.shot{margin:0 0 18px}
.shot img{width:100%;height:auto;display:block;background:var(--hair-2)}
.photos .shot img{background:none}
.shot figcaption{font-family:"PT Sans",Arial,sans-serif;font-size:11.5px;color:var(--ink-3);
  padding-top:6px}
.shot:last-child{margin-bottom:0}
.hist p{margin:0 0 9px;color:var(--ink-2);font-size:15px;max-width:72ch}
.hist p:last-child{margin-bottom:0}

.links{display:flex;flex-wrap:wrap;gap:9px;margin-top:20px;padding-top:16px;
  border-top:1px solid var(--hair)}
.btn{font-size:13px;font-weight:700;text-decoration:none;padding:8px 15px;background:var(--brick);
  color:#fff;border-radius:2px}
.btn.alt{background:transparent;color:var(--link);border:1px solid var(--hair)}
.btn:hover{opacity:.87}
a:focus-visible,.btn:focus-visible{outline:2px solid var(--link);outline-offset:2px}

.terms{background:var(--paper);box-shadow:var(--shadow);padding:26px 28px;
  border-left:4px solid var(--brick);margin:0 0 30px}
.terms h3{margin:0 0 4px;font-size:21px}
.terms .sect{margin:20px 0 0}
.terms ul{margin:6px 0 0;padding-left:20px;color:var(--ink-2)}
.terms li{margin:0 0 6px;max-width:72ch}
.rates{display:grid;grid-template-columns:repeat(auto-fit,minmax(210px,1fr));gap:12px;margin:14px 0 0}
.rate{border:1px solid var(--hair);padding:13px 15px}
.rate b{font-size:27px;color:var(--brick);display:block;line-height:1.1;
  font-family:"PT Sans",Arial,sans-serif}
.rate span{font-size:13.5px;color:var(--ink-2)}
.deadlines td{padding:5px 16px 5px 0;border-bottom:1px solid var(--hair-2);font-size:13.5px}
.deadlines td:last-child{font-weight:700;white-space:nowrap;text-align:right}
.deadlines{max-width:520px}

footer.src{margin-top:44px;padding-top:18px;border-top:1px solid var(--hair);
  font-family:"PT Sans",Arial,sans-serif;font-size:12.5px;color:var(--ink-3)}
footer.src a{color:var(--link)}
footer.src p{margin:0 0 8px;max-width:84ch}

@media screen and (max-width:760px){
  h1{font-size:28px} .okn{padding:20px 16px} .wrap{padding:0 16px 50px}
  .gal img{height:180px} .bigmap img{height:230px}
  table.kv tbody th{width:52%}
}

/* ---- печать ----------------------------------------------------------- */
@page{size:A4;margin:13mm 12mm 15mm}
@media print{
  :root{--ground:#fff;--paper:#fff;--zebra:#faf8f2}
  html,body{background:#fff;font-size:9.4pt;line-height:1.48}
  body{-webkit-print-color-adjust:exact;print-color-adjust:exact}
  .wrap{max-width:none;padding:0}

  header.top{border-bottom:none;padding:0;margin:0;break-after:page;min-height:248mm;
    display:flex;flex-direction:column;justify-content:center}
  header.top h1{font-size:29pt;line-height:1.08;margin-top:6mm}
  header.top .lede{font-size:11.5pt;margin-top:7mm;max-width:none}
  .kicker{font-size:9.5pt;letter-spacing:.2em}
  .stats{margin-top:11mm;border-color:#d8d3c7;grid-template-columns:repeat(4,1fr)}
  .stats div{padding:4mm}
  .stats dt{font-size:6.6pt;letter-spacing:.07em}
  .stats dd{font-size:13.5pt}
  .strip{margin-top:9mm;gap:3mm}
  .strip img{height:42mm}
  .strip figcaption{font-size:7.4pt}
  .meta{margin-top:8mm;font-size:8.2pt}

  h2{font-size:15pt;margin:0 0 2mm;break-after:avoid;break-before:page}
  h2:first-of-type{break-before:auto}
  .gsub{break-after:avoid;font-size:7.6pt}
  .gnote{font-size:9.4pt;margin-bottom:5mm;max-width:none;break-before:avoid}
  .tnote{font-size:8.4pt}

  table.sum{font-size:7.9pt;box-shadow:none;border:1px solid #d8d3c7}
  table.sum th{padding:2.2mm 1.8mm;font-size:6.6pt;background:#232a34 !important;color:#fff !important}
  table.sum td{padding:1.9mm 1.8mm}
  table.sum tr{break-inside:avoid}

  .terms{box-shadow:none;border:1px solid #d8d3c7;border-left:3px solid var(--brick);
    padding:6mm 7mm;break-inside:avoid}
  .terms .sect{break-inside:avoid;margin-top:5mm}
  .rate b{font-size:18pt}
  .deadlines{font-size:8.8pt}

  /* Разворот на объект: первая полоса — образ и паспорт, вторая — таблицы
     и выжимка. Перелом стоит жёстко, поэтому блоки не свисают сиротами. */
  .okn{box-shadow:none;border:none;border-top:2px solid var(--gold);padding:4mm 0 0;
    margin:0;break-before:page;break-inside:auto}
  .p1{break-inside:avoid}
  .p2{break-before:page}
  .cont{display:block;font-family:"PT Sans",Arial,sans-serif;font-size:8pt;color:var(--ink-3);
    margin:0 0 4mm;padding-bottom:1.5mm;border-bottom:1px solid var(--hair)}
  .ohead{margin-bottom:4mm;break-after:avoid}
  .ohead h3{font-size:15pt}
  .addr{font-size:10pt}
  .oid{width:7mm;height:7mm;font-size:8.5pt}
  .idtag{font-size:7.6pt}
  .bdg{font-size:7.4pt;padding:.6mm 2mm}

  .gal{gap:2.5mm;margin-bottom:3.5mm;grid-template-columns:repeat(auto-fit,minmax(38mm,1fr))}
  .gal img{height:40mm}
  .bigmap{margin-bottom:4mm}
  .bigmap img{height:78mm}
  .bigmap figcaption{font-size:7.6pt}

  .block{margin-bottom:4mm;break-inside:avoid}
  h4{font-size:7.8pt;margin-bottom:2mm}
  table{font-size:8.6pt}
  .kvwrap{gap:0 7mm}
  table.kv tbody th{font-size:8.2pt;padding:1.3mm 3mm 1.3mm 0}
  table.kv tbody td{padding:1.5mm 0}
  table.grid thead th{font-size:6.8pt;padding:1.6mm 2mm}
  table.grid td{padding:1.5mm 2mm}
  .digest{padding:4mm 5mm;break-inside:auto}
  .dg{break-inside:avoid}
  .dg h5{font-size:9.4pt}
  .dg li{font-size:8.8pt;margin-bottom:1.2mm;max-width:none}
  .hist p{font-size:8.8pt;margin-bottom:1.8mm;max-width:none}
  .hist{break-inside:auto}

  /* кнопки не должны открывать полосу в одиночку */
  .p3{break-before:page}
  .photos .shot{margin-bottom:5mm;break-inside:avoid}
  /* без object-fit: иначе по краю кадра остаётся светлая полоса */
  .photos .shot img{max-width:100%;max-height:150mm;width:auto;height:auto;
    background:none}
  .photos .shot figcaption{font-size:7.6pt}
  .links{margin-top:4mm;padding-top:3mm;break-before:avoid;break-inside:avoid}
  .digest .dg:last-of-type{break-after:avoid}
  .btn{font-size:8.2pt;padding:1.5mm 3.5mm;background:var(--brick) !important;color:#fff !important}
  .btn.alt{background:#fff !important;color:var(--link) !important;border:1px solid #d8d3c7}

  footer.src{break-before:page;font-size:8.4pt;margin-top:0;padding-top:0;border-top:none}
  footer.src p{max-width:none;margin-bottom:3mm}
  a{text-decoration:none}
}
"""
