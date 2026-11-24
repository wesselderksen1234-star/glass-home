# Haalt officiële KNMI-waarschuwingen op (open data, anonieme sleutel) en zet ze klaar voor Glass Home.
import json,os,sys,urllib.request,time
K="eyJvcmciOiI1ZTU1NGUxOTI3NGE5NjAwMDEyYTNlYjEiLCJpZCI6IjUzYTg1ZDBhMmQ5YzRkYzJiYWNlNzQ4NTQ2Zjk4ODExIiwiaCI6Im11cm11cjEyOCJ9"
B="https://api.dataplatform.knmi.nl/open-data/v1/datasets/waarschuwingen_nederland_48h/versions/1.0/files"
out=sys.argv[1];os.makedirs(out,exist_ok=True)
def get(u,h=True):
    r=urllib.request.Request(u,headers={"Authorization":K} if h else {});return urllib.request.urlopen(r,timeout=30).read()
l=json.loads(get(B+"?orderBy=created&sorting=desc&maxKeys=10"))
files=[f["filename"] for f in l.get("files",[])]
for ext in ("xml","txt"):
    fn=next((f for f in files if f.lower().endswith(ext)),None)
    if fn:
        u=json.loads(get(B+"/"+fn+"/url"))["temporaryDownloadUrl"]
        open(os.path.join(out,"knmi."+ext),"wb").write(get(u,False))
json.dump({"t":int(time.time()),"files":files},open(os.path.join(out,"knmi.json"),"w"))
