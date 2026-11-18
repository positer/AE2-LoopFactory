"""生成全向连接器的原生模型引用、配置页、配方和说明；不修改依赖图片。"""
from pathlib import Path
import json
import zipfile

ROOT = Path(__file__).resolve().parents[1]
MOD = 'ae2lpt'
ID = 'omnidirectional_connector'

def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes((json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode('utf-8'))

LABELS = {
    'zh_cn': {'title': '全向连接器', 'create': '创建或加入同名连接频道', 'previous': '上一页', 'next': '下一页',
              'unlink': '断开此连接器', 'name': '连接频道名称', 'none': '未连接', 'selected': '当前：%s',
              'page': '第 %s / %s 页', 'invalid': '名称须为 1–48 个可显示字符', 'links': '全服连接频道（括号内为已加载端点数）'},
    'en_us': {'title': 'Omnidirectional Connector', 'create': 'Create or join a named link', 'previous': 'Previous page', 'next': 'Next page',
              'unlink': 'Unlink this connector', 'name': 'Link name', 'none': 'Not linked', 'selected': 'Current: %s',
              'page': 'Page %s / %s', 'invalid': 'Use 1–48 visible characters', 'links': 'Server links (loaded endpoints in brackets)'}
}
for generation in ['1.21.1', '26.1.2']:
    root = ROOT / f'versions/neoforge-{generation}/src/main/resources'
    properties = (ROOT / f'versions/neoforge-{generation}/gradle.properties').read_text(encoding='utf-8')
    version = next(line.split('=', 1)[1].strip() for line in properties.splitlines() if line.startswith('ae2_version='))
    jar = next((ROOT / f'.gradle-user-home/{generation}/caches/modules-2/files-2.1/org.appliedenergistics/appliedenergistics2/{version}').rglob('appliedenergistics2-*.jar'))
    with zipfile.ZipFile(jar) as native:
        state = json.loads(native.read('assets/ae2/blockstates/wireless_access_point.json'))
    for part in state['multipart']:
        model = part['apply']['model']
        suffix = model.split('wireless_access_point_', 1)[1]
        part['apply']['model'] = f'{MOD}:block/omni_connector_{suffix}'
        write(root / f'assets/{MOD}/models/block/omni_connector_{suffix}.json', {
            'parent': model, 'textures': ({'base': f'{MOD}:block/omni_white'} if model.endswith('_chassis') else {'torch': f'{MOD}:block/omni_white', '0': f'{MOD}:block/omni_white'})})
    write(root / f'assets/{MOD}/blockstates/{ID}.json', state)
    write(root / f'assets/{MOD}/models/item/{ID}.json', {'parent': 'ae2:item/wireless_access_point',
          'textures': {'base': f'{MOD}:block/omni_white', 'particle': f'{MOD}:block/omni_white'}})
    if generation == '26.1.2':
        write(root / f'assets/{MOD}/items/{ID}.json', {'model': {'type': 'minecraft:model', 'model': f'{MOD}:item/{ID}'}})
    atlas_path = root / 'assets/minecraft/atlases/blocks.json'
    atlas = json.loads(atlas_path.read_text(encoding='utf-8')) if atlas_path.exists() else {'sources': []}
    source = {'type': f'{MOD}:omni_white'}
    if source not in atlas['sources']: atlas['sources'].append(source)
    write(atlas_path, atlas)
    ingredient = (lambda item: {'item': item}) if generation == '1.21.1' else (lambda item: item)
    loop_crystals = {'tag': 'c:gems/loop_crystal'} if generation == '1.21.1' else '#c:gems/loop_crystal'
    write(root / f'data/{MOD}/recipe/{ID}.json', {'type': 'minecraft:crafting_shapeless', 'category': 'misc',
          'ingredients': [ingredient('ae2:wireless_access_point'), loop_crystals, ingredient('ae2:quantum_entangled_singularity')],
          'result': {'id': f'{MOD}:{ID}', 'count': 4}})
    write(root / f'data/{MOD}/loot_table/blocks/{ID}.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1,
          'entries': [{'type': 'minecraft:item', 'name': f'{MOD}:{ID}'}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    tag = root / 'data/minecraft/tags/block/mineable/pickaxe.json'
    values = json.loads(tag.read_text(encoding='utf-8'))
    if f'{MOD}:{ID}' not in values['values']: values['values'].append(f'{MOD}:{ID}')
    write(tag, values)
    for locale, labels in LABELS.items():
        path = root / f'assets/{MOD}/lang/{locale}.json'
        lang = json.loads(path.read_text(encoding='utf-8'))
        lang[f'block.{MOD}.{ID}'] = labels['title']
        lang.update({f'gui.{MOD}.omni.{key}': value for key, value in labels.items()})
        write(path, lang)
    screen = {'includes': ['common/common.json'], 'generatedBackground': {'width': 246, 'height': 224},
              'text': {'dialog_title': {'text': {'translate': f'block.{MOD}.{ID}'}, 'position': {'left': 8, 'top': 6}},
                       'selected': {'position': {'left': 8, 'top': 22}},
                       'links': {'text': {'translate': f'gui.{MOD}.omni.links'}, 'position': {'left': 8, 'top': 62}},
                       'page': {'position': {'left': 50, 'top': 204}}},
              'widgets': {'createLink': {'left': 224, 'top': 40}, 'previous': {'left': 8, 'top': 200},
                          'next': {'left': 28, 'top': 200}, 'unlink': {'left': 224, 'top': 200}}}
    for index in range(6): screen['widgets'][f'channel{index}'] = {'left': 8, 'top': 74 + 20 * index, 'width': 230, 'height': 18}
    write(root / 'assets/ae2/screens/ae2lightoptimizer_omni_connector.json', screen)
    print(f'Generated {generation}: native wireless models, runtime white sprite, configuration and shapeless recipe')
