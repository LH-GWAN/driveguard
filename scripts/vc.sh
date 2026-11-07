#!/bin/bash
# Virtual Console 조작. 사용: vc.sh session | park | drive | stop | brake <0~100> | steer <각도> | turn <Off|BlinkLeft|BlinkRight> | state
# session은 켜 둔 채 다른 터미널에서 나머지를 쓴다. 브라우저로 콘솔을 열어 두면 session은 필요 없다.
# drive는 기어 D 30 km/h, stop은 기어 D 속도 0, park는 기어 P 속도 0이다.
V=http://127.0.0.1:8088
adb forward tcp:8088 tcp:8088 >/dev/null
controls() { curl -sS -m 5 -X PUT "$V/vehicle/controls?speed=$1&accelerator=0&brake=$2&steering=$3" -o /dev/null; }
case "${1:-}" in
  session) while true; do curl -sS -m 2 -X POST "$V/browser/session?client=cli" -o /dev/null; sleep 1; done ;;
  drive) curl -sS -m 5 -X PUT "$V/vehicle/gear?gear=Drive" -o /dev/null; controls 30 0 0 ;;
  stop) curl -sS -m 5 -X PUT "$V/vehicle/gear?gear=Drive" -o /dev/null; controls 0 0 0 ;;
  park) controls 0 0 0; curl -sS -m 5 -X PUT "$V/vehicle/gear?gear=Park" -o /dev/null ;;
  brake) controls 0 "${2:?값}" 0 ;;
  steer) controls 0 0 "${2:?각도}" ;;
  turn) curl -sS -m 5 -X PUT "$V/vehicle/turn-signal?mode=${2:?모드}" -o /dev/null ;;
  state) curl -sS -m 5 "$V/vehicle/state"; echo ;;
  *) echo "사용: vc.sh session|park|drive|stop|brake|steer|turn|state" >&2; exit 2 ;;
esac
