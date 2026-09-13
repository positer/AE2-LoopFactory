/* Local Blockbench production exporter. No network access; writes only this repository. */
(function () {
    const root = 'C:/Users/12252/Desktop/Files/Code/Minecraft Multi-Version Mod Workspace';
    let action;
    Plugin.register('ae2lf_assets', {
        title: 'AE2LF Assets', author: 'AE2LF', version: '1.0.0',
        description: 'Build editable Loop Factory models and native pixel textures in Blockbench.',
        variant: 'desktop',
        onload() {
            action = new Action('ae2lf_export_assets', {
                name: 'AE2LF: Export Factory Assets', icon: 'inventory_2',
                click: () => build().catch(error => {
                    require('fs').writeFileSync(root + '/design/loop_factory/blockbench-error.txt', error.stack);
                    Blockbench.showMessageBox({title: 'AE2LF export failed', message: error.message});
                })
            });
            MenuBar.addAction(action, 'tools');
        },
        onunload() { action?.delete(); }
    });
    async function build() {
        const fs = require('fs');
        const path = require('path');
        const report = {tool: 'Blockbench ' + Blockbench.version, generated: new Date().toISOString(), assets: []};
        const write = (file, data) => { fs.mkdirSync(path.dirname(file), {recursive: true}); fs.writeFileSync(file, data); };
        const json = (file, data) => write(file, JSON.stringify(data, null, 2) + '\n');
        const canvas = size => { let c = document.createElement('canvas'); c.width = c.height = size; return c; };
        async function read(file) {
            const img = new Image();
            await new Promise((resolve, reject) => { img.onload = resolve; img.onerror = reject; img.src = 'data:image/png;base64,' + fs.readFileSync(file).toString('base64'); });
            const c = canvas(img.width); c.getContext('2d').drawImage(img, 0, 0); return c;
        }
        function recolor(c, convert) {
            const ctx = c.getContext('2d'), pixels = ctx.getImageData(0, 0, c.width, c.height);
            for (let i = 0; i < pixels.data.length; i += 4) {
                const rgb = convert(...pixels.data.slice(i, i + 3));
                if (pixels.data[i + 3] && rgb) pixels.data.set(rgb, i);
            }
            ctx.putImageData(pixels, 0, 0); return c;
        }
        // Only yellow/orange hues change. Original shell pixels and alpha remain untouched.
        const yellowToGrey = (r,g,b) => r > b + 18 && g > b + 9 ? Array(3).fill(Math.round((r * .25 + g * .65 + b * .1) * .87)) : null;
        const darkerPattern = (r,g,b) => { const v = Math.round((r * .2126 + g * .7152 + b * .0722) * .84); return [v,v,v]; };
        const blueScreen = (r,g,b) => r > g + 15 && r > b + 15 ? [Math.round(b * .65), Math.round(r * .58), r] : null;
        function codeScreen(c) {
            recolor(c, blueScreen);
            const x=c.getContext('2d');x.fillStyle='#101b2a';x.fillRect(5,5,6,6);
            x.fillStyle='#4fb6e4';x.fillRect(5,5,3,1);x.fillRect(9,5,2,1);
            x.fillStyle='#3187b8';x.fillRect(5,7,2,1);x.fillRect(8,7,3,1);
            x.fillStyle='#68c8eb';x.fillRect(5,9,4,1);x.fillRect(10,9,1,1);
            return c;
        }
        function phone() {
            const c = canvas(32), x = c.getContext('2d');
            const rect = (color,a,b,w,h) => { x.fillStyle = color; x.fillRect(a,b,w,h); };
            rect('#20242a',8,2,16,28); rect('#0d1015',7,4,18,24);
            rect('#858b92',9,2,14,1); rect('#5e666e',8,3,1,25); rect('#343b44',23,3,1,25);
            rect('#151b24',9,6,14,20); rect('#263340',10,7,12,1);
            rect('#929ba4',13,4,5,1); rect('#3b92c7',20,4,1,1);
            [[9,3,3],[11,5,2],[13,2,5],[15,4,3],[17,3,4],[19,6,1],[21,2,4],[23,4,2]].forEach(([y,a,b],i) => {
                rect(i%2 ? '#4693c8':'#62c6ef',10,y,a,1); rect('#2879ad',11+a,y,b,1);
            });
            rect('#5d6975',14,28,4,1); rect('#4b525c',25,10,1,4);
            return c;
        }
        function emptyTexture(size) {
            return canvas(size);
        }
        function nativeBackground(masks) {
            const out = canvas(masks[0].width), x = out.getContext('2d');
            for (let y = 0; y < out.height; y++) {
                for (let px = 0; px < out.width; px++) {
                    if (masks.some(mask => mask.getContext('2d').getImageData(px, y, 1, 1).data[3] > 0)) {
                        x.fillStyle = 'rgba(242,242,242,1)';
                        x.fillRect(px, y, 1, 1);
                    }
                }
            }
            return out;
        }
        function ringCoreMask(core) {
            const out = canvas(core.width), x = out.getContext('2d');
            const coreData = core.getContext('2d').getImageData(0, 0, core.width, core.height);
            for (let i = 0; i < coreData.data.length; i += 4) {
                const r = coreData.data[i], g = coreData.data[i + 1], b = coreData.data[i + 2], a = coreData.data[i + 3];
                if (a && r === 186 && g === 255 && b === 244) {
                    x.fillStyle = 'rgba(242,242,242,0.376)';
                    x.fillRect((i / 4) % core.width, Math.floor(i / 4 / core.width), 1, 1);
                } else if (a && r === 98 && g === 245 && b === 223) {
                    x.fillStyle = 'rgba(242,242,242,0.627)';
                    x.fillRect((i / 4) % core.width, Math.floor(i / 4 / core.width), 1, 1);
                } else if (a && r === 22 && g === 143 && b === 138) {
                    x.fillStyle = 'rgba(242,242,242,1)';
                    x.fillRect((i / 4) % core.width, Math.floor(i / 4 / core.width), 1, 1);
                }
            }
            return out;
        }
        function panelModel(gen, names, powered) {
            const layer = (name, tint) => ({
                from: [2, 2, 0], to: [14, 14, 2],
                faces: {north: Object.assign({texture: '#lights' + name, tintindex: tint},
                    powered ? {neoforge_data: {block_light: 15, sky_light: 15}} : {})}
            });
            const textures = {
                lightsBright: 'ae2lightoptimizer:part/' + names[0],
                lightsMedium: 'ae2lightoptimizer:part/' + names[1],
                lightsDark: 'ae2lightoptimizer:part/' + names[2]
            };
            if (!powered) return {parent: 'ae2:part/display_off', textures};
            const on = {
                textures: {...textures},
                elements: [layer('Bright', 3), layer('Medium', 2), layer('Dark', 1)]
            };
            if (gen !== '1.21.1') {
                on.textures.particle = '#lightsMedium';
                on.render_type = 'minecraft:cutout';
            }
            return on;
        }
        function panelItemModel(names) {
            return {
                parent: 'ae2:item/display_base',
                textures: {
                    front: 'ae2:part/pattern_encoding_terminal',
                    front_bright: 'ae2lightoptimizer:part/' + names[0],
                    front_medium: 'ae2lightoptimizer:part/' + names[1],
                    front_dark: 'ae2lightoptimizer:part/' + names[2]
                }
            };
        }
        function panelItemDescriptor() {
            return {
                model: {
                    type: 'minecraft:model',
                    model: 'ae2lightoptimizer:item/loop_factory_pattern_encoding_panel',
                    tints: [
                        {type: 'minecraft:constant', value: -1},
                        {type: 'ae2:color', color: 'fluix', variant: 'dark'},
                        {type: 'ae2:color', color: 'fluix', variant: 'medium'},
                        {type: 'ae2:color', color: 'fluix', variant: 'bright'},
                        {type: 'ae2:color', color: 'fluix', variant: 'medium_bright'}
                    ]
                }
            };
        }
        async function project(gen, id, faces, shape, assets) {
            newProject(Formats.java_block);
            Project.name = id; Project.texture_width = Project.texture_height = 16;
            const textures = {};
            for (const [name, source] of Object.entries(faces)) {
                const category = shape === 'item' ? 'item' : shape === 'panel' ? 'part' : 'block';
                const png = source.toDataURL();
                write(assets + '/textures/' + category + '/' + name + '.png', Buffer.from(png.split(',')[1], 'base64'));
                const tex = new Texture({name:name+'.png', namespace:'ae2lightoptimizer', folder:category});
                await new Promise(resolve => { tex.load_callback = resolve; tex.fromDataURL(png).add(false); });
                textures[name]=tex;
            }
            const names = Object.keys(textures);
            if (shape === 'panel') {
                const off = panelModel(gen, names, false), on = panelModel(gen, names, true);
                json(assets + '/models/part/loop_factory_panel_off.json', off);
                json(assets + '/models/part/loop_factory_panel_on.json', on);
                json(assets + '/models/item/' + id + '.json', panelItemModel(names));
                if (gen !== '1.21.1') {
                    json(assets + '/items/' + id + '.json', panelItemDescriptor());
                    json(assets + '/ae2/parts/' + id + '.json', {
                        model: {
                            type: 'ae2:composite',
                            models: [
                                {type: 'ae2:model', model: 'ae2:part/display_base'},
                                {type: 'ae2:status_indicator',
                                    active: 'ae2lightoptimizer:part/loop_factory_panel_on',
                                    powered: 'ae2lightoptimizer:part/loop_factory_panel_on',
                                    unpowered: 'ae2lightoptimizer:part/loop_factory_panel_off'},
                                {type: 'ae2:status_indicator',
                                    active: 'ae2:part/display_status_has_channel',
                                    powered: 'ae2:part/display_status_on',
                                    unpowered: 'ae2:part/display_status_off'}
                            ]
                        }
                    });
                }
                const cube = new Cube({name: id, from: [0, 0, 0], to: [16, 16, 2]}).init();
                for (const [face, data] of Object.entries(cube.faces)) {
                    data.extend({uv: [0, 0, 16, 16], texture: textures[names[0]].uuid});
                }
                cube.select(); Canvas.updateAll();
                const output = root + '/design/loop_factory/' + gen + '/' + id + '.bbmodel';
                write(output, Codecs.project.compile());
                Project.save_path = output; Project.saved = true;
                report.assets.push({gen, id, shape, textures: names, project: output});
                return;
            }
            const cube = new Cube({name:id, from: shape === 'item' ? [0,0,7.9] : [0,0,0], to:shape==='panel'?[16,16,2]:shape==='item'?[16,16,8.1]:[16,16,16]}).init();
            for (const [face, data] of Object.entries(cube.faces)) {
                const key = id === 'loop_factory_pattern_provider' ? (face === 'up' ? names[0] : face === 'down' ? names[2] : names[1]) : face === 'north' || (id === 'loop_factory_network_terminal' && !['up','down'].includes(face)) ? names[0] : (names[1] || names[0]);
                data.extend({uv:[0,0,16,16],texture:textures[key].uuid});
            }
            cube.select();Canvas.updateAll();
            const model = Codecs.java_block.compile({raw:true});
            const kind = shape === 'item' ? 'item' : 'block';
            if (shape !== 'item') { model.parent='minecraft:block/block'; model.textures.particle='ae2lightoptimizer:'+(shape==='panel'?'part':'block')+'/'+names[0]; }
            delete model.format_version;
            if (shape === 'item') {
                model.parent='minecraft:item/generated'; delete model.elements;
                model.textures={layer0:'ae2lightoptimizer:item/'+names[0]};
            }
            json(assets+'/models/'+kind+'/'+id+'.json',model);
            if (shape !== 'item') json(assets+'/models/item/'+id+'.json',{parent:'ae2lightoptimizer:block/'+id});
            if (gen !== '1.21.1') json(assets+'/items/'+id+'.json',{model:{type:'minecraft:model',model:'ae2lightoptimizer:item/'+id}});
            if (shape === 'block') {
                const variants={};
                for(const [facing,x,y] of (id === 'loop_factory_pattern_provider' ? [['up',0,0],['down',180,0],['north',90,0],['east',90,90],['south',90,180],['west',90,270]] : [['north',0,0],['east',0,90],['south',0,180],['west',0,270],['up',270,0],['down',90,0]]))
                    variants['facing='+facing]={model:'ae2lightoptimizer:block/'+id,x,y};
                json(assets+'/blockstates/'+id+'.json',{variants});
            }
            const output=root+'/design/loop_factory/'+gen+'/'+id+'.bbmodel';
            write(output,Codecs.project.compile()); Project.save_path=output;Project.saved=true;
            report.assets.push({gen,id,shape,textures:names,project:output});
        }
        for (const gen of ['1.21.1','26.1.2']) {
            const ref=root+'/design/loop_factory/references/'+gen;
            const common=root+'/design/loop_factory/references';
            const assets=root+'/versions/neoforge-'+gen+'/src/main/resources/assets/ae2lightoptimizer';
            await project(gen,'loop_factory_pattern_provider',{
                loop_factory_provider_front:recolor(await read(ref+'/pattern_provider_alternate_front.png'),yellowToGrey),
                loop_factory_provider_side:recolor(await read(ref+'/pattern_provider_alternate_arrow.png'),yellowToGrey),
                loop_factory_provider_back:recolor(await read(ref+'/pattern_provider_alternate.png'),yellowToGrey)
            },'block',assets);
            await project(gen,'loop_factory_network_terminal',{
                loop_factory_terminal:codeScreen(await read(common+'/sfm_manager_side.png')),
                loop_factory_terminal_top:await read(common+'/sfm_manager_top.png')
            },'block',assets);
            await project(gen,'loop_factory_interface_cable',{
                loop_factory_cable:recolor(await read(common+'/sfm_cable.png'),(r,g,b)=>[r,g,b].map(v=>Math.min(244,Math.round(156+v*1.2))))
            },'block',assets);
            await project(gen,'loop_factory_pattern',{loop_factory_pattern:recolor(await read(ref+'/blank_pattern.png'),darkerPattern)},'item',assets);
            await project(gen,'handheld_loop_factory_encoder',{handheld_loop_factory_encoder:phone()},'item',assets);
            const ringCore = await read(ref+'/recipe_ring_solver_terminal_connected.png');
            const nativeMasks = [
                await read(ref+'/pattern_encoding_terminal_bright.png'),
                await read(ref+'/pattern_encoding_terminal_medium.png'),
                await read(ref+'/pattern_encoding_terminal_dark.png')
            ];
            await project(gen,'loop_factory_pattern_encoding_panel',{
                loop_factory_pattern_encoding_panel_bright: nativeBackground(nativeMasks),
                loop_factory_pattern_encoding_panel_medium: emptyTexture(16),
                loop_factory_pattern_encoding_panel_dark: ringCoreMask(ringCore)
            },'panel',assets);
        }
        json(root+'/design/loop_factory/blockbench-export.json',report);
        Blockbench.showMessageBox({title:'AE2LF export complete',message:'Both generations exported. Panel rendering uses only AE2 native display_base/item models with the Loop Factory core masks.'});
    }
})();
