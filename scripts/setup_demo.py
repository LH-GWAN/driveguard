"""앱을 처음 상태에서 동의, 가상 견본 입력, 운전자 확인까지 진행한다. 사용: setup_demo.py [대기초]"""
import sys, os
sys.path.insert(0, os.path.dirname(__file__))
from h import *

def allow_all():
    for _ in range(6):
        names = texts()
        for name in ['앱 사용 중에만 허용', '허용']:
            if name in names:
                tap(name)
                w(2)
                break
        else:
            return


quiet = sys.argv[1] if len(sys.argv) > 1 else '15'
session(); car('park'); reset_body()
adb('shell', 'input', 'keyevent', '224'); adb('shell', 'wm', 'dismiss-keyguard')
adb('shell', 'am', 'force-stop', '--user', '10', PKG); adb('shell', 'pm', 'clear', '--user', '10', PKG)
reset_alerts(); app(); w(3)
for t in ['건강 정보(병력', '응답 확인에 답이 없거나', '등록할 보호자에게']:
    tap(t); w(0.8)
tap('동의하고 계속'); w(2)
# 권한 안내 화면: 무엇을 허용하는지 읽은 뒤 계속
if not tap('계속'):
    swipe_up(); w(1); tap('계속')
w(2)
allow_all()
tap('견본 채우기'); w(1)
swipe_up(); swipe_up()
tap('응답 확인까지 기다릴 시간'); w(1)
adb('shell', 'input', 'keyevent', 'KEYCODE_MOVE_END')
for _ in range(4):
    adb('shell', 'input', 'keyevent', 'KEYCODE_DEL')
adb('shell', 'input', 'text', quiet); w(1); hide_keyboard()
swipe_up(); swipe_up()
tap('저장'); w(2)
tap('맞습니다'); w(3)
allow_all()
print(texts()[:14])
print(logs('service|tts|stt'))
