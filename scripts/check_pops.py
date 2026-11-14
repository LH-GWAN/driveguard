#!/usr/bin/env python3
"""영상 소리의 튐 검사. 앱 TTS 음성은 약 11kHz 위에 소리가 거의 없다(마찰음 포함). 녹화가 깨진 곳은 짧은 광대역 순간음이 생겨
11.5~16kHz에 에너지가 나타난다. 2.7ms 창마다 이 대역 에너지를 그 대사의 말소리 크기(0.3~4kHz 95백분위)와 비교해 -55dB를 넘는 창을 세고,
100ms 안에 그런 창이 15개(20%) 이상이면 튐 구간으로 센다.
기준 표본(이전 영상): 134.6~137.8초 '운전자가 응답하지 않습니다'(비정상) 12구간 이상, 138.6~141.8초 '기어를 P로 넣어 주세요'(정상) 1구간.
사용: check_pops.py <영상> [시작 끝 ...]  인자가 영상뿐이면 말소리 구간을 찾아 구간마다 센다."""
import subprocess, sys
import numpy as np

SR = 48000
N, H = 128, 64
WIN = 75  # 100ms


def load(path, s=None, e=None):
    cmd = ['ffmpeg', '-v', 'error']
    if s is not None:
        cmd += ['-ss', str(s), '-to', str(e)]
    cmd += ['-i', path, '-vn', '-ac', '1', '-ar', str(SR), '-f', 'f32le', '-']
    return np.frombuffer(subprocess.run(cmd, capture_output=True, check=True).stdout, dtype=np.float32).astype(np.float64)


def pops(x):
    fr = np.lib.stride_tricks.sliding_window_view(x, N)[::H] * np.hanning(N)
    p = np.abs(np.fft.rfft(fr, axis=1)) ** 2
    f = np.fft.rfftfreq(N, 1 / SR)
    hf = p[:, (f >= 11500) & (f < 16000)].sum(1)
    sp = p[:, (f >= 300) & (f < 4000)].sum(1)
    ref = np.percentile(sp, 95)
    flag = (10 * np.log10(hf / ref + 1e-20) > -55) & (sp > ref * 1e-3)
    n = len(flag) // WIN
    cnt = flag[:n * WIN].reshape(n, WIN).sum(1)
    return [round(i * WIN * H / SR, 1) for i in np.where(cnt >= 15)[0]]


def speech_spans(path):
    r = subprocess.run(['ffmpeg', '-hide_banner', '-i', path, '-vn', '-af', 'silencedetect=noise=-40dB:d=0.6', '-f', 'null', '-'],
                       capture_output=True, text=True).stderr
    import re
    st = [float(v) for v in re.findall(r'silence_start: ([\d.]+)', r)]
    en = [float(v) for v in re.findall(r'silence_end: ([\d.]+)', r)]
    out, prev = [], 0.0
    for i, s0 in enumerate(st):
        if s0 > prev + 0.1:
            out.append((prev, s0))
        prev = en[i] if i < len(en) else 1e9
    return out


if __name__ == '__main__':
    path = sys.argv[1]
    pairs = [(float(sys.argv[i]), float(sys.argv[i + 1])) for i in range(2, len(sys.argv) - 1, 2)] or speech_spans(path)
    total = 0
    for s, e in pairs:
        ev = pops(load(path, s, e))
        total += len(ev)
        print(f'{s:7.2f}~{e:7.2f}초  튐 구간 {len(ev)}  {[round(s + t, 1) for t in ev]}')
    print('합계', total)
