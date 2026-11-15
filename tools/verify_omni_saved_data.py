"""独立读取已正常退出的原生测试存档，核对频道 SavedData 的实际压缩 NBT。"""
import argparse,gzip,io,json,struct
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('evidence');p.add_argument('--generation',required=True);a=p.parse_args()
e=Path(a.evidence);report=json.loads((e/'report.json').read_text(encoding='utf-8-sig'))
root=Path(__file__).resolve().parents[1]/f'versions/neoforge-{a.generation}/run/saves'/report['world']
root=root/('data' if a.generation=='1.21.1' else 'dimensions/minecraft/overworld/data')
def parse(path):
 f=io.BytesIO(gzip.decompress(path.read_bytes()))
 def take(n):
  b=f.read(n)
  if len(b)!=n:raise ValueError('truncated NBT')
  return b
 def number(fmt):return struct.unpack('>'+fmt,take(struct.calcsize(fmt)))[0]
 def string():return take(number('H')).decode('utf-8')
 def value(t):
  if t in range(1,7):return number({1:'b',2:'h',3:'i',4:'q',5:'f',6:'d'}[t])
  if t==7:return take(number('i'))
  if t==8:return string()
  if t==9:
   child=number('B');return [value(child) for _ in range(number('i'))]
  if t==10:
   out={}
   while (child:=number('B')):
    name=string();out[name]=value(child)
   return out
  if t in (11,12):return [number('i' if t==11 else 'q') for _ in range(number('i'))]
  raise ValueError(f'unknown NBT tag {t}')
 tag=number('B');name=string();return value(tag)
files=[f for f in root.rglob('*omni*dat') if f.is_file()]
assert len(files)==1,files
nbt=parse(files[0]);links=nbt['data']['links'];names={x['name'] for x in links}
chain=report.get('chain','omni_connector')
assert len({x['id'] for x in links})==len(links)
if chain=='omni_access':
 assert {'Open access','Secret plan','Other private','Fresh private','Client private'}<=names,names
 public=[x for x in links if x.get('visibility','public')=='public']
 private=[x for x in links if x.get('visibility')=='private']
 assert len(public)==1 and public[0]['name']=='Open access',public
 assert {x['name'] for x in private}=={'Secret plan','Other private','Fresh private','Client private'},private
 assert len(private)==5,[(x['name'],x.get('visibility')) for x in links]
 owners={x['name']:x.get('owner','') for x in private}
 assert all(owners.values()),owners
 assert len({x['owner'] for x in private if x['name']=='Secret plan'})==2,private
 import uuid
 for owner in owners.values(): uuid.UUID(owner)
 result={'passed':True,'generation':a.generation,'chain':chain,'savedDataFile':str(files[0]),'entries':len(links),
         'publicLinks':len(public),'privateLinks':len(private),'privateOwnersOnDisk':sorted(set(owners.values())),
         'samePrivateNameDifferentOwners':True,'uniqueStableIds':True}
else:
 assert {'跨维度物流','备用链路','Client created link'}<=names,names
 result={'passed':True,'generation':a.generation,'chain':chain,'savedDataFile':str(files[0]),'entries':len(links),'unicodeNamesAndClientCreatedLinkOnDisk':True,'uniqueStableIds':True}
target=e/'saved-data-readback.json'
if target.exists():
 assert json.loads(target.read_text(encoding='utf-8'))==result, 'Existing disk readback differs'
else:
 target.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps(result,ensure_ascii=False,indent=2))
