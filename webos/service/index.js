// Background JS service: the page can't call trackers itself (packaged apps have no CORS origin),
// so tracker requests go through here. Methods are in api.js.
'use strict';

const Service = require('webos-service');
const api = require('./api.js');

const service = new Service('io.github.nindzyago.torrentodrive.webos.service');

Object.keys(api).forEach((name) => {
    service.register(name, (message) => {
        api[name](message.payload || {})
            .then((r) => message.respond(Object.assign({ returnValue: true }, r)))
            .catch((e) => message.respond({ returnValue: false, errorText: e.message }));
    });
});
