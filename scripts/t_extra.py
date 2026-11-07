"""암호화 저장, 전송 실패, 장시간 유지, 로그를 확인한다. debug 빌드에서 실행한다."""
import sys, os, subprocess
sys.path.insert(0, os.path.dirname(__file__))
from h import *

WORDS = ['심장', '가상의 약', '000-0000', '가족 B', '운전자 A', '글러브박스']
session(); adb('shell', 'input', 'keyevent', '224')
print('== 앱 폴더 평문 검색')
cmd = 'cd /data/user/10/kr.driveguard; find . -type f | head -40; echo --; grep -rl -e 심장 -e 000-0000 -e 운전자 -e 글러브박스 . ; echo done'
print(adb('shell', f"run-as {PKG} --user 10 sh -c '{cmd}'")[:1200])
print(adb('shell', f"run-as {PKG} --user 10 sh -c 'od -c no_backup/profile.enc | head -2'")[:240])
print('== logcat 평문 검색')
x = adb('logcat', '-d')
print({w_: sum(1 for l in x.splitlines() if w_ in l) for w_ in WORDS})
print('앱 로그 종류', sorted({re.sub(r'[0-9.]+', '#', l)[:40] for l in logs()})[:30])
