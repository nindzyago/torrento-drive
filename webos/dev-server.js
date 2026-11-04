// Runs the app in a desktop browser: serves app/ and answers /api/<method> the way the TV service does.
// node dev-server.js  ->  http://localhost:8091
'use strict';

const http = require('http');
const fs = require('fs');
const path = require('path');
const api = require('./service/api.js');

const ROOT = path.join(__dirname, 'app');
const PORT = Number(process.env.PORT) || 8091;
const TYPES = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.png': 'image/png', '.jpg': 'image/jpeg', '.svg': 'image/svg+xml', '.woff2': 'font/woff2', '.json': 'application/json' };

http.createServer((req, res) => {
    const url = new URL(req.url, 'http://localhost');
    const method = url.pathname.startsWith('/api/') && api[url.pathname.slice(5)];
    if (method) {
        const payload = {};
        url.searchParams.forEach((v, k) => { payload[k] = v; });
        method(payload)
            .then((r) => Object.assign({ returnValue: true }, r))
            .catch((e) => ({ returnValue: false, errorText: e.message }))
            .then((r) => { res.setHeader('Content-Type', 'application/json'); res.end(JSON.stringify(r)); });
        return;
    }
    const file = path.join(ROOT, path.normalize(url.pathname === '/' ? '/index.html' : url.pathname));
    if (!file.startsWith(ROOT)) { res.statusCode = 403; res.end(); return; }
    fs.readFile(file, (err, data) => {
        if (err) { res.statusCode = 404; res.end(); return; }
        res.setHeader('Content-Type', TYPES[path.extname(file)] || 'application/octet-stream');
        res.end(data);
    });
}).listen(PORT, () => console.log('Torrento Drive dev: http://localhost:' + PORT));
