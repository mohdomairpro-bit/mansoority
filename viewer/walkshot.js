const { chromium } = require('/opt/npm-tools/node_modules/playwright');
const http = require('http'), fs = require('fs'), path = require('path');
const root = __dirname, out = process.argv[2], mode = process.argv[3] || 'walk';
const server = http.createServer((q, r) => { const f = path.join(root, q.url.split('?')[0].slice(1) || 'index.html');
  fs.readFile(f, (e, d) => { if (e) { r.writeHead(404); r.end(); } else { r.writeHead(200, f.endsWith('.html') ? { 'Content-Type': 'text/html; charset=utf-8' } : {}); r.end(d); } }); }).listen(8767, async () => {
  const b = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const p = await b.newPage({ viewport: { width: 900, height: 700 } });
  p.on('pageerror', e => console.log('pageerror:', e.message));
  p.on('console', m => { if (m.type() === 'error') console.log('console:', m.text()); });
  await p.route('https://cdnjs.cloudflare.com/**', r => r.fulfill({ body: fs.readFileSync('/tmp/three/three.min.js'), contentType: 'text/javascript' }));
  await p.route('https://cdn.jsdelivr.net/**', r => r.fulfill({ body: fs.readFileSync('/tmp/three/OrbitControls.js'), contentType: 'text/javascript' }));
  await p.route('https://fonts.googleapis.com/**', r => r.fulfill({ body: '', contentType: 'text/css' }));
  await p.goto('http://localhost:8767/index.html');
  await p.waitForFunction(() => typeof M !== 'undefined' && M && A, null, { timeout: 60000 });
  await p.evaluate((mode) => { setWalk(mode); view.speed = 1; }, mode);
  fs.mkdirSync(out, { recursive: true });
  for (let i = 0; i < 6; i++) {
    await p.waitForTimeout(1500);
    await p.evaluate(() => { const c = Math.cos(walk.yaw), s = Math.sin(walk.yaw);
      camera.position.set(walk.x + c * 5.5, walk.y + 1.6, walk.z - s * 5.5); controls.target.set(walk.x, walk.y + 0.9, walk.z); controls.update(); });
    await p.waitForTimeout(150);
    await p.screenshot({ path: path.join(out, `${mode}_${i}.png`), clip: { x: 0, y: 0, width: 580, height: 700 }, timeout: 60000 });
    console.log('legs', await p.evaluate(() => JSON.stringify({ step: walk.legs && walk.legs.step, t: view.t.toFixed(1), y: walk.y.toFixed(2) })));
  }
  await b.close(); server.close();
});
