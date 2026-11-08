"""release APK 재연. 음성 주입이 없으므로 말로 하는 응답은 시험하지 않는다. setup_demo.py 15를 먼저 실행한다."""
import sys, os
sys.path.insert(0, os.path.dirname(__file__))
from h import *

results = []


def check(name, ok, detail=''):
    results.append((name, bool(ok)))
    print(('PASS ' if ok else 'FAIL ') + name, detail if not ok else '', flush=True)


session(); adb('shell', 'input', 'keyevent', '224')
print('== debug 훅 없음')
clear(); say('help'); w(4)
check('합성 음성 주입이 동작하지 않음', not logs('REQUESTED|voice'), logs())

print('== 화면 버튼 도움 요청 (기어 P)')
car('park'); w(1); reset_body(); reset_alerts(); app(); w(3); clear()
tap('도움 요청')
check('REQUESTED', wait_log('-> REQUESTED', 6), logs())
check('내비 요청', wait_log('navi route requested', 8), logs())
for i in range(40):
    if 'LOCATION_SHARE' in alert_kinds(): break
    w(1)
check('위치 공유', 'LOCATION_SHARE' in alert_kinds(), alerts())
app(); w(3); tap('보호자 알림 취소'); w(3)
check('알림 취소 전송', 'CANCEL' in alert_kinds(), alerts())

print('== 앱 뒤, 무응답부터 종료까지')
car('park'); w(1); reset_body(); reset_alerts(); navi_front(); w(3); clear()
car('stop')
check('ASK1', wait_log('-> ASK1', 25))
check('ASK2', wait_log('ASK1 -> ASK2', 30))
check('ALERTED', wait_log('ASK2 -> ALERTED', 30))
w(3); v = view()
check('창문', v['window'] > 0, v)
check('무응답 전송', 'NO_RESPONSE' in alert_kinds(), alerts())
na = [x for x in alerts() if x['kind'] == 'NO_RESPONSE']
check('119 요청·지도·마지막 응답 시각', na and '119' in na[0]['text'] and 'map' in na[0] and 'lastResponseAt' in na[0], na)
check('기어 P 전 제어 없음', v['locked'] is True and v['cabin'] is False and not logs('control (door|cabin|tail|glove)'), v)
put('/vehicle/controls?speed=0&accelerator=0&brake=40&steering=0'); w(3)
check('브레이크로 취소 안 됨', not logs('ALERTED -> IDLE') and 'CANCEL' not in alert_kinds(), logs('state'))
put('/vehicle/gear?gear=Park')
check('NOTICE', wait_log('-> UNLOCK_NOTICE', 8))
check('RESCUE', wait_log('UNLOCK_NOTICE -> RESCUE', 35))
w(3); v = view()
check('잠금 해제', v['locked'] is False, v)
check('실내등', v['cabin'] is True, v)
check('미등', v['tail'] == 'Position', v)
check('글러브박스 호출', logs('control glove locked=false'), logs('control'))
app(); w(3); t = texts()
check('구조 화면 표시', any('심장 질환 이력' in x for x in t), t)
swipe_up(); swipe_up(); tap('구조 안내 종료'); w(3); t = texts()
check('종료 후 가림', not any('심장 질환 이력' in x for x in t), t)
check('종료 전달', 'ENDED' in alert_kinds(), alerts())

print('== 조작 취소')
car('park'); w(1); reset_body(); reset_alerts(); clear(); car('stop')
ok = wait_log('-> ASK1', 25); clear(); put('/vehicle/controls?speed=0&accelerator=0&brake=40&steering=0')
check('브레이크 취소', ok and wait_log('-> IDLE', 8), logs('state'))
check('전송 없음', alert_kinds() == [], alerts())

print('== 고지 중 화면 취소 (기어 P)')
car('park'); w(1); reset_body(); reset_alerts(); navi_front(); w(2); clear(); car('stop')
check('ALERTED', wait_log('-> ALERTED', 70)); w(2); put('/vehicle/gear?gear=Park'); check('NOTICE', wait_log('-> UNLOCK_NOTICE', 8))
app(); w(2); tap('보호자 알림 취소'); check('화면으로 취소', wait_log('UNLOCK_NOTICE -> IDLE', 8), logs('state'))
w(3); check('취소 메시지', 'CANCEL' in alert_kinds(), alerts()); check('잠금 안 풂', view()['locked'] is True, view())

print('== 차가 다시 움직임')
car('park'); w(1); reset_body(); reset_alerts(); navi_front(); w(2); clear(); car('stop')
check('ALERTED', wait_log('-> ALERTED', 70)); w(2); clear(); put('/vehicle/controls?speed=20&accelerator=5&brake=0&steering=0')
check('흐름 멈춤', wait_log('ALERTED -> IDLE', 8), logs('state')); w(3); k = alert_kinds()
check('움직임 전달, 취소 아님', 'MOVING' in k and 'CANCEL' not in k, k)
car('park'); w(20); check('움직인 뒤 P는 잠금 안 풂', view()['locked'] is True, view())

print('== 운전자 변경')
car('park'); w(2); app(); w(2); swipe_up(); tap('운전자 변경'); w(3)
check('서비스 종료', logs('service stopped'), logs())
t = texts(); check('다시 확인 요구', any('맞습니까' in x for x in t) and not any('심장' in x for x in t), t)

print('프로세스', alive(), '충돌', [l[:100] for l in adb('logcat', '-b', 'crash', '-d').splitlines() if PKG in l][:3])
x = adb('logcat', '-d')
print('logcat 평문', {w_: sum(1 for l in x.splitlines() if w_ in l) for w_ in ['심장', '가상의 약', '000-0000', '가족 B', '운전자 A']})
print('stt 오류', logs('stt error')[:4])
print('합계', sum(1 for _, o in results if o), '/', len(results), '실패', [n for n, o in results if not o])
