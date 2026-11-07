import assert from 'node:assert/strict';
import { after, before, beforeEach, test } from 'node:test';
import { createGuardianServer, sanitize } from '../src/server.js';

let server;
let base;
const logs = [];

before(async () => {
  server = createGuardianServer({ log: (line) => logs.push(line) });
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  base = `http://127.0.0.1:${server.address().port}`;
});
after(() => server.close());
beforeEach(() => fetch(`${base}/reset`, { method: 'POST' }));

const post = (body) =>
  fetch(`${base}/alerts`, { method: 'POST', body: typeof body === 'string' ? body : JSON.stringify(body) });

test('알림을 저장하고 조회한다', async () => {
  const res = await post({ kind: 'NO_RESPONSE', lat: 37.5, lng: 127, stoppedAt: 1000, text: '자동 메시지이며 진단이 아닙니다.' });
  assert.equal(res.status, 201);
  const list = await (await fetch(`${base}/alerts`)).json();
  assert.equal(list.length, 1);
  assert.equal(list[0].kind, 'NO_RESPONSE');
  assert.equal(list[0].lat, 37.5);
  assert.ok(list[0].receivedAt > 0);
});

test('정해진 필드 밖의 값은 버린다', async () => {
  await post({ kind: 'CANCEL', text: 't', history: '병력', medication: '약', phone: '000' });
  const [alert] = await (await fetch(`${base}/alerts`)).json();
  assert.deepEqual(Object.keys(alert).sort(), ['kind', 'receivedAt', 'text']);
});

test('sanitize는 모르는 종류와 숫자가 아닌 좌표를 거른다', () => {
  assert.equal(sanitize({ kind: 'X' }), null);
  assert.equal(sanitize(null), null);
  assert.deepEqual(sanitize({ kind: 'ENDED', lat: '1', lng: 2, text: 'a' }), { kind: 'ENDED', text: 'a' });
});

test('차가 움직인다는 알림을 받는다', async () => {
  assert.equal((await post({ kind: 'MOVING', text: 't' })).status, 201);
  const html = await (await fetch(`${base}/`)).text();
  assert.match(html, /차가 다시 움직임/);
});

test('잘못된 요청을 거부한다', async () => {
  assert.equal((await post('not json')).status, 400);
  assert.equal((await post({ kind: 'NOPE' })).status, 400);
  assert.equal((await post('x'.repeat(5000))).status, 413);
  assert.equal((await fetch(`${base}/nope`)).status, 404);
});

test('화면에 가상 표시가 있고 문구를 이스케이프한다', async () => {
  await post({ kind: 'CANCEL', text: '<script>a</script>' });
  const html = await (await fetch(`${base}/`)).text();
  assert.match(html, /가상 전송입니다/);
  assert.doesNotMatch(html, /<script>/);
  assert.match(html, /&lt;script&gt;/);
});

test('로그에 본문을 남기지 않는다', async () => {
  logs.length = 0;
  await post({ kind: 'CANCEL', text: '본문비밀문구' });
  assert.equal(logs.length, 1);
  assert.doesNotMatch(logs[0], /본문비밀문구/);
});

test('초기화하면 목록이 빈다', async () => {
  await post({ kind: 'CANCEL', text: 't' });
  await fetch(`${base}/reset`, { method: 'POST' });
  assert.deepEqual(await (await fetch(`${base}/alerts`)).json(), []);
});
