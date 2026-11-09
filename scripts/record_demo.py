"""시연 장면을 에뮬레이터 자체 녹화로 찍는다. debug 빌드가 필요하다(합성 음성 주입).
사용: record_demo.py <작업폴더> [--dry]
화면 덤프가 느려 녹화 중에는 쓰지 않는다. --dry로 한 번 돌려 버튼 좌표를 저장한 뒤 녹화한다.
보호자 화면은 headless Chrome으로 실제 페이지를 캡처한다."""
import json, os, subprocess, sys, time
sys.path.insert(0, os.path.dirname(__file__))
import h
from h import adb, alert_kinds, alerts, app, car, clear, logs, navi_front, put, reset_alerts, reset_body, say, session, view, wait_log, w

OUT = sys.argv[1]
DRY = '--dry' in sys.argv
CLIPS = os.path.join(OUT, 'clips')
STILLS = os.path.join(OUT, 'stills')
os.makedirs(CLIPS, exist_ok=True)
os.makedirs(STILLS, exist_ok=True)
CACHE = os.path.join(OUT, 'taps.json')
taps = {} if DRY else json.load(open(CACHE))
seen = {}
scenes = []
cur = None
facts = {}
seen_allow = set()
CHROME = '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'


class Scene:
    def __init__(self, name):
        self.name = name
        self.caps = []

    def __enter__(self):
        global cur
        cur = self
        path = os.path.join(CLIPS, self.name + '.webm')
        if os.path.exists(path):
            os.remove(path)
        if not DRY:
            adb('emu', 'screenrecord', 'start', '--time-limit', '150', '--size', '1920x1080', '--bit-rate', '6000000', '--fps', '30', path)
        self.t0 = time.time()
        w(0.5)
        return self

    def cap(self, text):
        self.caps.append((round(time.time() - self.t0, 2), text))
        print(self.name, text, flush=True)

    def __exit__(self, *exc):
        self.dur = round(time.time() - self.t0, 2)
        if not DRY:
            adb('emu', 'screenrecord', 'stop')
        w(2)
        scenes.append({'name': self.name, 'dur': self.dur, 'caps': self.caps})


def tap(text):
    key = f'{cur.name}:{text}'
    seen[key] = seen.get(key, 0) + 1
    key = f'{key}:{seen[key]}'
    if not DRY:
        w(0.8)
        x, y = taps[key]
        adb('shell', 'input', 'tap', str(x), str(y))
        return
    ns = h.nodes()
    ms = [n for n in ns if n[0] == text] or [n for n in ns if text in n[0]]
    if not ms:
        raise SystemExit(f'NOT FOUND {text} {[n[0] for n in ns if n[0]][:12]}')
    _, a, b, c, d = ms[0]
    taps[key] = [(a + c) // 2, (b + d) // 2]
    adb('shell', 'input', 'tap', str((a + c) // 2), str((b + d) // 2))


def allow():
    """권한 대화상자를 차례로 허용한다. 몇 번 나오는지는 --dry에서 정해 저장한다."""
    if DRY:
        count = 0
        for _ in range(8):
            names = h.texts()
            name = next((n for n in ['앱 사용 중에만 허용', '허용'] if n in names), None)
            if not name:
                break
            count += 1
            tap(name)
            w(2)
        return
    for k in [k for k in taps if k.startswith(f'{cur.name}:') and '허용' in k and not k.endswith(':names') and k not in seen_allow]:
        seen_allow.add(k)
        adb('shell', 'input', 'tap', str(taps[k][0]), str(taps[k][1]))
        w(1.5)


def guardian_shot(name):
    if DRY:
        return
    w(1)
    subprocess.run([CHROME, '--headless=new', '--disable-gpu', '--hide-scrollbars', '--no-first-run',
                    f'--user-data-dir={os.path.join(OUT, "chrome")}', '--window-size=1400,788', '--force-device-scale-factor=1.2',
                    f'--screenshot={os.path.join(STILLS, name + ".png")}', '--virtual-time-budget=500', h.GUARDIAN + '/'],
                   capture_output=True, timeout=60)


def wait_or_stop(pattern, seconds):
    if not wait_log(pattern, seconds):
        raise SystemExit(f'시간 초과: {pattern} {logs()[-6:]}')


def wait_alert(kind, seconds=25):
    end = time.time() + seconds
    while time.time() < end:
        if kind in alert_kinds():
            return
        w(0.5)
    raise SystemExit(f'알림 없음: {kind} {alert_kinds()}')


session()
adb('shell', 'input', 'keyevent', '224')
adb('shell', 'wm', 'dismiss-keyguard')
car('park')
reset_body()
reset_alerts()
adb('shell', 'am', 'force-stop', '--user', '10', h.PKG)
adb('shell', 'pm', 'clear', '--user', '10', h.PKG)
adb('emu', 'geo', 'fix', '127.0396', '37.5012')
clear()
app()
w(3.5)

with Scene('s1') as s:
    s.cap('출발 전: 건강 정보, 위치 전송, 보호자 연락에 각각 동의')
    w(2)
    tap('건강 정보(병력'); tap('응답 확인에 답이 없거나'); tap('등록할 보호자에게')
    w(1.5)
    tap('동의하고 계속')
    w(2.5)
    allow()
    s.cap('사전 입력: 병력, 복용약, 응급약 위치, 보호자')
    tap('가상 견본 채우기')
    w(3)
    h.swipe_up(); h.swipe_up()
    s.cap('응답을 기다릴 시간은 운전자가 정함 (시연 10초)')
    tap('응답 확인까지 기다릴 시간')
    w(1)
    adb('shell', 'input', 'keyevent', 'KEYCODE_MOVE_END')
    for _ in range(4):
        adb('shell', 'input', 'keyevent', 'KEYCODE_DEL')
    adb('shell', 'input', 'text', '10')
    w(1.5)
    h.hide_keyboard()
    h.swipe_up(); h.swipe_up()
    tap('저장')
    w(2.5)
    s.cap('오늘 운전자를 확인하면 켜짐. 건강 정보는 암호화해 저장하고 평소에는 가림')
    w(3)
    tap('맞습니다')
    w(2)
    allow()
    w(4)
    wait_or_stop('tts ready', 15)

navi_front()
w(3)
car('drive')
w(3)
clear()
reset_alerts()

with Scene('s2') as s:
    s.cap('주행 중 (기어 D, 30 km/h). 앱은 뒤에서 동작')
    w(3.5)
    s.cap('주행 중 운전자: "가슴이 아파요"')
    w(1)
    say('help')
    wait_or_stop('-> REQUESTED', 12)
    s.cap('음성 안내: "가까운 안전한 곳에 바로 세우세요. 필요하면 119에 전화하세요..."')
    w(5)
    s.cap('도움 요청 인식')
    w(4.5)
    s.cap('가까운 졸음쉼터로 내비 설정')
    wait_alert('LOCATION_SHARE', 30)
    s.cap('보호자에게 현재 위치 전송')
    w(3.5)
guardian_shot('g1')

clear()
with Scene('s3') as s:
    s.cap('갓길에 정차: 기어 D, 속도 0')
    car('stop')
    w(2)
    s.cap('조작 없이 멈춘 시간을 셈 (시연 10초)')
    wait_or_stop('-> ASK1', 25)
    s.cap('음성 안내: "괜찮으세요? 괜찮으면 괜찮아요 라고 말해 주세요."')
    wait_or_stop('ASK1 -> ASK2', 30)
    s.cap('답이 없어 한 번 더 확인')
    wait_or_stop('ASK2 -> ALERTED', 30)
    s.cap('두 번 모두 답이 없어 응답 없음으로 판단')
    w(1)

with Scene('s4') as s:
    s.cap('음성 안내: "운전자가 응답하지 않습니다. 차가 멈춰 있으면 기어를 P로 넣어 주세요. 확인하고 119에 신고해 주세요..."')
    wait_alert('NO_RESPONSE', 15)
    w(8)
    v = view()
    facts['window'] = v['window']
    facts['locked_before'] = v['locked']
    s.cap('운전석 창문을 조금 엶')
    w(5)
guardian_shot('g2')
if not DRY and any('심장' in json.dumps(a, ensure_ascii=False) for a in alerts()):
    raise SystemExit('전송 본문에 건강 정보가 있습니다')

with Scene('s5') as s:
    s.cap('동승자가 브레이크를 밟아도 안내는 계속됨')
    clear()
    w(2.5)
    put('/vehicle/controls?speed=0&accelerator=0&brake=40&steering=0')
    facts['brake_at'] = round(time.time() - s.t0, 2)
    w(0.2)
    s.cap('음성 안내: "안내를 멈추려면 괜찮아 취소해줘 라고 말해 주세요."')
    w(5)
    if logs('ALERTED -> IDLE'):
        raise SystemExit('브레이크로 취소됨')
    s.cap('동승자가 기어를 P로 넣음')
    w(2)
    put('/vehicle/gear?gear=Park')
    wait_or_stop('ALERTED -> UNLOCK_NOTICE', 10)
    s.cap('음성 안내: "문 잠금을 해제합니다. 취소하려면 괜찮아 취소해줘 라고 말하세요."')
    wait_or_stop('UNLOCK_NOTICE -> RESCUE', 40)
    put('/vehicle/controls?speed=0&accelerator=0&brake=0&steering=0')
    s.cap('음성 안내: "문 잠금을 해제했습니다. 화면에서 앱을 열면 미리 입력한 정보를 볼 수 있습니다."')
    w(5.5)
    v = view()
    facts.update(locked_after=v['locked'], cabin=v['cabin'], tail=v['tail'])
    s.cap('문 잠금 해제, 실내등·미등 켜짐')
    w(4)
    # 서비스가 화면을 띄우는 동작은 일정하지 않아 실제 결과대로 적는다.
    shown = any(h.PKG + '/.ui.MainActivity' in line for line in adb('shell', 'dumpsys', 'activity', 'activities').splitlines()
                if 'ResumedActivity' in line)
    s.cap('구조 화면 표시' if shown else '동승자가 앱을 열어 구조 화면 표시')
    app()
    w(3)
    s.cap('구조 화면: 병력, 복용약, 응급약 위치, 보호자, 마지막 응답 시각, 정차 시각')
    w(5)
    h.swipe_up(); h.swipe_up()
    s.cap('동승자·구조자 안내: 주변 안전 확인, 반응 확인, 119 신고')
    w(4)

with Scene('s6') as s:
    s.cap('구조대에 인계한 뒤 차 안에서 종료')
    w(2)
    tap('구조 안내 종료')
    w(3)
    s.cap('건강 정보를 다시 가리고 보호자에게 종료 전달')
    w(4)
guardian_shot('g3')
facts['alerts'] = alert_kinds()

navi_front()
w(2)
car('park')
w(1)
reset_body()
reset_alerts()
clear()
with Scene('s7') as s:
    s.cap('신호 대기로 멈춘 경우: 기어 D, 속도 0, 조작 없음')
    car('stop')
    wait_or_stop('-> ASK1', 25)
    s.cap('음성 안내: "괜찮으세요?..."')
    w(6)
    s.cap('운전자: "괜찮아요"')
    say('ok')
    wait_or_stop('ASK1 -> IDLE', 12)
    s.cap('음성 안내: "취소되었습니다." 보호자에게 아무것도 보내지 않음')
    w(4)
facts['after_cancel'] = alert_kinds()
car('park')
w(1)

if DRY:
    json.dump(taps, open(CACHE, 'w'), ensure_ascii=False, indent=1)
else:
    json.dump({'scenes': scenes, 'facts': facts}, open(os.path.join(OUT, 'timeline.json'), 'w'), ensure_ascii=False, indent=1)
print('facts', facts)
print('합계', sum(x['dur'] for x in scenes))
