"""녹화 조각과 자막으로 영상을 만든다. 사용: build_video.py <작업폴더> <출력폴더> [timeline 파일 이름]
음악과 외부 소재를 넣지 않는다. 소리는 녹화에 담긴 앱 음성뿐이다. 화면을 줄여 위아래 띠에 자막을 넣어 앱 화면을 가리지 않는다."""
import json, os, re, subprocess, sys

V, O = sys.argv[1], sys.argv[2]
OUT = os.path.join(V, 'out')
os.makedirs(OUT, exist_ok=True)
FONT = 'Apple SD Gothic Neo'
d = json.load(open(os.path.join(V, sys.argv[3] if len(sys.argv) > 3 else 'timeline.json'), encoding='utf-8'))
scenes = {s['name']: s for s in d['scenes']}
items = [
    ('card', 'title', 4, ['{\\b1\\fs96}DriveGuard', '{\\fs46}차가 멈춘 뒤 응답을 확인하고 보호자와 구조자를 연결하는 앱', '',
                          '{\\fs38}시나리오: 심장 질환 이력이 있는 운전자 A, 동승자 C, 보호자 B']),
    ('clip', 's1'), ('clip', 's2'),
    ('still', 'g1', 3, '보호자 화면: 위치 공유 도착'),
    ('clip', 's3'), ('clip', 's4'),
    ('still', 'g2', 5, '보호자 화면: 119 신고 요청과 신고 때 알려 줄 위치·지도·정차 시각·마지막 응답 시각'),
    ('clip', 's5'), ('clip', 's6'),
    ('still', 'g3', 3, '보호자 화면: 종료 전달'),
    ('clip', 's7'),
    ('card', 'end', 4, ['{\\b1\\fs50}Pleos Connect Emulator에서 시연', '',
                        '{\\fs38}사용한 SDK: TextToSpeech, SpeechToText, NaviHelper, Vehicle, FusedLocation',
                        '{\\fs38}차량 속성: 기어, 속도 (Android Automotive)']),
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


# 터치 표시: 누른 자리에 반투명 원이 커지며 사라진다. 녹화 좌표(2560x1440)를 영상 안의 앱 화면 위치로 옮긴다.
# 음수 좌표를 쓰면 libass가 가운데를 어긋나게 잡으므로 0~68 범위로 그린다.
RING = 'm 34 0 b 53 0 68 15 68 34 b 68 53 53 68 34 68 b 15 68 0 53 0 34 b 0 15 15 0 34 0'


# 누른 순간의 프레임을 이 시간만큼 멈춰 두고 그 위에만 원을 그린다. 원이 다음 화면에 겹치지 않게 하기 위해서다.
FREEZE = 0.8


def touch(t, px, py):
    x = round(120 + px * 1680 / 2560)
    y = round(35 + py * 945 / 1440)
    style = ('{\\an5\\pos(%d,%d)\\bord6\\shad0\\3c&H00D7FF&\\1c&H00D7FF&\\1a&HB0&\\3a&H10&\\fscx55\\fscy55'
             '\\t(0,300,\\fscx125\\fscy125)\\t(420,780,\\alpha&HFF&)\\p1}' % (x, y))
    return f'Dialogue: 1,{ts(t)},{ts(t + FREEZE)},Cap,,0,0,0,,{style}{RING}{{\\p0}}\n'


def freeze_graph(cuts, dur, vf):
    """원본을 누른 시각마다 잘라 그 순간의 프레임을 FREEZE초 동안 멈춘 조각을 끼운다. 멈춘 동안은 무음이다."""
    n = len(cuts) + 1
    g = [f'[0:v]fps=30,split={2 * n - 1}' + ''.join(f'[s{i}]' for i in range(2 * n - 1)),
         f'[0:a]aformat=sample_rates=48000:channel_layouts=stereo,apad=whole_dur={dur},asplit={n}' + ''.join(f'[r{i}]' for i in range(n))]
    order, prev = [], 0.0
    for i, c in enumerate(cuts + [dur]):
        g.append(f'[s{2 * i}]trim=start={prev}:end={c},setpts=PTS-STARTPTS[v{i}]')
        g.append(f'[r{i}]atrim=start={prev}:end={c},asetpts=PTS-STARTPTS[a{i}]')
        order.append(f'[v{i}][a{i}]')
        if i < len(cuts):
            g.append(f'[s{2 * i + 1}]trim=start={c}:duration=0.04,setpts=PTS-STARTPTS,tpad=stop_mode=clone:stop_duration={FREEZE},trim=duration={FREEZE}[f{i}]')
            g.append(f'aevalsrc=0|0:s=48000:d={FREEZE},aformat=channel_layouts=stereo[z{i}]')
            order.append(f'[f{i}][z{i}]')
        prev = c
    g.append(''.join(order) + f'concat=n={len(order)}:v=1:a=1[cv][ca]')
    g.append('[ca]volume=14dB,alimiter=limit=0.9[aout]')
    return ';'.join(g)


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
        cuts = sorted(t0 for t0, _, _ in s.get('taps', []) if 0 < t0 < dur)
        # 멈춘 시간만큼 뒤 시각을 민다. 누른 시각에 시작하는 자막은 멈춤이 끝난 뒤에 바뀐다.
        shift = lambda x: x + FREEZE * sum(1 for c in cuts if c <= x)
        pairs = [(max(0, c[0] - 0.3), min(dur, caps[i + 1][0] - 0.3) if i + 1 < len(caps) else dur, c[1]) for i, c in enumerate(caps)]
        pairs = [(shift(x), shift(y), t) for x, y, t in pairs]
        raw_dur, dur = dur, round(dur + FREEZE * len(cuts), 3)
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
    if kind == 'clip':
        for t0, px, py in scenes[name].get('taps', []):
            if t0 in cuts:
                ev += touch(t0 + FREEZE * cuts.index(t0), px, py)
    open(a, 'w', encoding='utf-8').write(HEAD + ev)
    vf = f'fps=30,ass={a}' if kind == 'card' else f'scale=1680:945:force_original_aspect_ratio=decrease,pad=1920:1080:(ow-iw)/2:35:color=0x101418,fps=30,ass={a}'
    # 소리는 에뮬레이터 녹화에 담긴 앱 음성뿐이다. 카드와 정지 화면은 무음을 넣어 이어 붙인다.
    enc = ['-c:v', 'libx264', '-preset', 'medium', '-crf', '20', '-pix_fmt', 'yuv420p', '-video_track_timescale', '30000', '-c:a', 'aac', '-b:a', '128k', '-ac', '2']
    if kind == 'clip':
        # 1단계: 멈춤을 끼운 원본을 만든다. 2단계: 자막과 터치 표시를 입힌다. 한 그래프로 하면 자막 시각이 어긋났다.
        frozen = os.path.join(OUT, name + '_frozen.mp4')
        graph = freeze_graph(cuts, raw_dur, '')
        subprocess.run(['ffmpeg', '-v', 'error', '-y', *inp, '-filter_complex', graph, '-map', '[cv]', '-map', '[aout]', '-t', str(dur), '-map_metadata', '-1',
                        '-c:v', 'libx264', '-preset', 'medium', '-crf', '12', '-pix_fmt', 'yuv420p', '-c:a', 'aac', '-b:a', '192k', frozen], check=True)
        subprocess.run(['ffmpeg', '-v', 'error', '-y', '-i', frozen, '-map', '0:v:0', '-map', '0:a:0', '-t', str(dur), '-map_metadata', '-1', '-vf', vf, *enc, m], check=True)
    else:
        audio = ['-f', 'lavfi', '-t', str(dur), '-i', 'anullsrc=r=48000:cl=stereo', '-map', '0:v:0', '-map', '1:a:0']
        subprocess.run(['ffmpeg', '-v', 'error', '-y', *inp, *audio, '-t', str(dur), '-map_metadata', '-1', '-vf', vf, *enc, m], check=True)
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
