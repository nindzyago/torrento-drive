// Methods of the app's service: luna://io.github.nindzyago.torrentodrive.webos.service/<name> on the TV,
// /api/<name> in dev-server.js on a Mac. Each takes the request payload and resolves to the answer.
'use strict';

const rutor = require('./rutor.js');

module.exports = {
    search: (p) => {
        const query = String(p.query || '').trim();
        if (!query) return Promise.reject(new Error('Empty query'));
        return rutor.search(query).then((items) => ({ items }));
    },
    poster: (p) => rutor.poster(String(p.page || '')).then((url) => ({ url })),
    reach: () => rutor.reach().then((millis) => ({ trackers: [{ id: 'rutor', name: 'Rutor', millis }] }),
        () => ({ trackers: [{ id: 'rutor', name: 'Rutor', millis: null }] })),
};
