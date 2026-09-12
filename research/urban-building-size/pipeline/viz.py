# -*- coding: utf-8 -*-
import json, math, os
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.ticker import FuncFormatter

SURF="#fcfcfb"; INK="#0b0b0b"; INK2="#52514e"; MUTED="#898781"
GRID="#e1e0d9"; BASE="#c3c2b7"
# ординальная шкала (одна гамма, светлая→тёмная), шаг не светлее 250
SEQ=["#86b6ef","#3987e5","#256abf","#104281"]
# категориальные слоты в фиксированном порядке
CAT={"Азия":"#2a78d6","Африка":"#eb6834","Европа":"#1baf7a",
     "Северная Америка":"#eda100","Южная Америка":"#e87ba4","Океания":"#008300",
     "Азия/Европа":"#4a3aa7","Антарктида":"#e34948"}
BINLBL=["до 300 м²","300–1 000 м²","1 000–10 000 м²","свыше 10 000 м²"]

plt.rcParams.update({
 "font.family":"DejaVu Sans","font.size":9.5,
 "figure.facecolor":SURF,"axes.facecolor":SURF,
 "axes.edgecolor":BASE,"axes.labelcolor":INK2,
 "text.color":INK,"xtick.color":MUTED,"ytick.color":MUTED,
 "axes.grid":True,"grid.color":GRID,"grid.linewidth":0.7,
 "axes.spines.top":False,"axes.spines.right":False,
 "legend.frameon":False,"savefig.facecolor":SURF,
})

def load(p):
    return [json.loads(l) for l in open(p) if json.loads(l).get("n",0)>0]

HEAD_IN=1.30   # высота шапки (дюймы): заголовок + подзаголовок + легенда
def header(fig,t,sub,H,legend_handles=None,ncol=4):
    fig.subplots_adjust(top=1-HEAD_IN/H, bottom=0.62/H, left=None, right=0.985)
    fig.text(0.008,1-0.22/H,t,fontsize=13.5,fontweight="bold",color=INK,ha="left",va="top")
    if sub: fig.text(0.008,1-0.56/H,sub,fontsize=9,color=MUTED,ha="left",va="top")
    if legend_handles:
        fig.legend(handles=legend_handles,loc="upper left",
                   bbox_to_anchor=(0.008,1-0.80/H),ncol=ncol,fontsize=9,
                   frameon=False,handlelength=1.1,columnspacing=1.6,handletextpad=0.55)

def save(fig,name):
    fig.savefig(name,dpi=200,pad_inches=0.20,bbox_inches="tight")
    plt.close(fig); print("wrote",name)

def fig_mix(rows,fname,ttl,sub):
    rs=sorted(rows,key=lambda r:r["bin_counts"][0]/r["n"])
    names=[r["city"] for r in rs]; y=range(len(rs))
    H=0.30*len(rs)+2.1
    fig,ax=plt.subplots(figsize=(9.2,H))
    left=[0.0]*len(rs)
    for k in range(4):
        v=[100*r["bin_counts"][k]/r["n"] for r in rs]
        ax.barh(list(y),v,left=left,height=0.68,color=SEQ[k],label=BINLBL[k],
                edgecolor=SURF,linewidth=1.4)
        for i,(vv,ll) in enumerate(zip(v,left)):
            if vv>=7: ax.text(ll+vv/2,i,f"{vv:.0f}",ha="center",va="center",fontsize=8,
                              color="#ffffff" if k>=2 else INK)
        left=[a+b for a,b in zip(left,v)]
    ax.set_yticks(list(y)); ax.set_yticklabels(names,fontsize=9,color=INK)
    ax.set_xlim(0,100); ax.set_xlabel("доля зданий, %"); ax.xaxis.grid(True); ax.yaxis.grid(False)
    ax.set_axisbelow(True)
    hs=[plt.Rectangle((0,0),1,1,color=SEQ[k],label=BINLBL[k]) for k in range(4)]
    header(fig,ttl,sub,H,hs,ncol=4)
    save(fig,fname)

def fig_scatter(rows,fname):
    H=6.9
    fig,ax=plt.subplots(figsize=(9.2,H))
    for r in rows:
        ax.scatter(r["median_m2"],100*r["coverage_ratio"],s=68,
                   color=CAT.get(r["continent"],MUTED),edgecolor=SURF,linewidth=1.6,zorder=3)
    fig.canvas.draw()
    placed=[]
    cand=[(8,4,"left"),(8,-12,"left"),(-8,4,"right"),(-8,-12,"right"),
          (8,15,"left"),(8,-23,"left"),(-8,15,"right"),(-8,-23,"right")]
    order=sorted(rows,key=lambda r:-r["coverage_ratio"])
    for r in order:
        px,py=ax.transData.transform((math.log10(r["median_m2"]) if ax.get_xscale()=="log" else r["median_m2"],
                                      100*r["coverage_ratio"]))
        if ax.get_xscale()=="log":
            px,py=ax.transData.transform((r["median_m2"],100*r["coverage_ratio"]))
        w=len(r["city"])*5.6+4; h=13
        best=cand[0]
        for dx,dy,ha in cand:
            x0=px+dx if ha=="left" else px+dx-w
            bb=(x0,py+dy-3,x0+w,py+dy+h-3)
            if not any(not(bb[2]<q[0] or bb[0]>q[2] or bb[3]<q[1] or bb[1]>q[3]) for q in placed):
                best=(dx,dy,ha); placed.append(bb); break
        else:
            dx,dy,ha=cand[0]; x0=px+dx
            placed.append((x0,py+dy-3,x0+w,py+dy+h-3))
        ax.annotate(r["city"],(r["median_m2"],100*r["coverage_ratio"]),
                    textcoords="offset points",xytext=(best[0],best[1]),
                    ha=best[2],fontsize=8,color=INK2)
    ax.set_xscale("log")
    ax.set_xticks([80,100,150,200,300,400]); ax.get_xaxis().set_major_formatter(FuncFormatter(lambda v,p:f"{v:.0f}"))
    ax.set_xlabel("медианное пятно застройки, м² (лог. шкала)")
    ax.set_ylabel("коэффициент застроенности освоенной территории, %")
    seen=[]
    for r in rows:
        if r["continent"] not in seen: seen.append(r["continent"])
    hs=[plt.Line2D([],[],marker="o",ls="",color=CAT.get(c,MUTED),markersize=7,label=c) for c in seen]
    header(fig,"Размер зерна и плотность заполнения",
           "каждая точка — 10-км диск вокруг центра города · MS GlobalMLBuildingFootprints, 2026",H,hs,ncol=4)
    save(fig,fname)

def fig_lolli(rows,key,fname,ttl,sub,xlabel,scale=1.0,fmt="{:.1f}"):
    rs=sorted(rows,key=lambda r:r[key]*scale)
    H=0.29*len(rs)+2.0
    fig,ax=plt.subplots(figsize=(9.2,H))
    for i,r in enumerate(rs):
        v=r[key]*scale; c=CAT.get(r["continent"],MUTED)
        ax.plot([0,v],[i,i],color=c,linewidth=2.0,alpha=.55,zorder=2,solid_capstyle="round")
        ax.scatter([v],[i],s=62,color=c,edgecolor=SURF,linewidth=1.5,zorder=3)
        ax.text(v,i,"  "+fmt.format(v),va="center",fontsize=8.4,color=INK2)
    ax.set_yticks(range(len(rs))); ax.set_yticklabels([r["city"] for r in rs],fontsize=9,color=INK)
    ax.set_xlabel(xlabel); ax.yaxis.grid(False); ax.xaxis.grid(True); ax.set_axisbelow(True)
    ax.set_xlim(0,max(r[key]*scale for r in rs)*1.16)
    seen=[]
    for r in rs:
        if r["continent"] not in seen: seen.append(r["continent"])
    hs=[plt.Line2D([],[],marker="o",ls="",color=CAT.get(c,MUTED),markersize=7,label=c) for c in seen]
    header(fig,ttl,sub,H,hs,ncol=4)
    save(fig,fname)

def fig_big(rows,fname):
    rs=sorted(rows,key=lambda r:r["bin_counts"][3])
    H=0.29*len(rs)+2.0
    fig,ax=plt.subplots(figsize=(9.2,H))
    for i,r in enumerate(rs):
        v=r["bin_counts"][3]; c=CAT.get(r["continent"],MUTED)
        ax.barh(i,v,height=0.62,color=c,edgecolor=SURF,linewidth=1.2)
        per=100*v/r["builtup_km2"]
        ax.text(v,i,f"  {v}   ·   {per:.0f} на 100 км²",va="center",fontsize=8.2,color=INK2)
    ax.set_yticks(range(len(rs))); ax.set_yticklabels([r["city"] for r in rs],fontsize=9,color=INK)
    ax.set_xlabel("число зданий с пятном застройки свыше 10 000 м²")
    ax.yaxis.grid(False); ax.xaxis.grid(True); ax.set_axisbelow(True)
    ax.set_xlim(0,max(r["bin_counts"][3] for r in rs)*1.45)
    seen=[]
    for r in rs:
        if r["continent"] not in seen: seen.append(r["continent"])
    hs=[plt.Rectangle((0,0),1,1,color=CAT.get(c,MUTED),label=c) for c in seen]
    header(fig,"Крупнейшая размерная категория: здания свыше 10 000 м²",
           "абсолютное число внутри 10-км диска и плотность на 100 км² освоенной территории",H,hs,4)
    save(fig,fname)

def fig_cont(rows,fname):
    order=["Африка","Азия","Европа","Северная Америка","Южная Америка","Океания"]
    agg={}
    for c in order:
        sub=[r for r in rows if r["continent"]==c]
        if not sub: continue
        agg[c]=[sum(100*r["bin_counts"][k]/r["n"] for r in sub)/len(sub) for k in range(4)]+[len(sub)]
    ks=list(agg.keys()); H=0.52*len(ks)+2.2
    fig,ax=plt.subplots(figsize=(9.2,H))
    left=[0.0]*len(ks)
    for k in range(4):
        v=[agg[c][k] for c in ks]
        ax.barh(range(len(ks)),v,left=left,height=0.58,color=SEQ[k],label=BINLBL[k],
                edgecolor=SURF,linewidth=1.6)
        for i,(vv,ll) in enumerate(zip(v,left)):
            if vv>=4.5: ax.text(ll+vv/2,i,f"{vv:.1f}",ha="center",va="center",fontsize=8.6,
                                color="#ffffff" if k>=2 else INK)
        left=[a+b for a,b in zip(left,v)]
    ax.set_yticks(range(len(ks)))
    ax.set_yticklabels([f"{c}  (n={agg[c][4]})" for c in ks],fontsize=9.5,color=INK)
    ax.set_xlim(0,100); ax.set_xlabel("средняя по городам доля зданий, %")
    ax.yaxis.grid(False); ax.xaxis.grid(True); ax.set_axisbelow(True)
    hs=[plt.Rectangle((0,0),1,1,color=SEQ[k],label=BINLBL[k]) for k in range(4)]
    header(fig,"Континентальный профиль размерной структуры",
           "невзвешенное среднее долей по городам континента · MS GlobalMLBuildingFootprints",H,hs,4)
    save(fig,fname)

def fig_dens(rows,fname):
    H=6.9
    fig,ax=plt.subplots(figsize=(9.2,H))
    xs=[r["mean_m2"] for r in rows]; ys=[r["density_per_builtup_km2"] for r in rows]
    for r in rows:
        ax.scatter(r["mean_m2"],r["density_per_builtup_km2"],s=68,
                   color=CAT.get(r["continent"],MUTED),edgecolor=SURF,linewidth=1.6,zorder=3)
    # регрессия в логарифмах
    lx=[math.log10(x) for x in xs]; ly=[math.log10(y) for y in ys]
    n=len(lx); mx=sum(lx)/n; my=sum(ly)/n
    b=sum((a-mx)*(c-my) for a,c in zip(lx,ly))/sum((a-mx)**2 for a in lx)
    a0=my-b*mx
    r2=(sum((a-mx)*(c-my) for a,c in zip(lx,ly))**2)/(sum((a-mx)**2 for a in lx)*sum((c-my)**2 for c in ly))
    gx=[min(xs)*0.9,max(xs)*1.1]
    ax.plot(gx,[10**(a0+b*math.log10(v)) for v in gx],color=MUTED,lw=1.6,ls="--",zorder=2)
    ax.text(0.98,0.95,f"наклон {b:.2f}   R² = {r2:.2f}",transform=ax.transAxes,ha="right",
            fontsize=9.5,color=INK2)
    fig.canvas.draw(); placed=[]
    cand=[(8,4,"left"),(8,-12,"left"),(-8,4,"right"),(-8,-12,"right"),(8,15,"left"),(-8,15,"right")]
    for r in sorted(rows,key=lambda z:-z["density_per_builtup_km2"]):
        px,py=ax.transData.transform((r["mean_m2"],r["density_per_builtup_km2"]))
        w=len(r["city"])*5.6+4
        best=cand[0]
        for dx,dy,ha in cand:
            x0=px+dx if ha=="left" else px+dx-w
            bb=(x0,py+dy-3,x0+w,py+dy+10)
            if not any(not(bb[2]<q[0] or bb[0]>q[2] or bb[3]<q[1] or bb[1]>q[3]) for q in placed):
                best=(dx,dy,ha); placed.append(bb); break
        ax.annotate(r["city"],(r["mean_m2"],r["density_per_builtup_km2"]),textcoords="offset points",
                    xytext=(best[0],best[1]),ha=best[2],fontsize=8,color=INK2)
    ax.set_xscale("log"); ax.set_yscale("log")
    for axis in (ax.get_xaxis(),ax.get_yaxis()):
        axis.set_major_formatter(FuncFormatter(lambda v,p:f"{v:.0f}"))
        axis.set_minor_formatter(FuncFormatter(lambda v,p:""))
    ax.set_xticks([150,200,300,500,700,1000,2000])
    ax.set_yticks([200,300,500,700,1000,1500])
    ax.set_xlabel("средняя площадь здания, м² (лог.)")
    ax.set_ylabel("зданий на км² освоенной территории (лог.)")
    seen=[]
    for r in rows:
        if r["continent"] not in seen: seen.append(r["continent"])
    hs=[plt.Line2D([],[],marker="o",ls="",color=CAT.get(c,MUTED),markersize=7,label=c) for c in seen]
    header(fig,"Закон сохранения застроенной массы",
           "чем крупнее типичное здание, тем меньше зданий на квадратный километр",H,hs,4)
    save(fig,fname)

def fig_src(pairs,fname):
    rs=sorted(pairs,key=lambda p:p["ratio"])
    H=0.29*len(rs)+2.1
    fig,ax=plt.subplots(figsize=(9.2,H))
    for i,p in enumerate(rs):
        c=CAT.get(p["continent"],MUTED)
        ax.plot([1,p["ratio"]],[i,i],color=c,lw=2.0,alpha=.55,solid_capstyle="round",zorder=2)
        ax.scatter([p["ratio"]],[i],s=62,color=c,edgecolor=SURF,linewidth=1.5,zorder=3)
        if p["ratio"]>=1: ax.text(p["ratio"]*1.07,i,f"{p['ratio']:.2f}×",va="center",fontsize=8.4,color=INK2)
        else: ax.text(p["ratio"]*0.93,i,f"{p['ratio']:.2f}×",va="center",ha="right",fontsize=8.4,color=INK2)
    ax.axvline(1,color=BASE,lw=1.4,zorder=1)
    ax.set_xscale("log")
    ax.set_xticks([0.8,1,1.5,2,3,5,8,12])
    ax.get_xaxis().set_major_formatter(FuncFormatter(lambda v,p:(f"{v:g}")))
    ax.get_xaxis().set_minor_formatter(FuncFormatter(lambda v,p:""))
    ax.set_yticks(range(len(rs))); ax.set_yticklabels([p["city"] for p in rs],fontsize=9,color=INK)
    ax.set_xlabel("во сколько раз Overture насчитывает больше зданий, чем Microsoft (лог. шкала)")
    ax.yaxis.grid(False); ax.xaxis.grid(True); ax.set_axisbelow(True)
    ax.set_xlim(min(0.72,min(p["ratio"] for p in rs)*0.82),max(p["ratio"] for p in rs)*1.9)
    seen=[]
    for p in rs:
        if p["continent"] not in seen: seen.append(p["continent"])
    hs=[plt.Line2D([],[],marker="o",ls="",color=CAT.get(c,MUTED),markersize=7,label=c) for c in seen]
    header(fig,"Индекс дробности: два источника об одном и том же городе",
           "справа — города, где нейросетевая модель склеивает смежные здания в кварталы; слева — где источники согласны\nбез Киншасы (128×) и Сиднея (2,9×): там расхождение вызвано пробелом покрытия, а не сегментацией",H,hs,4)
    save(fig,fname)

def fig_height(rows,fname):
    rs=sorted(rows,key=lambda r:r["h_p99"])
    H=0.34*len(rs)+2.2
    fig,ax=plt.subplots(figsize=(9.2,H))
    for i,r in enumerate(rs):
        c=CAT.get(r["continent"],MUTED)
        ax.plot([r["h_median"],r["h_p99"]],[i,i],color=c,lw=3.0,alpha=.35,solid_capstyle="round",zorder=2)
        ax.scatter([r["h_median"]],[i],s=46,color=c,edgecolor=SURF,linewidth=1.4,zorder=3)
        ax.scatter([r["h_p99"]],[i],s=78,color=c,edgecolor=SURF,linewidth=1.5,zorder=3)
        ax.text(r["h_p99"]+1.6,i,f"{r['h_p99']:.0f} м",va="center",fontsize=8.3,color=INK2)
        ax.text(r["h_median"]-1.6,i,f"{r['h_median']:.0f}",va="center",ha="right",fontsize=8.3,color=MUTED)
    ax.set_yticks(range(len(rs)))
    ax.set_yticklabels([f"{r['city']}  ({100*r['h_known_share']:.0f}%)" for r in rs],fontsize=9,color=INK)
    ax.set_xlabel("высота здания, м")
    ax.yaxis.grid(False); ax.xaxis.grid(True); ax.set_axisbelow(True)
    ax.set_xlim(0,max(r["h_p99"] for r in rs)*1.18)
    hs=[plt.Line2D([],[],marker="o",ls="",color=MUTED,markersize=5,label="медианное здание"),
        plt.Line2D([],[],marker="o",ls="",color=MUTED,markersize=8,label="99-й процентиль (верхний 1%)")]
    header(fig,"Вертикальное измерение: медиана и верхний процент",
           "только города, где высота известна не менее чем для 40% зданий (доля в скобках) · Overture Maps",H,hs,2)
    save(fig,fname)
