import { createServer } from 'node:http';

const KINDS = new Set(['LOCATION_SHARE', 'NO_RESPONSE', 'CANCEL', 'ENDED']);
const LABELS = {
  LOCATION_SHARE: '도움 요청 · 위치 공유',
  NO_RESPONSE: '응답 없음',
  CANCEL: '취소',
  ENDED: '종료',
};
const MAX_BODY = 4_096;

const escapeHtml = (text) =>
  String(text).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);

const clock = (millis) =>
  new Date(millis).toLocaleTimeString('ko-KR', { hour12: false, timeZone: 'Asia/Seoul' });

/** 정해진 필드만 남긴다. 다른 값이 와도 저장하거나 보여 주지 않는다. */
export function sanitize(body) {
  if (!body || !KINDS.has(body.kind)) return null;
  const alert = { kind: body.kind, text: String(body.text ?? '').slice(0, 300) };
  if (Number.isFinite(body.lat) && Number.isFinite(body.lng)) {
    alert.lat = body.lat;
    alert.lng = body.lng;
  }
  if (Number.isFinite(body.stoppedAt)) alert.stoppedAt = body.stoppedAt;
  return alert;
}

function renderPage(alerts) {
  const rows = alerts
    .slice()
    .reverse()
    .map((a) => {
      const place = a.lat === undefined ? '위치 없음' : `${a.lat.toFixed(5)}, ${a.lng.toFixed(5)}`;
      const stopped = a.stoppedAt === undefined ? '' : `<p>정차 시각 ${clock(a.stoppedAt)}</p>`;
      return `<li><h2>${LABELS[a.kind]}</h2><p>${escapeHtml(a.text)}</p><p>위치 ${place}</p>${stopped}<p class="t">받은 시각 ${clock(a.receivedAt)}</p></li>`;
    })
    .join('');
  return `<!doctype html><html lang="ko"><head><meta charset="utf-8"><meta http-equiv="refresh" content="2"><title>보호자 화면 (가상)</title>
<style>body{font:20px system-ui,sans-serif;margin:40px;background:#f5f6f8;color:#16181d}h1{font-size:30px;margin:0}
.b{color:#b3261e;font-weight:600;margin:6px 0 24px}ul{list-style:none;padding:0}li{background:#fff;border:1px solid #d9dce1;border-radius:10px;padding:16px 22px;margin-bottom:14px}
h2{font-size:24px;margin:0 0 8px}p{margin:4px 0}.t{color:#5b616b;font-size:17px}</style></head>
<body><h1>보호자 B 화면 (가상)</h1><p class="b">가상 전송입니다. 실제 문자·알림을 보내지 않으며 로컬에서만 동작합니다.</p>
<ul>${rows || '<li>받은 알림이 없습니다.</li>'}</ul></body></html>`;
}

const send = (res, status, type, body) => {
  res.writeHead(status, { 'content-type': type });
  res.end(body);
};

export function createGuardianServer({ log = console.log } = {}) {
  const alerts = [];
  return createServer((req, res) => {
    const finish = (status, type, body) => {
      // 본문은 기록하지 않는다.
      log(`${new Date().toISOString()} ${req.method} ${req.url} ${status}`);
      send(res, status, type, body);
    };
    if (req.method === 'GET' && req.url === '/') return finish(200, 'text/html; charset=utf-8', renderPage(alerts));
    if (req.method === 'GET' && req.url === '/alerts') return finish(200, 'application/json', JSON.stringify(alerts));
    if (req.method === 'POST' && req.url === '/reset') {
      alerts.length = 0;
      return finish(204, 'text/plain', '');
    }
    if (req.method === 'POST' && req.url === '/alerts') {
      let raw = '';
      let tooLarge = false;
      req.setEncoding('utf8');
      req.on('data', (chunk) => {
        raw += chunk;
        if (raw.length > MAX_BODY) tooLarge = true;
      });
      req.on('end', () => {
        if (tooLarge) return finish(413, 'text/plain', '');
        let parsed;
        try {
          parsed = JSON.parse(raw);
        } catch {
          return finish(400, 'text/plain', '');
        }
        const alert = sanitize(parsed);
        if (!alert) return finish(400, 'text/plain', '');
        alerts.push({ ...alert, receivedAt: Date.now() });
        return finish(201, 'application/json', '{}');
      });
      return undefined;
    }
    return finish(404, 'text/plain', '');
  });
}
