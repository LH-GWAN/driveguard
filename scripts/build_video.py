"""녹화 조각과 자막으로 영상을 만든다. 사용: build_video.py <작업폴더> <출력폴더>
음악과 외부 소재를 넣지 않는다. 소리는 녹화에 담긴 앱 음성뿐이다. 화면을 줄여 위아래 띠에 자막을 넣어 앱 화면을 가리지 않는다."""
import json, os, re, subprocess, sys

V, O = sys.argv[1], sys.argv[2]
OUT = os.path.join(V, 'out')
os.makedirs(OUT, exist_ok=True)
FONT = 'Apple SD Gothic Neo'
d = json.load(open(os.path.join(V, 'timeline.json'), encoding='utf-8'))
scenes = {s['name']: s for s in d['scenes']}
TOP = 'DriveGuard · 가상 데이터 · Pleos Connect Emulator · 실차 미시험 · 구조를 보장하지 않음'
items = [
    ('card', 'title', 6, ['{\\b1\\fs96}DriveGuard', '{\\fs46}차가 멈춘 뒤 응답을 확인하고, 보호자와 구조자를 연결하도록 설계한 앱', '',
                          '{\\b1\\fs44}가상 데이터 · Pleos Connect Emulator 결과 · 실차 미시험',
                          '{\\fs38}구조를 보장하지 않으며 진단·예측을 하지 않고 119를 대신하지 않습니다',
                          '{\\fs36}가상 시나리오: 심장 질환 이력 운전자 A, 동승자 C, 보호자 B',
                          '{\\fs34}자막의 [실제 SDK] [시뮬레이션] [가상 전송] [음성 출력]으로 구분합니다']),
    ('clip', 's1'), ('clip', 's2'),
    ('still', 'g1', 4, '[가상 전송] 보호자 화면(가상, 로컬 서버): 위치 공유. 병력은 보내지 않습니다. 실제 문자·알림 경로는 미시험'),
    ('clip', 's3'), ('clip', 's4'),
    ('still', 'g2', 4, '[가상 전송] 보호자 화면(가상): 위치와 정차 시각. "자동 메시지이며 진단이 아닙니다"'),
    ('clip', 's5'), ('clip', 's6'),
    ('still', 'g3', 4, '[가상 전송] 보호자 화면(가상): 종료 전달'),
    ('clip', 's7'),
    ('card', 'end', 12, ['{\\b1\\fs50}이 영상의 범위', '',
                         '{\\fs34}실제 SDK 호출: TextToSpeech·SpeechToText(ON_DEVICE), NaviHelper, Vehicle(창문, 잠금, 조명, 글러브박스, 조향, 브레이크, 방향지시등),',
                         '{\\fs34}AAOS 기어·속도, FusedLocation. 음성 입력 주입을 위해 debug 빌드로 녹화했습니다',
                         '{\\fs34}시뮬레이션: 차량 값(Virtual Console), 위치, 음성 입력(합성 음성 파일)  ·  가상 전송: 보호자 연락(로컬 서버)',
                         '{\\fs34}미시험: 실제 마이크, 실제 전송 경로, 시동 시 자동 시작, 실차, App Market 승인, 감지 성능(오탐·미탐)',
                         '{\\fs34}한계: 기어가 P가 아니면 잠금 해제·조명·구조 화면이 막힙니다. 앱은 기어를 바꾸지 못하고 비상등을 켜지 못합니다',
                         '{\\fs34}한계: 응답 없음 확정 뒤에는 깨어난 운전자의 조작으로 취소되지 않고, 말하거나 기어 P에서 화면으로 취소합니다',
                         '{\\fs34}구조 성공을 보여 주는 영상이 아닙니다. 의료기기 해당 여부는 판단받지 않았습니다']),
]


def ts(t):
    return f'{int(t // 3600)}:{int(t % 3600 // 60):02d}:{t % 60:05.2f}'


HEAD = f"""[Script Info]
ScriptType: v4.00+
PlayResX: 1920
PlayResY: 1080
WrapStyle: 0
[V4+ Styles]
Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
Style: Cap,{FONT},31,&H00FFFFFF,&H00FFFFFF,&H00101418,&H00101418,1,0,0,0,100,100,0,0,1,0,0,5,40,40,0,1
Style: Top,{FONT},22,&H00E6E6E6,&H00E6E6E6,&H00101418,&H00101418,0,0,0,0,100,100,0,0,1,0,0,5,0,0,0,1
Style: Card,{FONT},40,&H00FFFFFF,&H00FFFFFF,&H00000000,&H00000000,0,0,0,0,100,100,0,0,1,0,0,5,70,70,0,1
[Events]
Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
"""


def dia(a, b, st, tx):
    return f'Dialogue: 0,{ts(a)},{ts(b)},{st},,0,0,0,,{tx}\n'


def duration(path):
    return float(subprocess.run(['ffprobe', '-v', 'error', '-show_entries', 'format=duration', '-of', 'csv=p=0', path], capture_output=True, text=True).stdout)


parts, srt, T = [], [], 0.0
for it in items:
    kind, name = it[0], it[1]
    a, m = os.path.join(OUT, name + '.ass'), os.path.join(OUT, name + '.mp4')
    ev = ''
    if kind == 'clip':
        s = scenes[name]
        src = os.path.join(V, 'clips', name + '.webm')
        dur = round(min(s['dur'], duration(src)), 3)
        inp = ['-i', src]
        caps = s['caps']
        pairs = [(max(0, c[0] - 0.3), min(dur, caps[i + 1][0] - 0.3) if i + 1 < len(caps) else dur, c[1]) for i, c in enumerate(caps)]
    elif kind == 'still':
        dur = it[2]
        inp = ['-loop', '1', '-t', str(dur), '-i', os.path.join(V, 'stills', name + '.png')]
        pairs = [(0, dur, it[3])]
    else:
        dur = it[2]
        inp = ['-f', 'lavfi', '-t', str(dur), '-i', 'color=c=0x101418:s=1920x1080:r=30']
        pairs = []
        ev += dia(0, dur, 'Card', '\\N'.join(l if l else ' ' for l in it[3]))
        srt.append((T, T + dur, '\n'.join(re.sub(r'\{[^}]*\}', '', l) for l in it[3] if l)))
    for x, y, t in pairs:
        ev += dia(x, y, 'Cap', '{\\pos(960,1030)}' + t)
        srt.append((T + x, T + y, t))
    if kind != 'card':
        ev += dia(0, dur, 'Top', '{\\pos(960,17)}' + TOP)
    open(a, 'w', encoding='utf-8').write(HEAD + ev)
    vf = f'fps=30,ass={a}' if kind == 'card' else f'scale=1680:945:force_original_aspect_ratio=decrease,pad=1920:1080:(ow-iw)/2:35:color=0x101418,fps=30,ass={a}'
    # 소리는 에뮬레이터 녹화에 담긴 앱 음성뿐이다. 카드와 정지 화면은 무음을 넣어 이어 붙인다.
    if kind == 'clip':
        audio = ['-map', '0:v:0', '-map', '0:a:0', '-af', 'volume=14dB,alimiter=limit=0.9,aresample=48000,apad']
    else:
        audio = ['-f', 'lavfi', '-t', str(dur), '-i', 'anullsrc=r=48000:cl=stereo', '-map', '0:v:0', '-map', '1:a:0']
    subprocess.run(['ffmpeg', '-v', 'error', '-y', *inp, *audio, '-t', str(dur), '-map_metadata', '-1', '-vf', vf, '-c:v', 'libx264', '-preset', 'medium', '-crf', '20', '-pix_fmt', 'yuv420p', '-video_track_timescale', '30000', '-c:a', 'aac', '-b:a', '128k', '-ac', '2', m], check=True)
    parts.append(m)
    T += duration(m)
lst = os.path.join(OUT, 'list.txt')
open(lst, 'w').write(''.join(f"file '{p}'\n" for p in parts))
mp4 = os.path.join(O, 'DriveGuard_시연영상.mp4')
subprocess.run(['ffmpeg', '-v', 'error', '-y', '-f', 'concat', '-safe', '0', '-i', lst, '-c', 'copy', '-map_metadata', '-1', '-movflags', '+faststart', mp4], check=True)


def st(t):
    return f'{int(t // 3600):02d}:{int(t % 3600 // 60):02d}:{int(t % 60):02d},{int(round((t - int(t)) * 1000)) % 1000:03d}'


open(os.path.join(O, 'DriveGuard_시연영상_자막.srt'), 'w', encoding='utf-8').write(
    ''.join(f'{i + 1}\n{st(a)} --> {st(b)}\n{t}\n\n' for i, (a, b, t) in enumerate(srt)))
print('total', round(T, 1), 'facts', d.get('facts'))
