#!/usr/bin/env python3
"""장면 소리를 에뮬레이터 오디오 출력 캡처(WAV)에서 가져와 녹화 시각에 맞춰 놓는다.
에뮬레이터 자체 녹화(webm)의 소리에는 녹화 경로에서 생기는 광대역 순간음(튐)이 섞인다. QEMU 모니터의 `wavcapture`로 받은 오디오 출력에는 이 튐이 없다.
(`-audio wav` 백엔드도 같은 소리를 주지만 음성 인식이 수 초 느려져 쓰지 않는다.) 출력 자체의 짧은 끊김은 fix_audio로 메운다.
다만 WAV에는 소리가 나지 않는 동안이 빠져 있어 시각을 알 수 없다. 그래서 녹화(webm)에서 소리 구간마다 시각을 얻고,
같은 소리를 WAV에서 상호상관으로 찾아 그 시각에 놓는다. 찾지 못한 구간은 녹화 소리를 끊김만 메워 쓴다(fix_audio).
사용: clean_audio.py <녹화.webm> <에뮬레이터.wav> <찾기 시작 바이트> <출력.wav>"""
import subprocess, sys
import numpy as np
import fix_audio

SR = 48000
WAV_RATE, WAV_CH = 44100, 2
HEADER = 44


def read_wav(path, start_byte, seconds):
    """에뮬레이터 WAV(44.1kHz 16비트 스테레오)의 일부를 48kHz 스테레오 float로 읽는다."""
    start = max(HEADER, start_byte - (start_byte - HEADER) % 4)
    with open(path, 'rb') as f:
        f.seek(start)
        raw = f.read(int(seconds * WAV_RATE) * 4)
    y = subprocess.run(['ffmpeg', '-v', 'error', '-f', 's16le', '-ar', str(WAV_RATE), '-ac', '2', '-i', '-', '-af', 'aresample=48000:resampler=soxr',
                        '-f', 'f32le', '-ac', '2', '-'], input=raw, capture_output=True, check=True).stdout
    return np.frombuffer(y, dtype=np.float32).reshape(-1, 2).astype(np.float64)


def spans_of(mono, floor=10 ** (-62 / 20), gap=0.25, minlen=0.08):
    w = SR // 100
    n = len(mono) // w
    rms = np.sqrt((mono[:n * w].reshape(n, w) ** 2).mean(1))
    on = rms > floor
    out, i = [], 0
    while i < n:
        if on[i]:
            j = i
            while j < n and (on[j] or on[j:j + int(gap * 100)].any()):
                j += 1
            if (j - i) / 100 >= minlen:
                out.append((i * w, j * w))
            i = j
        else:
            i += 1
    return out


def best_match(tmpl, hay):
    """hay 안에서 tmpl과 가장 닮은 위치와 정규화 상관값."""
    if len(hay) < len(tmpl):
        return 0, 0.0
    n = 1 << int(np.ceil(np.log2(len(hay) + len(tmpl))))
    c = np.fft.irfft(np.fft.rfft(hay, n) * np.conj(np.fft.rfft(tmpl, n)), n)[:len(hay) - len(tmpl) + 1]
    e = np.convolve(hay ** 2, np.ones(len(tmpl)), mode='valid')
    ncc = c / (np.sqrt(e * (tmpl ** 2).sum()) + 1e-12)
    k = int(np.argmax(ncc))
    return k, float(ncc[k])


def place(webm, wav, wav_from, out, report=print):
    rec = fix_audio.decode(webm)  # 녹화 시각대로 무음을 채운 소리
    mono = rec.mean(1)
    dur = len(rec) / SR
    # 소리가 없던 동안은 WAV에 빠져 있으므로 장면 길이만큼만 읽어도 충분하다. 앞뒤 여유를 둔다.
    src = read_wav(wav, wav_from - 8 * WAV_RATE * 4, dur + 20)
    smono = src.mean(1)
    fixed = fix_audio.repair(rec, fix_audio.find_gaps(mono))
    out_x = np.zeros_like(rec)
    cursor, results = 0, []
    pad = int(0.08 * SR)
    for a, b in spans_of(mono):
        a0, b0 = max(0, a - pad), min(len(rec), b + pad)
        tmpl = mono[a0:b0]
        k, score = best_match(tmpl, smono[cursor:])
        k += cursor
        ok = score >= 0.7 and k + (b0 - a0) <= len(src)
        seg = src[k:k + (b0 - a0)] if ok else fixed[a0:b0]
        if ok:
            # 크기를 녹화와 같게 맞춘다(뒤 단계의 음량 보정을 그대로 쓰기 위해).
            g = np.sqrt((tmpl ** 2).mean() / ((seg.mean(1) ** 2).mean() + 1e-20))
            seg = seg * g
            cursor = k + (b0 - a0)
        fade = min(240, len(seg) // 4)
        win = np.ones(len(seg))
        win[:fade] = np.linspace(0, 1, fade)
        win[-fade:] = np.linspace(1, 0, fade)
        out_x[a0:b0] += seg * win[:, None]
        results.append((round(a0 / SR, 2), round(b0 / SR, 2), round(score, 3), 'WAV' if ok else '녹화 보정'))
    # 에뮬레이터 오디오 출력 자체에도 1ms 안팎의 끊김(언더런)이 있다. 녹화 경로의 튐은 없어졌으니 이 끊김만 메운다.
    gaps = fix_audio.find_gaps(out_x.mean(1))
    out_x = fix_audio.repair(out_x, gaps)
    jumps = fix_audio.find_jumps(out_x.mean(1))
    out_x = fix_audio.repair(out_x, jumps, margin=0)
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-f', 'f32le', '-ar', str(SR), '-ac', '2', '-i', '-', '-c:a', 'pcm_f32le', out],
                   input=out_x.astype(np.float32).tobytes(), check=True)
    results.append(('메운 끊김', len(gaps) + len(jumps)))
    report(results)
    return results


if __name__ == '__main__':
    place(sys.argv[1], sys.argv[2], int(sys.argv[3]), sys.argv[4])
