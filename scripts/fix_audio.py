#!/usr/bin/env python3
"""에뮬레이터 녹화 소리의 끊김을 메운다.
에뮬레이터 자체 녹화는 앱 음성 도중 30~50ms마다 약 1ms씩 소리가 0으로 빠진다(오디오 버퍼 언더런). 들으면 '팍' 하고 튄다.
빠진 구간을 앞뒤 소리에서 선형 예측(LPC)으로 이어 그린 뒤 겹쳐 채운다. 소리 길이와 시각은 바꾸지 않는다.
사용: fix_audio.py <녹화.webm> <출력.wav>   (48kHz 스테레오 float, 녹화 시각대로 빈 구간은 무음으로 채움)"""
import subprocess, sys
import numpy as np

SR = 48000


def decode(path):
    # 녹화 소리는 소리가 날 때만 기록되어 사이가 비어 있다. 시각대로 무음을 채운다.
    raw = subprocess.run(['ffmpeg', '-v', 'error', '-i', path, '-vn', '-af', 'aresample=48000:async=1:first_pts=0',
                          '-f', 'f32le', '-ac', '2', '-ar', str(SR), '-'], capture_output=True, check=True).stdout
    return np.frombuffer(raw, dtype=np.float32).reshape(-1, 2).astype(np.float64)


def find_gaps(mono):
    """앞뒤로 소리가 있는데 갑자기 0 근처로 떨어져 0.17~5ms 머무는 구간."""
    a = np.abs(mono)
    gaps, i, n = [], 0, len(mono)
    while i < n:
        if a[i] < 0.0015:
            j = i
            while j < n and a[j] < 0.0015:
                j += 1
            if 8 <= j - i <= 240 and i > 600 and j + 600 < n:
                before, after = a[i - 96:i].mean(), a[j:j + 96].mean()
                flat = a[i:j].mean() + 1e-6
                edge = max(a[i - 3:i].max(), a[j:j + 3].max())
                # 앞뒤 소리가 평평한 구간보다 훨씬 크고 경계에서 한 번에 떨어진 경우만. 자연스러운 잦아듦은 제외된다.
                if min(before, after) > max(0.002, 8 * flat) and edge > 6 * flat:
                    gaps.append((i, j))
            i = j
        else:
            i += 1
    return gaps


def find_jumps(mono):
    """샘플이 빠져 파형이 한 번에 크게 꺾인 곳. 2차 차분이 주변 1ms 블록들의 중앙값보다 10배 넘게 크면 끊긴 이음매로 본다."""
    env = np.convolve(np.abs(mono), np.ones(960) / 960, mode='same')
    speech = env > max(1e-4, np.percentile(env, 99) * 0.05)
    if not speech.any():
        return []
    x = mono / np.percentile(np.abs(mono[speech]), 99.5)
    d2 = np.abs(x[2:] - 2 * x[1:-1] + x[:-2])
    w = 48
    m = len(d2) // w
    blk = d2[:m * w].reshape(m, w)
    peak = blk.max(1)
    pad = np.pad(peak, 10, mode='edge')
    med = np.median(np.lib.stride_tricks.sliding_window_view(pad, 21), axis=1)
    out = []
    for k in np.where((peak > 0.35) & (peak > 10 * med))[0]:
        c = k * w + int(blk[k].argmax()) + 1
        if speech[c] and 600 < c < len(mono) - 600:
            out.append((c - 24, c + 24))
    return out


def lpc(x, order):
    r = np.correlate(x, x, 'full')[len(x) - 1:len(x) + order]
    r[0] *= 1.0001
    a = np.zeros(order + 1); a[0] = 1.0
    e = r[0]
    for k in range(1, order + 1):
        acc = r[k] + np.dot(a[1:k], r[k - 1:0:-1])
        g = -acc / e
        a[1:k + 1] = a[1:k + 1] + g * np.concatenate([a[k - 1:0:-1], [1.0]])
        e *= (1 - g * g)
        if e <= 0:
            break
    return a


def extrapolate(hist, count, order=24):
    a = lpc(hist * np.hanning(len(hist)), order)
    buf = list(hist[-order:])
    out = []
    for _ in range(count):
        v = -np.dot(a[1:], buf[::-1][:order])
        out.append(v)
        buf.append(v)
        buf.pop(0)
    return np.array(out)


def repair(x, gaps, margin=3, ctx=480):
    y = x.copy()
    for i, j in gaps:
        s, e = i - margin, j + margin
        L = e - s
        w = np.linspace(0, 1, L)
        for c in range(x.shape[1]):
            fwd = extrapolate(y[s - ctx:s, c], L)
            bwd = extrapolate(y[e:e + ctx, c][::-1], L)[::-1]
            y[s:e, c] = (1 - w) * fwd + w * bwd
    return y


def main(src, dst):
    x = decode(src)
    gaps = find_gaps(x.mean(1))
    y = repair(x, gaps)
    # 0으로 채워지지 않고 샘플만 빠진 이음매도 같은 방법으로 앞뒤 1ms를 이어 그린다.
    jumps = find_jumps(y.mean(1))
    y = repair(y, jumps, margin=0)
    gaps += jumps
    subprocess.run(['ffmpeg', '-v', 'error', '-y', '-f', 'f32le', '-ar', str(SR), '-ac', '2', '-i', '-', '-c:a', 'pcm_f32le', dst],
                   input=y.astype(np.float32).tobytes(), check=True)
    return len(gaps)


if __name__ == '__main__':
    print('메운 끊김', main(sys.argv[1], sys.argv[2]))
