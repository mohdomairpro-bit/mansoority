// screenshots of the viewer for review: node shoot.js <outdir> clip:frame:view ...
const { chromium } = require('/opt/npm-tools/node_modules/playwright');
const http = require('http'), fs = require('fs'), path = require('path');
const root = __dirname, out = process.argv[2];
const shots = process.argv.slice(3);
const server = http.createServer((q, r) => {
  const f = path.join(root, decodeURIComponent(q.url.split('?')[0]).replace(/^\//, '') || 'index.html');
  fs.readFile(f, (e, d) => { if (e) { r.writeHead(404); r.end(); } else { r.writeHead(200, f.endsWith('.html') ? { 'Content-Type': 'text/html; charset=utf-8' } : {}); r.end(d); } });
}).listen(8765, async () => {
  const b = await chromium.launch({ args: ['--use-gl=angle', '--use-angle=swiftshader', '--enable-unsafe-swiftshader'] });
  const p = await b.newPage({ viewport: { width: 1100, height: 900 } });
  p.on('console', m => { if (m.type() === 'error') console.log('console:', m.text()); });
  p.on('pageerror', e => console.log('pageerror:', e.message));
  await p.route('https://cdnjs.cloudflare.com/**', r => r.fulfill({ body: fs.readFileSync('/tmp/three/three.min.js'), contentType: 'text/javascript' }));
  await p.route('https://cdn.jsdelivr.net/**', r => r.fulfill({ body: fs.readFileSync('/tmp/three/OrbitControls.js'), contentType: 'text/javascript' }));
  await p.route('https://fonts.googleapis.com/**', r => r.fulfill({ body: '', contentType: 'text/css' }));
  await p.goto('http://localhost:8765/index.html');
  await p.waitForFunction(() => typeof M !== 'undefined' && M && A, null, { timeout: 60000 });
  fs.mkdirSync(out, { recursive: true });
  for (const s of shots) {
    const [clip, fr, v, jaw, cam] = s.split(':');
    await p.evaluate(([clip, fr, v, jaw, cam]) => {
      pick(clip); view.fade = 1; view.playing = false; view.t = parseFloat(fr); setView(v || '34');
      view.jaw = jaw ? parseFloat(jaw) : 0.03; view.bones = false;
      if (cam) { const c = cam.split(',').map(Number); camera.position.set(c[0], c[1], c[2]); controls.target.set(c[3], c[4], c[5]); controls.update(); }
    }, [clip, fr, v, jaw, cam]);
    await p.waitForTimeout(400);
    await p.locator('#stage').screenshot({ path: path.join(out, `${clip}_${fr}_${v || '34'}${jaw ? '_j' + jaw : ''}${cam ? '_cam' + shots.indexOf(s) : ''}.png`) });
  }
  if (shots.length === 0) await p.screenshot({ path: path.join(out, 'page.png') });
  await b.close(); server.close();
});
