# 두 Pleos 에뮬레이터로 공유 확인하기

2026-11-06. PowerShell 기준. 두 Android 앱 사이의 로컬 HTTP 공유를 확인하는 방법이다. 실차·공식 휴대폰 앱 연동·온라인 배포 검증과는 구분한다.

## 준비된 가상 기기

- A: `Pleos_Connect_v2.1.0` — 기존 기기, 기존 설정 유지.
- B: `Donghaeng_Gil_Peer` — 이번 검증에서 새로 만든 독립 기기. 데이터 위치는 `D:\projects\avds\Donghaeng_Gil_Peer.avd`다.

Android Studio의 Device Manager에서 두 기기의 실행 버튼을 각각 누르면 된다. 같은 기기를 두 번 실행하지 않는다. 두 기기는 SDK 이미지를 공유하지만 앱 데이터와 권한은 별개다. 각각 RAM 4GB를 사용하므로 다른 무거운 프로그램은 닫는 편이 좋다. 자동 검증을 마친 뒤 두 기기는 종료해 두었다.

## 명령으로 실행하기

아래 세 블록을 각각 별도 PowerShell 창에서 실행한다. Device Manager로 이미 실행한 기기를 다시 실행하지 않는다.

```powershell
Set-Location 'D:\projects\donghaeng-gil'
& 'D:\node.exe' 'relay/server.mjs'
```

```powershell
& 'C:\Users\dnrdl\AppData\Local\Android\Sdk\emulator\emulator.exe' -avd 'Pleos_Connect_v2.1.0' -port 5554 -cores 2 -no-snapshot
```

```powershell
& 'C:\Users\dnrdl\AppData\Local\Android\Sdk\emulator\emulator.exe' -avd 'Donghaeng_Gil_Peer' -port 5556 -cores 2 -no-snapshot
```

네 번째 PowerShell 창에서 설치·실행한다. APK가 없거나 소스를 바꿨다면 먼저 프로젝트 지정 빌드 명령을 실행한다.

```powershell
Set-Location 'D:\projects\donghaeng-gil'
# 필요한 경우: & 'D:\projects\build-donghaeng.ps1'
$adb = 'C:\Users\dnrdl\AppData\Local\Android\Sdk\platform-tools\adb.exe'
& $adb devices
$apk = 'D:\projects\donghaeng-gil\app\build\outputs\apk\standard\debug\app-standard-debug.apk'
foreach ($serial in @('emulator-5554', 'emulator-5556')) {
    & $adb -s $serial install -r $apk
    & $adb -s $serial shell am start --user 10 -n 'io.demo.companion/.MainActivity'
}
```

이번 기기는 Android 사용자 10이었다. 다른 기기에서는 `adb -s <기기번호> shell am get-current-user`로 확인한다. Device Manager가 다른 포트를 배정했다면 `adb devices`에 나온 번호를 사용한다. 두 대 연결 시 ADB 명령에 반드시 `-s`로 기기를 지정한다.

## 화면에서 확인할 순서

1. 두 기기를 P·속도 0으로 유지한다. 처음 사용하는 기기에서는 앱의 위치·차량 상태 접근을 허용하고 내비를 한 번 실행한다. 공유 시작 때 알림 권한 창이 나타나면 허용 후 공유 시작을 다시 누른다.
2. 두 기기 내비에서 목적지를 설정하고 경로 안내를 시작한다. GPS가 목적지 주변의 유효한 최신 위치여야 ETA가 표시된다. 목적지만 넣고 위치 조건을 충족하지 않으면 ETA를 임의로 만들지 않는다.
3. 동행길 → 동행 그룹에서 양쪽 서버 주소를 `http://10.0.2.2:8787`, 이름을 각각 `VehicleA`, `VehicleB`로 지정하고 서버 연결을 누른다. 두 에뮬레이터 모두 같은 PC의 중계 서버에 접속한다.
4. A가 그룹 만들기 → A의 초대 코드를 B에 입력 → B가 참여 요청 → A가 VehicleB 참여 승인. 승인 전에는 B에 차량 정보가 보이지 않아야 한다.
5. 승인만으로 공유가 시작되지 않는다. A와 B가 각각 공유 시작 → 전송 범위 확인 → 위치 공유 시작을 선택한다. 각자의 지속 알림이 표시된다.
6. 양쪽 화면에서 서로의 주소·목적지·ETA·남은 거리와 최근 갱신 시간을 확인한다. Debug 좌표는 개발 확인용이다.
7. 양쪽에서 메시지를 하나씩 보낸다. '앱 수신 확인'은 기기의 수신이고, 받은 쪽이 메시지 확인을 누른 뒤의 '상대가 확인'은 별도 확인 응답이다.
8. A가 VehicleB 내보내기 → 확인. B는 퇴장·공유 중지 안내를 표시하고 위치·ETA와 공유 알림을 제거해야 한다. A에서도 B의 위치가 없어야 한다.

이번 자동 검증에서는 판교 부근 시험 GPS를 주입하고 Debug 진단 경로를 사용했다. SDK가 반환한 약 8분·1.8km를 실제 HTTP로 교환했으며, 실차 주행이나 실제 교통 기준 도착 정확도 시험은 아니다. 기본 GPS가 해외 위치라면 먼저 에뮬레이터 Extended Controls의 Location에서 국내 시험 위치를 설정하고 해당 위치 주변 경로를 사용한다.

## 종료와 다음 실행

양쪽 공유 중지·내비 안내 종료 후 에뮬레이터 창을 닫는다. 별도 서버 터미널에서 Ctrl+C로 서버를 종료한다. 서버 데이터는 메모리에만 있으므로 서버를 다시 켜면 새 연결·그룹·참여 승인·공유 동의가 필요하다. 이전 코드가 영구 유지되는 방식은 아니다. 그룹장이 나가거나 장시간 연결이 끊겨도 그룹은 종료된다.

이번 검증 후 시험 경로 종료를 확인하고 원래 GPS를 복원했다. 새 B 기기와 설치한 Debug 앱은 다음 직접 검증을 위해 남겼다. Release 서명 키나 제출 APK는 만들지 않았다.
