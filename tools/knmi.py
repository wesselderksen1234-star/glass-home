# Haalt de officiële KNMI-waarschuwingen (code geel/oranje/rood) op via MeteoAlarm en zet ze klaar voor Glass Home.
import json,os,sys,urllib.request,time
out=sys.argv[1];os.makedirs(out,exist_ok=True)
U="https://feeds.meteoalarm.org/feeds/meteoalarm-legacy-atom-netherlands"
raw=urllib.request.urlopen(urllib.request.Request(U,headers={"User-Agent":"glass-home"}),timeout=30).read()
open(os.path.join(out,"meteoalarm.xml"),"wb").write(raw)
json.dump({"t":int(time.time())},open(os.path.join(out,"knmi.json"),"w"))
