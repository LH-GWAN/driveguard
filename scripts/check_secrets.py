#!/usr/bin/env python3
"""저장소, 이력, APK, 영상에서 금지값을 찾는다. 금지값 목록은 저장소 밖 ~/.driveguard/forbidden.txt에만 둔다.
일치한 값 자체는 출력하지 않는다. 사용: check_secrets.py [--staged] [--history] [--apk 파일] [--video 파일...]"""
import argparse, os, subprocess, sys, zipfile

parser = argparse.ArgumentParser()
parser.add_argument('--staged', action='store_true')
parser.add_argument('--history', action='store_true')
parser.add_argument('--apk')
parser.add_argument('--video', nargs='*', default=[])
args = parser.parse_args()

path = os.path.expanduser('~/.driveguard/forbidden.txt')
if not os.path.exists(path):
    print('금지값 목록이 없습니다: ~/.driveguard/forbidden.txt', file=sys.stderr)
    sys.exit(2)
words = [w.strip() for w in open(path, encoding='utf8') if w.strip()]
needles = [w.lower().encode('utf8') for w in words]
hits = 0


def scan(label, data):
    global hits
    low = data.lower()
    for i, needle in enumerate(needles):
        if needle in low:
            hits += 1
            print(f'일치: {label} (목록 {i + 1}번째 값)')


def git(*a):
    return subprocess.run(['git', *a], capture_output=True).stdout


if args.staged:
    for name in git('diff', '--cached', '--name-only', '--diff-filter=ACMR', '-z').decode().split('\0'):
        if name:
            scan(name, name.encode() + b'\n' + git('show', ':' + name))
else:
    for name in git('ls-files', '-z', '--cached', '--others', '--exclude-standard').decode().split('\0'):
        if name and os.path.isfile(name):
            scan(name, name.encode() + b'\n' + open(name, 'rb').read())

if args.history:
    scan('커밋 이력', git('log', '--all', '-p', '--format=%an%n%ae%n%cn%n%ce%n%B', '--text'))
if args.apk:
    with zipfile.ZipFile(args.apk) as z:
        for info in z.infolist():
            scan('APK:' + info.filename, z.read(info))
for video in args.video:
    scan('영상:' + os.path.basename(video), open(video, 'rb').read())

print(f'일치 {hits}건')
sys.exit(1 if hits else 0)
