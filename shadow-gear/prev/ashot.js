const { chromium } = require('/opt/npm-tools/node_modules/playwright');
const http = require('http'), fs = require('fs'), path = require('path');
const out = process.argv[2], root = process.argv[3], shots = process.argv.slice(4);
const server = http.createServer((q, r) => {
  let u = decodeURIComponent(q.url.split('?')[0]);
  const f = u.startsWith('/prev/') ? path.join(__dirname, u.slice(6)) : path.join(root, u);
  fs.readFile(f, (e, d) => { if (e) { r.writeHead(404); r.end(); } else { r.writeHead(200); r.end(d); } });
}).listen(8767, async () => {
  const b = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const p = await b.newPage({ viewport: { width: 900, height: 900 } });
  p.on('pageerror', e => console.log('pageerror:', e.message));
  await p.goto('http://localhost:8767/prev/armor.html');
  await p.waitForFunction(() => window.ready, null, { timeout: 60000 });
  fs.mkdirSync(out, { recursive: true });
  for (const s of shots) {
    const [name, cp, ct] = s.split(':');
    await p.evaluate(([a, b]) => view(a, b), [cp.split(',').map(Number), ct.split(',').map(Number)]);
    await p.waitForTimeout(150);
    await p.screenshot({ path: path.join(out, name + '.png') });
  }
  await b.close(); server.close();
});
