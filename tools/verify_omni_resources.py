"""只读对照全向连接器的资源与 AE2 原生模型合同。"""
from pathlib import Path
import json, zipfile
ROOT=Path(__file__).resolve().parents[1]
results=[]
for gen in ['1.21.1','26.1.2']:
 r=ROOT/f'versions/neoforge-{gen}/src/main/resources'
 properties=(ROOT/f'versions/neoforge-{gen}/gradle.properties').read_text(encoding='utf-8')
 version=next(line.split('=',1)[1].strip() for line in properties.splitlines() if line.startswith('ae2_version='))
 j=next((ROOT/f'.gradle-user-home/{gen}/caches/modules-2/files-2.1/org.appliedenergistics/appliedenergistics2/{version}').rglob('appliedenergistics2-*.jar'))
 def read(p):return json.loads((r/p).read_text(encoding='utf-8'))
 with zipfile.ZipFile(j) as z:
  original=json.loads(z.read('assets/ae2/blockstates/wireless_access_point.json'))
  actual=read('assets/ae2lpt/blockstates/omnidirectional_connector.json')
  assert len(original['multipart'])==len(actual['multipart'])
  for a,b in zip(original['multipart'],actual['multipart']):
   old=a['apply']['model'];new=b['apply']['model']
   assert old!=new, '所有部件都应引用白色纹理'
   if old!=new:
    wrapper=read('assets/'+new.replace(':','/models/')+'.json')
    assert wrapper['parent']==old
    assert set(wrapper)=={'parent','textures'}
    assert all(v=='ae2lpt:block/omni_white' for v in wrapper['textures'].values())
   b['apply']['model']=old
   assert a==b, '朝向、状态或旋转偏离原生'
  recipe=read('data/ae2lpt/recipe/omnidirectional_connector.json')
  assert recipe['result']=={'id':'ae2lpt:omnidirectional_connector','count':4}
  ingredients=[x if isinstance(x,str) else x.get('item') or '#'+x['tag'] for x in recipe['ingredients']]
  assert set(ingredients)=={'ae2:wireless_access_point','#c:gems/loop_crystal','ae2:quantum_entangled_singularity'}
  for name in ingredients:
   if name.startswith('ae2:'):assert f"assets/ae2/models/item/{name.split(':')[1]}.json" in z.namelist() or f"assets/ae2/items/{name.split(':')[1]}.json" in z.namelist()
  assert {'type':'ae2lpt:omni_white'} in read('assets/minecraft/atlases/blocks.json')['sources']
  for lang in ['en_us','zh_cn']:
   assert read(f'assets/ae2lpt/lang/{lang}.json')['block.ae2lpt.omnidirectional_connector']
  results.append({'generation':gen,'nativeMultipartIdenticalExceptPalette':True,'recipeThreeIngredientsFourOutput':True,'runtimeSpriteRegistered':True})
print(json.dumps({'passed':True,'results':results},ensure_ascii=False,indent=2))
