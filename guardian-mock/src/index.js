import { createGuardianServer } from './server.js';

const PORT = Number(process.env.PORT ?? 8787);
// 에뮬레이터의 10.0.2.2는 PC의 루프백으로 연결된다. 밖으로 열지 않는다.
createGuardianServer().listen(PORT, '127.0.0.1', () => {
  console.log(`보호자 가상 서버 http://127.0.0.1:${PORT}/ (가상 전송, 실제 문자·알림 없음)`);
});
