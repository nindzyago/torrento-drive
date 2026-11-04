// Rutor: no sign-in needed, magnet links are right in the search results.
// Plain Node (no dependencies) so it runs both on the TV and on a Mac for checks.
'use strict';

const https = require('https');

const BASE_URL = 'https://rutor.info';

function get(url, redirects) {
    return new Promise((resolve, reject) => {
        https.get(url, { timeout: 20000 }, (res) => {
            if (res.statusCode >= 300 && res.statusCode < 400 && res.headers.location && (redirects || 0) < 3) {
                res.resume();
                resolve(get(new URL(res.headers.location, url).toString(), (redirects || 0) + 1));
                return;
            }
            if (res.statusCode !== 200) {
                res.resume();
                reject(new Error('Rutor answered HTTP ' + res.statusCode));
                return;
            }
            const chunks = [];
            res.on('data', (c) => chunks.push(c));
            res.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')));
        }).on('timeout', function () { this.destroy(new Error('Rutor did not answer in 20 s')); })
          .on('error', reject);
    });
}

function text(html) {
    return html.replace(/<[^>]*>/g, '')
        .replace(/&nbsp;/g, ' ').replace(/&amp;/g, '&').replace(/&quot;/g, '"')
        .replace(/&#039;/g, "'").replace(/&lt;/g, '<').replace(/&gt;/g, '>')
        .trim();
}

/** Parses a Rutor search page into releases. */
function parse(html) {
    const rows = html.split(/<tr class="(?:gai|tum)">/).slice(1);
    const items = [];
    for (const raw of rows) {
        const row = raw.split('</tr>')[0];
        const magnet = /href="(magnet:[^"]+)"/.exec(row);
        const topic = /<a href="(\/torrent\/[^"]+)">([\s\S]*?)<\/a>/.exec(row);
        if (!magnet || !topic) continue;
        const cells = [];
        const cellRe = /<td align="right">([\s\S]*?)<\/td>/g;
        let m;
        while ((m = cellRe.exec(row)) !== null) cells.push(text(m[1]));
        const seeds = /<span class="green">([\s\S]*?)<\/span>/.exec(row);
        const peers = /<span class="red">([\s\S]*?)<\/span>/.exec(row);
        items.push({
            tracker: 'Rutor',
            title: text(topic[2]),
            size: cells.length ? cells[cells.length - 1] : '',
            seeds: seeds ? parseInt(text(seeds[1]), 10) || 0 : 0,
            peers: peers ? parseInt(text(peers[1]), 10) || 0 : 0,
            magnet: magnet[1].replace(/&amp;/g, '&'),
            page: BASE_URL + topic[1],
        });
    }
    return items;
}

/** /search/<page>/<category>/<mode>/<sort>/<query>; sort 2 = by seeders */
async function search(query) {
    const q = encodeURIComponent(query.trim());
    return parse(await get(BASE_URL + '/search/0/0/100/2/' + q));
}

// the poster is the first image in the torrent description (#details)
async function poster(page) {
    if (!page || page.indexOf(BASE_URL + '/torrent/') !== 0) return null;
    const html = await get(page);
    const details = html.split('id="details"')[1];
    const img = details && /<img[^>]+src="([^"]+)"/.exec(details);
    if (!img) return null;
    return new URL(img[1].replace(/&amp;/g, '&'), page).toString();
}

/** How long the front page takes to open, in ms: the connection check in settings. */
async function reach() {
    const started = Date.now();
    await get(BASE_URL + '/');
    return Date.now() - started;
}

module.exports = { search, parse, poster, reach };
