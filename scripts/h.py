"""에뮬레이터 시험 도우미: Virtual Console, 화면 조작, 앱 로그, 가상 보호자 서버."""
import json, re, subprocess, time, urllib.request

VC = 'http://127.0.0.1:8088'
GUARDIAN = 'http://127.0.0.1:8787'
PKG = 'kr.driveguard'
w = time.sleep


def sh(*a, t=40):
    return subprocess.run(a, capture_output=True, text=True, errors='replace', timeout=t).stdout


def adb(*a, t=40):
    return sh('adb', *a, t=t)


def put(path):
    urllib.request.urlopen(urllib.request.Request(VC + path, method='PUT'), timeout=6).read()


def session():
    urllib.request.urlopen(urllib.request.Request(VC + '/browser/session?client=cli', method='POST'), timeout=6).read()


def vc():
    return json.load(urllib.request.urlopen(VC + '/vehicle/state', timeout=6))


def car(kind):
    """park, stop(기어 D 속도 0), drive(기어 D 30 km/h)"""
    if kind == 'park':
        put('/vehicle/controls?speed=0&accelerator=0&brake=0&steering=0')
        put('/vehicle/gear?gear=Park')
    elif kind == 'stop':
        put('/vehicle/gear?gear=Drive')
        put('/vehicle/controls?speed=0&accelerator=0&brake=0&steering=0')
    elif kind == 'drive':
        put('/vehicle/gear?gear=Drive')
        put('/vehicle/controls?speed=30&accelerator=10&brake=0&steering=0')


def reset_body():
    """창문, 잠금, 실내등, 미등을 처음 상태로 되돌린다."""
    put('/vehicle/body?window_fl=0&doors_locked=1&cabin_light=0')
    put('/vehicle/lights?feature=headlamp_mode&value=Off')


def view():
    d = vc()
    b, l = d['body'], d['lights']
    return dict(gear=(d['gear']['actual'] or {}).get('value'), speed=d['controls']['speed_kph'], window=b['windows']['front_left'],
                locked=b['door_locks']['all'], cabin=b['cabin_light']['on'], tail=l['headlamp_mode'])


def alerts():
    return json.load(urllib.request.urlopen(GUARDIAN + '/alerts', timeout=4))


def alert_kinds():
    return [a['kind'] for a in alerts()]


def reset_alerts():
    urllib.request.urlopen(urllib.request.Request(GUARDIAN + '/reset', method='POST'), timeout=4).read()


def clear():
    adb('logcat', '-c')


def logs(pattern=None):
    out = []
    for line in adb('logcat', '-d', '-s', 'DriveGuard:I').splitlines():
        m = re.search(r'DriveGuard\s*:\s*(.*)', line)
        if m and (not pattern or re.search(pattern, m.group(1))):
            out.append(m.group(1))
    return out


def wait_log(pattern, seconds):
    end = time.time() + seconds
    while time.time() < end:
        if logs(pattern):
            return True
        w(0.5)
    return False


def say(name):
    """debug 빌드 전용: 합성 음성 파일을 인식 엔진에 넣는다."""
    adb('shell', 'am', 'broadcast', '--user', '10', '-a', 'kr.driveguard.debug.SAY', '-p', PKG, '--es', 'file', name + '.pcm')


def nodes():
    adb('shell', 'uiautomator', 'dump', '/sdcard/u.xml')
    x = adb('exec-out', 'cat', '/sdcard/u.xml')
    return [(m.group(1), *map(int, m.groups()[1:])) for m in
            re.finditer(r'<node [^>]*?text="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x)]


def texts():
    return [n[0] for n in nodes() if n[0]]


def tap(text):
    ns = nodes()
    ms = [n for n in ns if n[0] == text] or [n for n in ns if text in n[0]]
    if not ms:
        print('NOT FOUND', text, flush=True)
        return False
    _, a, b, c, d = ms[0]
    adb('shell', 'input', 'tap', str((a + c) // 2), str((b + d) // 2))
    return True


def swipe_up():
    adb('shell', 'input', 'swipe', '1500', '950', '1500', '250', '300')
    w(0.8)


def hide_keyboard():
    if 'mInputShown=true' in adb('shell', 'dumpsys', 'input_method'):
        adb('shell', 'input', 'keyevent', '4')
        w(1.5)


def app():
    adb('shell', 'am', 'start', '--user', '10', '-n', PKG + '/.ui.MainActivity')


def navi_front():
    adb('shell', 'am', 'start', '--user', '10', '-n', 'ai.umos.maps.android.navigation.app/.MainActivity')


def alive():
    return adb('shell', 'pidof', PKG).strip()
