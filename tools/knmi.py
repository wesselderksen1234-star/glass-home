# Haalt de officiële KNMI-waarschuwingen (code geel/oranje/rood) op via MeteoAlarm
# en zet ze als klein bestand klaar voor Glass Home (de app kijkt zelf of jouw woonplaats binnen het gebied valt).
import json,os,sys,urllib.request,time
from datetime import datetime
out=sys.argv[1];os.makedirs(out,exist_ok=True)
def get(u):return urllib.request.urlopen(urllib.request.Request(u,headers={"User-Agent":"glass-home"}),timeout=30).read()
d=json.loads(get("https://feeds.meteoalarm.org/api/v1/warnings/feeds-netherlands"))
now=time.time();res=[]
def ts(s):
    try:return int(datetime.fromisoformat(s).timestamp())
    except Exception:return 0
for w in d.get("warnings",[]):
    a=w.get("alert",{})
    if a.get("msgType")=="Cancel":continue
    infos=a.get("info",[])
    inf=next((i for i in infos if i.get("language","").startswith("nl")),infos[0] if infos else None)
    if not inf:continue
    p={x.get("valueName"):x.get("value") for x in inf.get("parameter",[])}
    try:lv=int((p.get("awareness_level") or "0").split(";")[0])
    except Exception:lv=0
    ex=ts(inf.get("expires",""));on=ts(inf.get("onset","") or inf.get("effective",""))
    if lv<2 or (ex and ex<now):continue
    polys=[]
    for ar in inf.get("area",[]):
        for pg in ar.get("polygon",[]):
            pts=[[round(float(c.split(",")[0]),3),round(float(c.split(",")[1]),3)] for c in pg.split()]
            st=max(1,len(pts)//150);polys.append(pts[::st])
    res.append({"lv":lv,"ty":(p.get("awareness_type") or "").split(";")[-1].strip(),"ev":inf.get("event",""),"hd":inf.get("headline",""),
                "ds":(inf.get("description") or "")[:400],"on":on,"ex":ex,"ar":", ".join(x.get("areaDesc","") for x in inf.get("area",[]))[:160],"pl":polys})
json.dump({"t":int(now),"w":res},open(os.path.join(out,"knmi.json"),"w"),separators=(",",":"))
