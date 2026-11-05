const fs = require('node:fs');
const path = require('node:path');
const source = process.env.GRAPHIFY_CONTEXT_SOURCE || path.resolve(__dirname, '../../graphify/context.py');
const bytes = fs.readFileSync(source);
if (!bytes.toString().includes("PROJECT = 'S15P31E102'")) throw new Error('Unexpected Graphify client source');
fs.mkdirSync(path.resolve(__dirname, '../resources'), {recursive:true});
fs.writeFileSync(path.resolve(__dirname, '../resources/context.py'), bytes);
console.log('Bundled Graphify client from ' + source);
