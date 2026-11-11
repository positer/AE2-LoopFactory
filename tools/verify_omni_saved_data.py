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
assert {'跨维度物流','备用链路','Client created link'}<=names,names
assert len({x['id'] for x in links})==len(links)
result={'passed':True,'generation':a.generation,'savedDataFile':str(files[0]),'entries':len(links),'unicodeNamesAndClientCreatedLinkOnDisk':True,'uniqueStableIds':True}
target=e/'saved-data-readback.json'
if target.exists():
 assert json.loads(target.read_text(encoding='utf-8'))==result, 'Existing disk readback differs'
else:
 target.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(json.dumps(result,ensure_ascii=False,indent=2))
