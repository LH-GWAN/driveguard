"""가상 서버가 꺼진 상태의 전송 실패와 음성 고지를 확인한다. 서버를 먼저 끄고 실행한다."""
import sys, os
sys.path.insert(0, os.path.dirname(__file__))
from h import *

session(); adb('shell', 'input', 'keyevent', '224')
car('park'); w(2); reset_body(); clear(); car('stop')
print('ALERTED', wait_log('-> ALERTED', 70))
print('전송 실패 기록', wait_log('guardian failed NO_RESPONSE', 30))
w(6)
print(logs('guardian|tts|state')[-8:])
print('창문은 전송과 무관하게 동작', view()['window'])
print('다음 단계 진행 가능', end=' ')
put('/vehicle/gear?gear=Park')
print(wait_log('-> UNLOCK_NOTICE', 8))
w(2); say('ok'); print('취소', wait_log('UNLOCK_NOTICE -> IDLE', 10))
car('park'); reset_body()
