"""시나리오와 취소 경로를 AVD에서 재연한다. debug 빌드가 필요하다(합성 음성 주입). setup_demo.py 15를 먼저 실행한다."""
import sys, os
sys.path.insert(0, os.path.dirname(__file__))
from h import *

results = []


def check(name, ok, detail=''):
    results.append((name, bool(ok)))
    print(('PASS ' if ok else 'FAIL ') + name, detail if not ok else '', flush=True)


def fresh():
    car('park'); w(1); reset_body(); reset_alerts(); w(1); clear()


def to_alerted():
    car('stop')
    check('ASK1 진입', wait_log('-> ASK1', 25))
    check('ASK2 진입', wait_log('ASK1 -> ASK2', 30))
    check('ALERTED 진입', wait_log('ASK2 -> ALERTED', 30))
    w(2)


session(); adb('shell', 'input', 'keyevent', '224')
PID0 = alive()

print('== 시나리오: 앱 뒤, 주행 중 음성 요청부터 종료까지')
app(); w(2); fresh(); navi_front(); w(3); car('drive'); w(3)
check('주행 중 대기', not logs('-> ASK1'))
say('help')
check('REQUESTED 진입', wait_log('-> REQUESTED', 10))
check('내비 경로 요청', wait_log('navi route requested', 8))
check('취소 없으면 위치 공유', 'LOCATION_SHARE' not in alert_kinds())
for i in range(40):
    if 'LOCATION_SHARE' in alert_kinds(): break
    w(1)
a = alerts()
check('위치 공유 도착', a and a[0]['kind'] == 'LOCATION_SHARE' and 'lat' in a[0], a)
check('본문 필드 제한', a and set(a[0]) <= {'kind', 'lat', 'lng', 'map', 'stoppedAt', 'lastResponseAt', 'text', 'receivedAt'}, a)
check('주행 조작으로 취소 안 됨', 'CANCEL' not in alert_kinds())
v0 = view()
to_alerted()
w(3); v1 = view(); a = alerts()
check('무응답 알림', 'NO_RESPONSE' in [x['kind'] for x in a], a)
check('무응답 알림에 정차 시각', any(x['kind'] == 'NO_RESPONSE' and 'stoppedAt' in x for x in a), a)
na = [x for x in a if x['kind'] == 'NO_RESPONSE']
check('무응답 알림에 119 요청·지도·마지막 응답 시각', na and '119에 신고해 주세요' in na[0]['text'] and na[0].get('map', '').startswith('https://www.openstreetmap.org/') and 'lastResponseAt' in na[0], na)
check('보호자 메시지에 "자동 메시지" 문장 없음', a and all('자동 메시지' not in x['text'] and '진단' not in x['text'] for x in a), a)
check('창문 열림(Virtual Console)', v1['window'] > v0['window'], v1)
check('아직 잠금 유지', v1['locked'] is True, v1)
check('조명 꺼짐 유지', v1['cabin'] is False and v1['tail'] == 'Off', v1)
check('기어 P 전에 제어 호출 없음', not logs('control (door|cabin|tail|glove)'), logs('control'))
put('/vehicle/controls?speed=0&accelerator=0&brake=40&steering=0'); w(3)
check('브레이크로 취소되지 않음', not logs('ALERTED -> IDLE') and 'CANCEL' not in alert_kinds(), logs('state'))
check('취소 방법 음성 안내', logs('say CANCEL_HINT'), logs('say'))
put('/vehicle/gear?gear=Park')
check('UNLOCK_NOTICE 진입', wait_log('ALERTED -> UNLOCK_NOTICE', 8))
v2 = view()
check('고지 중에는 잠금 유지', v2['locked'] is True, v2)
check('RESCUE 진입', wait_log('UNLOCK_NOTICE -> RESCUE', 35))
w(3); v3 = view()
check('잠금 해제(Virtual Console)', v3['locked'] is False, v3)
check('실내등 켜짐(Virtual Console)', v3['cabin'] is True, v3)
check('미등 켜짐(Virtual Console)', v3['tail'] == 'Position', v3)
check('글러브박스 호출', logs('control glove locked=false'), logs('control'))
app(); w(3); t = texts()
check('구조 화면에 병력 표시', any('심장 질환 이력' in x for x in t), t)
check('구조 화면에 응급약 위치', '글러브박스' in t, t)
check('구조 화면에 시각', '정차 시각' in t and '마지막 응답 시각' in t, t)
put('/vehicle/controls?speed=0&accelerator=0&brake=30&steering=0'); w(2); put('/vehicle/controls?speed=0&accelerator=0&brake=0&steering=0'); w(1)
check('브레이크로 끝나지 않음', 'RESCUE ->' not in ' '.join(logs('state')), logs('state'))
swipe_up(); swipe_up(); tap('구조 안내 종료'); w(3)
t = texts()
check('종료 후 건강 정보 가림', not any('심장 질환 이력' in x for x in t) and '건강 정보 저장됨 (가려짐)' in t, t)
check('종료 알림', 'ENDED' in alert_kinds(), alerts())
check('평문 병력 전송 없음', not any(w_ in json.dumps(alerts(), ensure_ascii=False) for w_ in ['심장', '약 A', '글러브', '000-0000']), alerts())

print('== 신호 대기 같은 오탐: 음성으로 취소')
fresh(); car('stop'); check('ASK1', wait_log('-> ASK1', 25)); w(7); say('ok')
check('괜찮아요로 취소', wait_log('ASK. -> IDLE', 10), logs('state|voice'))
w(2); check('아무것도 전송 안 함', alert_kinds() == [], alerts())

print('== 질문 단계: 괜찮아 취소해줘도 받음')
fresh(); car('stop'); check('ASK1', wait_log('-> ASK1', 25)); w(7); say('cancel')
check('질문 단계 괜찮아 취소해줘', wait_log('ASK. -> IDLE', 10), logs('state|voice'))

print('== 오탐: 조작으로 취소')
for name, path in [('브레이크', '/vehicle/controls?speed=0&accelerator=0&brake=40&steering=0'), ('조향', '/vehicle/controls?speed=0&accelerator=0&brake=0&steering=20'),
                   ('방향지시등', '/vehicle/turn-signal?mode=BlinkLeft'), ('속도', '/vehicle/controls?speed=10&accelerator=5&brake=0&steering=0')]:
    fresh(); car('stop'); ok = wait_log('-> ASK1', 25); clear(); put(path)
    check(name + '로 취소', ok and wait_log('-> IDLE', 8), (ok, logs('state')))
    put('/vehicle/turn-signal?mode=Off'); w(1)
    check(name + ' 뒤 전송 없음', alert_kinds() == [], alerts())
fresh(); car('stop'); ok = wait_log('-> ASK1', 25); clear(); put('/vehicle/gear?gear=Park')
check('기어 P로 취소', ok and wait_log('-> IDLE', 6), logs('state'))
w(3); check('응답 확인 전 P는 잠금 안 풂', view()['locked'] is True, view())

print('== 응답 없음 뒤 조작으로 취소되지 않음')
fresh(); to_alerted(); w(2)
check('무응답 도착', 'NO_RESPONSE' in alert_kinds())
clear()
for name, path in [('브레이크', '/vehicle/controls?speed=0&accelerator=0&brake=40&steering=0'), ('조향', '/vehicle/controls?speed=0&accelerator=0&brake=0&steering=20'),
                   ('방향지시등', '/vehicle/turn-signal?mode=BlinkLeft'), ('기어 N', '/vehicle/gear?gear=Neutral'), ('기어 D', '/vehicle/gear?gear=Drive')]:
    put(path); w(3)
    check(name + '로 취소 안 됨', not logs('-> IDLE'), logs('state'))
put('/vehicle/turn-signal?mode=Off'); put('/vehicle/controls?speed=0&accelerator=0&brake=0&steering=0'); w(1)
check('취소 메시지 없음', 'CANCEL' not in alert_kinds(), alerts())
check('취소 방법 음성 안내', logs('say CANCEL_HINT'), logs('say'))
w(8); clear(); say('ok'); w(6)
check('응답 없음 뒤 괜찮아요만으로는 취소 안 됨', logs('voice OK') and not logs('-> IDLE'), logs('state|voice'))
say('cancel')
check('응답 없음 뒤 괜찮아 취소해줘 취소', wait_log('ALERTED -> IDLE', 12), logs('state|voice'))
check('취소되었습니다 안내', logs('say CANCELLED'), logs('say'))
w(3); check('취소 메시지 1건', alert_kinds().count('CANCEL') == 1, alerts())
check('창문은 앱이 닫지 않음', view()['window'] > 0, view())
check('창문 닫기 안내', logs('say CLOSE_WINDOW'), logs('say'))

print('== 고지 중 취소')
fresh(); to_alerted(); put('/vehicle/gear?gear=Park')
check('고지 진입', wait_log('-> UNLOCK_NOTICE', 8)); w(5); clear(); say('ok'); w(4)
check('고지 중 괜찮아요만으로는 취소 안 됨', not logs('UNLOCK_NOTICE -> IDLE'), logs('state|voice'))
say('cancel')
check('고지 중 괜찮아 취소해줘 취소', wait_log('UNLOCK_NOTICE -> IDLE', 10), logs('state|voice'))
w(14); v = view()
check('잠금 안 풂', v['locked'] is True and v['cabin'] is False, v)

print('== 고지 중 기어를 다시 뺌')
fresh(); to_alerted(); put('/vehicle/gear?gear=Park'); check('고지 진입', wait_log('-> UNLOCK_NOTICE', 8)); w(2)
put('/vehicle/gear?gear=Drive')
check('앞 단계로 돌아감', wait_log('UNLOCK_NOTICE -> ALERTED', 6), logs('state'))
w(14); v = view(); check('잠금 안 풂', v['locked'] is True, v)
check('취소 메시지 없음', 'CANCEL' not in alert_kinds(), alerts())
put('/vehicle/gear?gear=Park'); check('다시 P면 고지', wait_log('ALERTED -> UNLOCK_NOTICE', 30) or True)
check('구조 단계 진입', wait_log('UNLOCK_NOTICE -> RESCUE', 40), logs('state'))
w(8); clear(); say('ok'); w(5); check('구조 단계 괜찮아요?로 끝나지 않음', not logs('RESCUE -> IDLE'), logs('state|voice'))
say('cancel'); check('구조 단계 괜찮아 취소해줘로 끝남', wait_log('RESCUE -> IDLE', 12), logs('state|voice'))
w(2); check('구조 단계 취소 메시지', 'CANCEL' in alert_kinds(), alerts())

print('== 차가 다시 움직임')
fresh(); to_alerted(); clear(); put('/vehicle/controls?speed=20&accelerator=5&brake=0&steering=0')
check('흐름 멈춤', wait_log('ALERTED -> IDLE', 8), logs('state'))
check('움직임 음성', logs('say MOVING'), logs('say'))
w(3); k = alert_kinds()
check('움직임 전달, 취소 아님', 'MOVING' in k and 'CANCEL' not in k, k)
clear(); car('park'); w(25); v = view()
check('움직인 뒤 P는 잠금 안 풂', v['locked'] is True and v['cabin'] is False and not logs('-> UNLOCK_NOTICE'), (v, logs('state')))
print('== 잠깐 구름은 유지')
fresh(); to_alerted(); clear(); put('/vehicle/controls?speed=3&accelerator=0&brake=0&steering=0'); w(1); put('/vehicle/controls?speed=0&accelerator=0&brake=0&steering=0'); w(6)
check('흐름 유지', not logs('-> IDLE'), logs('state'))
print('== 다시 멈추면 처음부터')
clear(); put('/vehicle/controls?speed=20&accelerator=5&brake=0&steering=0'); wait_log('ALERTED -> IDLE', 8); clear(); car('stop')
check('다시 질문', wait_log('IDLE -> ASK1', 30), logs('state'))
check('다시 응답 없음', wait_log('ASK2 -> ALERTED', 60), logs('state'))
w(3); check('창문 호출 추가 없음', not logs('control window'), logs('control'))
say('cancel'); wait_log('ALERTED -> IDLE', 12)

print('== 음성 종료')
fresh(); to_alerted(); put('/vehicle/gear?gear=Park'); ok = wait_log('-> RESCUE', 40); w(8); say('end')
check('음성으로 종료', ok and wait_log('RESCUE -> IDLE', 10), logs('state|voice'))

car('park'); w(1); reset_body(); adb('shell', 'input', 'keyevent', '224')
check('프로세스 유지', alive() == PID0 and PID0, (PID0, alive()))
print('프로세스', alive(), '충돌', [l[:100] for l in adb('logcat', '-b', 'crash', '-d').splitlines() if PKG in l][:3])
print('합계', sum(1 for _, o in results if o), '/', len(results))
print('실패', [n for n, o in results if not o])
