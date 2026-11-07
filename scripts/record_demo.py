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
    s.cap('[가상 데이터] 출발 전: 건강 정보·위치 전송·보호자 동의를 따로 받습니다')
    w(2)
    tap('건강 정보(병력'); tap('응답 확인에 답이 없거나'); tap('등록할 보호자에게')
    w(1.5)
    tap('동의하고 계속')
    w(2.5)
    allow()
    s.cap('[가상 데이터] 사전 입력: 병력, 복용약, 응급약 위치, 보호자. 전부 가상 값입니다')
    tap('가상 견본 채우기')
    w(3)
    h.swipe_up(); h.swipe_up()
    s.cap('기다릴 시간은 운전자가 정합니다. 병력으로 자동 변경하지 않습니다 (시연은 10초)')
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
    s.cap('출발할 때 오늘 운전자를 확인해야 켜집니다. 건강 정보는 암호화 저장, 평소 가림')
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
    s.cap('[시뮬레이션] 주행 중: 기어 D, 30 km/h (Virtual Console). 앱은 뒤에 있습니다')
    w(3.5)
    s.cap('[시뮬레이션] 운전자의 말 "가슴이 아파요"를 합성 음성 파일로 입력 (실제 마이크 미시험, 호출 방법 미정)')
    w(1)
    say('help')
    wait_or_stop('-> REQUESTED', 12)
    s.cap('[실제 SDK] 인식 엔진은 실제 동작. 증상을 해석하지 않고 "도와줘"와 같은 호출로 처리합니다')
    w(3.5)
    s.cap('[음성 출력] "가까운 안전한 곳에 바로 세우세요. 필요하면 119에 전화하세요..."')
    w(6)
    s.cap('[실제 SDK] NaviHelper로 가까운 졸음쉼터를 내비에 설정 (공공데이터 좌표, 위치는 시뮬레이션)')
    wait_alert('LOCATION_SHARE', 30)
    s.cap('10초 안에 취소가 없어 보호자에게 위치를 보냈습니다 [가상 전송]')
    w(3.5)
guardian_shot('g1')

clear()
with Scene('s3') as s:
    s.cap('[시뮬레이션] 차를 세움: 기어 D, 속도 0. 기어가 P가 아닌 채 조작이 멈춥니다')
    car('stop')
    w(2)
    s.cap('조향·브레이크·방향지시등·기어·속도 변화가 없는 시간을 셉니다 (시연 10초)')
    wait_or_stop('-> ASK1', 25)
    s.cap('[음성 출력] "괜찮으세요? 괜찮으면 괜찮아요 라고 말해 주세요." [실제 SDK: TextToSpeech]')
    wait_or_stop('ASK1 -> ASK2', 30)
    s.cap('답이 없어 한 번 더 묻습니다. 신호 대기에서도 일어날 수 있어 이 단계는 질문뿐입니다')
    wait_or_stop('ASK2 -> ALERTED', 30)
    s.cap('두 번 모두 답이 없어 다음 단계로 갑니다. 119에 자동 신고하지 않습니다')
    w(3)

with Scene('s4') as s:
    s.cap('[음성 출력] "운전자가 응답하지 않습니다. 차가 멈춰 있으면 기어를 P로 넣어 주세요. 확인하고 119에 신고해 주세요."')
    wait_alert('NO_RESPONSE', 15)
    w(3)
    v = view()
    facts['window'] = v['window']
    facts['locked_before'] = v['locked']
    s.cap(f"[실제 SDK] 운전석 창문을 조금 엽니다 (콘솔 값 {v['window']}). 기어가 P가 아니라 문 잠금은 풀 수 없습니다")
    w(5)
guardian_shot('g2')
if not DRY and any('심장' in json.dumps(a, ensure_ascii=False) for a in alerts()):
    raise SystemExit('전송 본문에 건강 정보가 있습니다')

with Scene('s5') as s:
    s.cap('[시뮬레이션] 동승자가 기어를 P로 넣는 상황을 Virtual Console로 대신합니다')
    w(2)
    put('/vehicle/gear?gear=Park')
    wait_or_stop('ALERTED -> UNLOCK_NOTICE', 10)
    s.cap('[음성 출력] "문 잠금을 해제합니다. 취소하려면 괜찮아요 라고 말하세요." 짧은 취소 시간을 둡니다')
    wait_or_stop('UNLOCK_NOTICE -> RESCUE', 40)
    w(2.5)
    v = view()
    facts.update(locked_after=v['locked'], cabin=v['cabin'], tail=v['tail'])
    s.cap(f"[실제 SDK] 문 잠금 해제, 실내등, 미등 (콘솔 값: 잠금 {'해제' if not v['locked'] else '유지'}, 실내등 {'켜짐' if v['cabin'] else '꺼짐'}, 미등 {'켜짐' if v['tail'] == 'Position' else '꺼짐'})")
    w(4)
    s.cap('[음성 출력] "화면에서 앱을 열면 미리 입력한 정보를 볼 수 있습니다." 동승자가 앱을 엽니다')
    app()
    w(4)
    s.cap('구조 화면: 입력해 둔 사실만 표시합니다 (가상 값). 진단이나 복용 지시를 하지 않습니다')
    w(5)
    h.swipe_up(); h.swipe_up()
    s.cap('안내 문구는 전문가 검토 전 초안입니다. 건강 정보는 응답 없음 확인 뒤에만 보입니다')
    w(4)

with Scene('s6') as s:
    s.cap('구조대에 인계한 뒤 차 안에서 끝냅니다. 보호자가 원격으로 끄지 못합니다')
    w(2)
    tap('구조 안내 종료')
    w(3)
    s.cap('건강 정보가 다시 가려지고 보호자에게 종료를 알립니다 [가상 전송]')
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
    s.cap('다른 경우: 신호 대기였다면. [시뮬레이션] 기어 D, 속도 0, 조작 없음')
    car('stop')
    wait_or_stop('-> ASK1', 25)
    s.cap('[음성 출력] "괜찮으세요?..."')
    w(6)
    s.cap('[시뮬레이션] 운전자의 답 "괜찮아요"를 합성 음성 파일로 입력')
    say('ok')
    wait_or_stop('ASK1 -> IDLE', 12)
    s.cap('[음성 출력] "확인했습니다. 안내를 멈춥니다." 아무것도 전송하지 않습니다')
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
