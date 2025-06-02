
const fs = require('fs');
const path = require('path');

// --- Step 1: Extract initial scene UUID from builder.json ---
const builderPath = path.join(__dirname, 'settings', 'builder.json');
if (!fs.existsSync(builderPath)) {
    console.error('❌ Cannot find settings/builder.json');
    process.exit(1);
}
const builderSettings = JSON.parse(fs.readFileSync(builderPath, 'utf8'));
const initialUUID = builderSettings.startScene;
if (!initialUUID) {
    console.error('❌ startScene UUID not found in builder.json');
    process.exit(1);
}

// --- Step 2: Find the corresponding .fire file from UUID ---
function findFireFileByUUID(dir, uuid) {
    const files = fs.readdirSync(dir);
    for (const file of files) {
        const fullPath = path.join(dir, file);
        const stat = fs.statSync(fullPath);
        if (stat.isDirectory()) {
            const found = findFireFileByUUID(fullPath, uuid);
            if (found) return found;
        } else if (file.endsWith('.fire.meta')) {
            try {
                const meta = JSON.parse(fs.readFileSync(fullPath, 'utf8'));
                if (meta.uuid === uuid) {
                    const firePath = fullPath.replace(/\.meta$/, '');
                    if (fs.existsSync(firePath)) return firePath;
                }
            } catch (e) {}
        }
    }
    return null;
}

const firePath = findFireFileByUUID(path.join(__dirname, 'assets'), initialUUID);
if (!firePath) {
    console.error("❌ Could not resolve UUID to .fire file:", initialUUID);
    process.exit(1);
}
console.log("📌 Found launch scene:", firePath);
const launchScene = JSON.parse(fs.readFileSync(firePath, 'utf8'));

// --- Step 3: Get component type from Checker node in scene ending with 's' ---
const loadSceneDir = path.join(__dirname, 'assets', 'LoadScene');
const loadSceneFile = fs.readdirSync(loadSceneDir).find(f => f.endsWith('s.fire'));
if (!loadSceneFile) {
    console.error('❌ No scene ending with "s" found.');
    process.exit(1);
}
const checkerScene = JSON.parse(fs.readFileSync(path.join(loadSceneDir, loadSceneFile), 'utf8'));

let checkerType = null;
for (let i = 0; i < checkerScene.length; i++) {
    const node = checkerScene[i];
    if (node.__type__ === 'cc.Node' && node._name === 'Checker') {
        if (node._components && node._components.length > 0) {
            const comp = checkerScene[node._components[0].__id__];
            checkerType = comp.__type__;
            break;
        }
    }
}
if (!checkerType) {
    console.error("❌ Failed to find Checker node with component.");
    process.exit(1);
}
console.log("🔧 Extracted component type:", checkerType);

// --- Step 4: Inject node with component into Canvas of launch scene ---
let canvasIdx = null;
for (let i = 0; i < launchScene.length; i++) {
    const node = launchScene[i];
    if (node.__type__ === 'cc.Node' && node._name === 'Canvas') {
        canvasIdx = i;
        break;
    }
}
if (canvasIdx === null) {
    console.error("❌ Canvas not found in launch scene.");
    process.exit(1);
}

const nodeIdx = launchScene.length;
const compIdx = nodeIdx + 1;
const injectedNode = {
    "__type__": "cc.Node",
    "_name": "InjectedNode",
    "_objFlags": 0,
    "_parent": { "__id__": canvasIdx },
    "_children": [],
    "_active": true,
    "_level": 1,
    "_components": [{ "__id__": compIdx }],
    "_prefab": null,
    "_opacity": 255,
    "_color": { "__type__": "cc.Color", "r": 255, "g": 255, "b": 255, "a": 255 },
    "_contentSize": { "__type__": "cc.Size", "width": 0, "height": 0 },
    "_anchorPoint": { "__type__": "cc.Vec2", "x": 0.5, "y": 0.5 },
    "_position": { "__type__": "cc.Vec3", "x": 0, "y": 0, "z": 379.31913 },
    "_scale": { "__type__": "cc.Vec3", "x": 1, "y": 1, "z": 1 },
    "_eulerAngles": { "__type__": "cc.Vec3", "x": 0, "y": 0, "z": 0 },
    "_skewX": 0,
    "_skewY": 0,
    "_is3DNode": false,
    "groupIndex": 0,
    "_id": "auto-gen-" + Math.random().toString(36).substr(2, 9)
};
const newComponent = {
    "__type__": checkerType,
    "_name": "",
    "_objFlags": 0,
    "node": { "__id__": nodeIdx },
    "_enabled": true
};

launchScene.push(injectedNode);
launchScene.push(newComponent);
launchScene[canvasIdx]._children = launchScene[canvasIdx]._children || [];
launchScene[canvasIdx]._children.push({ "__id__": nodeIdx });

fs.writeFileSync(firePath, JSON.stringify(launchScene, null, 2));
console.log("✅ Injected node with component added to launch scene.");
