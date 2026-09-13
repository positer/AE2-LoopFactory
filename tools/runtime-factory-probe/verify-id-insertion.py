"""Validate the native input report against independently reconstructed test expectations."""
import argparse
import hashlib
import json
from pathlib import Path


def require(ok, reason):
    if not ok:
        raise AssertionError(reason)


def length(text):
    return len(text.encode('utf-16-le')) // 2


def digest(text):
    return hashlib.sha256(text.encode('utf-8')).hexdigest()


def plan(mode):
    seed = '# alpha OMEGA\ndone'
    energy = 'ae2lightoptimizer:portable_1k_loop_storage_cell'
    rows = []
    def add(name, token, code=seed, start=8, end=8):
        rows.append((name, token, code, start, end))
    for name in ('ordinary-left', 'ordinary-right'):
        add(name, 'minecraft:stone')
    add('selection-replaced', 'minecraft:stone', start=2, end=7)
    add('unicode-caret', 'minecraft:stone', '# 中😀 before AFTER\ndone', 13, 13)
    for name, token in (
        ('water-container-left', 'minecraft:water_bucket'),
        ('water-container-right', 'minecraft:water'),
        ('lava-container-right', 'minecraft:lava'),
        ('empty-container-left', 'minecraft:bucket'), ('empty-container-right', ''),
        ('empty-fe-left', energy), ('empty-fe-right', ''),
        ('full-fe-left', energy), ('full-fe-right', 'neoforge::fe'),
    ):
        add(name, token)
    exact = '#' + 'x' * (65536 - len('minecraft:stone') - 1)
    add('exact-length', 'minecraft:stone', exact, 2, 2)
    add('length-reject-atomic', 'minecraft:stone', exact + 'x', 2, 2)
    add('full-length-selection', 'minecraft:stone', '#' + 'x' * 65535, 2, 40)
    for name in ('empty-carried-main-encoder', 'empty-carried-main-stone', 'empty-carried-offhand-water'):
        add(name, '', start=13, end=13)
    add('empty-shulker-right', '')
    add('filled-shulker-left', 'minecraft:shulker_box')
    add('filled-shulker-right', 'minecraft:stone & minecraft:gold_ingot')
    add('ae-cell-contents-right', 'minecraft:iron_ingot')
    if mode != 'none':
        add('viewer-item', 'minecraft:stone')
        add('viewer-selection', 'minecraft:stone', start=2, end=7)
        add('viewer-outside-rejected', 'minecraft:stone')
        add('viewer-fluid', 'minecraft:water')
    return rows


def validate(directory, generation, mode):
    directory = Path(directory).resolve()
    data = json.loads((directory / 'item-id-insertion-report.json').read_text(encoding='utf-8'))
    require(data['status'] == 'passed' and not data['failure'], 'native audit did not pass')
    require(data['generation'] == generation and data['viewerMode'] == mode, 'wrong generation/viewer')
    require(data['loadedJei'] is (mode == 'jei') and data['loadedEmi'] is (mode == 'emi'), 'viewer isolation')
    require(data['syntheticNativeEvents'] is True and data['inputMode'] == 'synthetic_native_gui_events', 'input attribution')
    require(data['dispatchMethod'] == 'native MouseHandler button callbacks plus pinned NeoForge pre/screen/post drag chain', 'unexpected native dispatch')
    require(data['guiScaleRestored'] is True, 'native GUI scale was not restored')
    expected = plan(mode)
    names = [entry[0] for entry in expected]
    require(data['planned'] == names and [row['id'] for row in data['cases']] == names, 'missing, duplicated, or reordered cases')
    require(data['index'] == len(names), 'incomplete case index')
    expected_screens = []
    for row, (name, token, code, start, end) in zip(data['cases'], expected):
        require(row['passed'] is True and row['syntheticNativeInput'] is True and row['saveRoundTrip'] is True
                and row['characterLimitRestored'] is True, name + ': input/save/source limit failed')
        require(row['requestedToken'] == token and row['initialLengthUtf16'] == length(code)
                and row['initialCodeSha256'] == digest(code) and row['initialSelection'] == [start, end], name + ': incorrect fixture')
        native_caret = name.startswith('empty-carried-')
        is_viewer = name.startswith('viewer-')
        changes = bool(token) and length(code) - (end - start) + len(token) <= 65536 and name != 'viewer-outside-rejected'
        utf16 = code.encode('utf-16-le')
        result = (utf16[:start * 2] + token.encode('utf-16-le') + utf16[end * 2:]).decode('utf-16-le') if changes else code
        caret = start + len(token) if changes else end
        require(row['atomicInsertionAccepted'] is changes, name + ': atomic decision mismatch')
        require(row['actualCodeSha256'] == row['expectedCodeSha256'] == digest(result)
                and row['actualLengthUtf16'] == row['expectedLengthUtf16'] == length(result), name + ': complete text mismatch')
        require(row['expectedCaret'] == caret, name + ': expected caret mismatch')
        if native_caret:
            require(0 < row['actualCaret'] < length(code) and row['actualCaret'] != end
                    and row['actualSelection'] == [row['actualCaret']] * 2, name + ': native caret did not move')
        else:
            require(row['actualCaret'] == caret and row['actualSelection'] == ([caret, caret] if changes else [start, end]), name + ': caret/selection mismatch')
        require(row['events'] == {'press': 1, 'release': 1, 'drag': 2 if is_viewer else 1}, name + ': duplicate/missing input events')
        require(row['viewer'] == (mode if is_viewer else 'none'), name + ': wrong viewer route')
        for side in ('client', 'server'):
            before = row[side + 'Before']
            require(before == row[side + 'After'], name + ': ' + side + ' resources changed')
            require(len(before['inventory']) >= 36 and len(before['menuSlots']) >= 37, name + ': incomplete inventory evidence')
            if native_caret or is_viewer:
                require(before['carried'] == 'empty', name + ': unexpected carried stack')
            else:
                require(len(before['carried']) == 64, name + ': absent native carried stack')
            if native_caret:
                require(before['off' if 'offhand' in name else 'main'] != 'empty', name + ': missing hand fixture')
            cap = before['capability']
            if name.startswith(('empty-fe-', 'full-fe-')):
                require(cap['energyCapability'] is True and cap['energyCapacity'] > 0
                        and cap['energyStored'] == (cap['energyCapacity'] if name.startswith('full-') else 0), name + ': invalid FE fixture')
            if name.startswith(('water-container-', 'lava-container-', 'empty-container-')):
                require(cap['fluidCapability'] is True, name + ': missing fluid capability')
                positive = [tank for tank in cap['tanks'] if tank['amount'] > 0]
                if name.startswith('empty-'):
                    require(not positive, name + ': nonempty container')
                else:
                    require(len(positive) == 1 and positive[0]['id'] == ('minecraft:water' if name.startswith('water-') else 'minecraft:lava'), name + ': incorrect fluid')
            if 'shulker' in name:
                require(row['nativeContainerFixtureValidated'] is True and cap['itemCapability'] is True
                        and len(cap['itemSlots']) == 27, name + ': invalid native shulker')
                positive = [slot for slot in cap['itemSlots'] if slot['amount'] > 0]
                contents = [] if name.startswith('empty-') else [
                    {'index': 0, 'id': 'minecraft:stone', 'amount': 9},
                    {'index': 1, 'id': 'minecraft:stone', 'amount': 13},
                    {'index': 2, 'id': 'minecraft:gold_ingot', 'amount': 5}]
                require(positive == contents, name + ': native duplicate content fixture differs')
            if name == 'ae-cell-contents-right':
                require(row['nativeContainerFixtureValidated'] is True and cap['aeCellInventory'] is True
                        and len(cap['aeCellContents']) == 1, name + ': invalid native AE cell')
                content = cap['aeCellContents'][0]
                require(content['id'] == 'minecraft:iron_ingot' and content['amount'] == 37
                        and content['keyType'] == 'ae2:i', name + ': native AE cell contents differ')
        if is_viewer:
            require(row['actualViewerSource']['registryId'] == token and len(row['dropArea']) == 4 and 'viewerDragObserved' in row, name + ': missing native viewer observation')
        expected_screens.append(f'id-insertion-screenshots/{name}-before.png')
        if is_viewer:
            expected_screens.append(f'id-insertion-screenshots/{name}-drag.png')
        expected_screens.append(f'id-insertion-screenshots/{name}-after.png')
    require(data['screenshots'] == expected_screens, 'missing or duplicate frame captures')
    for relative in expected_screens:
        path = (directory / relative).resolve()
        require(directory in path.parents and path.read_bytes()[:8] == b'\x89PNG\r\n\x1a\n', 'invalid framebuffer ' + relative)
    return {'status': 'passed', 'generation': generation, 'viewer': mode, 'cases': len(names), 'framebuffers': len(expected_screens)}


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('directory', type=Path)
    parser.add_argument('--generation', required=True, choices=('1.21.1', '26.1.2'))
    parser.add_argument('--viewer', required=True, choices=('none', 'jei', 'emi'))
    args = parser.parse_args()
    print(json.dumps(validate(args.directory, args.generation, args.viewer), indent=2))
