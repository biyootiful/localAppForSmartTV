#!/usr/bin/env node
// Evaluates a JavaScript expression in the app's current page over the Chrome DevTools
// protocol, the same channel chrome://inspect uses. For checking what the page sees.
//
// Usage: tools/page-eval.mjs <adb-serial> '<expression>'      or   ... @file.js
//   e.g. tools/page-eval.mjs <tv-ip>:5555 'navigator.userAgent'
import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';

const [serial, source] = process.argv.slice(2);
if (!serial || !source) {
    console.error("usage: page-eval.mjs <adb-serial> '<expression>' | @file.js");
    process.exit(2);
}
const expression = source.startsWith('@') ? readFileSync(source.slice(1), 'utf8') : source;
const PORT = 9333;
const adb = (...args) => execFileSync('adb', ['-s', serial, ...args], { encoding: 'utf8' }).trim();

const pid = adb('shell', 'pidof', 'local.tv.streamshell');
if (!pid) {
    console.error('StreamShell is not running on ' + serial);
    process.exit(1);
}
adb('forward', `tcp:${PORT}`, `localabstract:webview_devtools_remote_${pid}`);
try {
    const pages = await (await fetch(`http://127.0.0.1:${PORT}/json`)).json();
    const page = pages.find(p => p.type === 'page');
    if (!page) throw new Error('no page found');
    const ws = new WebSocket(page.webSocketDebuggerUrl);
    const result = await new Promise((resolve, reject) => {
        ws.onopen = () => ws.send(JSON.stringify({
            id: 1,
            method: 'Runtime.evaluate',
            params: { expression, returnByValue: true, awaitPromise: true },
        }));
        ws.onmessage = event => {
            const message = JSON.parse(event.data);
            if (message.id === 1) resolve(message.result);
        };
        ws.onerror = () => reject(new Error('DevTools connection failed'));
        setTimeout(() => reject(new Error('timed out')), 15000);
    });
    ws.close();
    console.log('page: ' + page.url);
    if (result.exceptionDetails) {
        console.log('threw: ' + (result.exceptionDetails.exception?.description ?? result.exceptionDetails.text));
        process.exitCode = 1;
    } else {
        console.log(JSON.stringify(result.result.value, null, 2));
    }
} finally {
    adb('forward', '--remove', `tcp:${PORT}`);
}
