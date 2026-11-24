# Haalt de officiële KNMI-waarschuwingen (code geel/oranje/rood) op via MeteoAlarm en zet ze klaar voor Glass Home.
import json,os,sys,urllib.request,time
out=sys.argv[1];os.makedirs(out,exist_ok=True)
def get(u):return urllib.request.urlopen(urllib.request.Request(u,headers={"User-Agent":"glass-home"}),timeout=30).read()
open(os.path.join(out,"ma.json"),"wb").write(get("https://feeds.meteoalarm.org/api/v1/warnings/feeds-netherlands"))
json.dump({"t":int(time.time())},open(os.path.join(out,"knmi.json"),"w"))
