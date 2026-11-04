# 직접 개발하고 검증하기

기준: 2026-11-04. 모든 명령은 **PowerShell** 기준이다. Git Bash의 `$` 프롬프트에 그대로 붙여 넣지 않는다. 에뮬레이터와 서버는 필요할 때 직접 켜고, 확인 후 종료한다.

## 1. 프로젝트 열기

Android Studio에서 `D:\projects\donghaeng-gil` 폴더를 연다. 코드 편집기는 같은 폴더를 열어 사용한다. 과거 C: 체크아웃이나 D: 바탕화면 복사본을 수정하지 않는다. 작업 전 `D:\projects\AGENTS.md`, 저장소 `AGENTS.md`, `docs/submission-release-guidelines.md`를 읽는다.

```powershell
Set-Location 'D:\projects\donghaeng-gil'
git status --short --branch
git log -5 --oneline
```

수정 파일이 있으면 내용을 확인한 뒤 작업한다. `git reset --hard`나 파일 삭제로 작업을 지우지 않는다. 이번 커밋은 로컬 main에만 있으며 GitHub에는 아직 푸시하지 않았다.

## 2. 지금 구현된 구조

```text
차량 Android 앱(Java, Android/AAOS + Pleos SDK)
  MainActivity → 내 차량 정보
  GroupSharingActivity → 실제 그룹 설정·표시·정차 시 메시지 조작
       ↕ 바인딩
  SharingService → 실제 GPS·내비 수집 + 공유 수명 + 지속 알림
       ↓ HttpSharingTransport (REST 방식 HTTP/JSON)
  Node.js 중계 서버(relay/server.mjs)
       ↔ 같은 그룹의 다른 앱

별도 모의 화면: SharingDemoActivity → MockSharingTransport
```

Node.js는 서버 JavaScript를 실행하는 환경이다. 서버는 Node 기본 HTTP 모듈을 사용하며 Express/Spring/H2는 사용하지 않는다. 데이터는 서버 프로세스의 RAM Map에만 두고 서버 종료 시 사라진다. 실제 공유 화면과 가상 모의 화면은 서로 다른 기능이다.

현재 실제 그룹·임시 초대·위치/ETA·상황 메시지·수신/확인·내비 전환 후 공유를 구현했다. 공식 마이현대/Kia App 연동, 영구 계정/차량 소유 인증, 온라인 HTTPS 배포, 실차 검증과 제출용 서명 APK는 완료하지 않았다.

## 3. 테스트부터 실행

JDK는 기존 C: 설치를 사용한다. 아래 경로는 현재 PC 기준이다.

```powershell
Set-Location 'D:\projects\donghaeng-gil'
.\scripts\test-state.ps1 -JdkHome 'C:\Program Files\Amazon Corretto\jdk21.0.10_7'
& 'D:\node.exe' --test .\relay\server.test.mjs
```

Java 178개 검사와 서버 HTTP 통합 시나리오가 통과해야 한다. 이 명령은 에뮬레이터나 서버 상주 프로세스를 켜지 않는다. 서버 검사는 임시 포트에 서버를 열고 검사 후 닫는다. 외부 npm 패키지 설치는 필요 없다.

## 4. Debug 빌드와 설치

```powershell
Set-Location 'D:\projects\donghaeng-gil'
$env:JAVA_HOME = 'C:\Program Files\Amazon Corretto\jdk21.0.10_7'
& 'D:\projects\build-donghaeng.ps1' -Tasks @('assembleStandardDebug')
```

최신 개발 APK: `D:\projects\donghaeng-gil\app\build\outputs\apk\standard\debug\app-standard-debug.apk`.
`artifacts/donghaeng-gil-demo-debug.apk`는 이전 시험 파일이므로 최신 작업 확인에 사용하지 않는다. 빌드 스크립트는 D:의 Gradle 캐시·임시 경로를 쓰며 SDK/JDK는 C:에 유지한다. 오프라인 캐시가 없는 의존성을 새로 추가하면 별도 다운로드 설정이 필요할 수 있다.

Android Studio의 Device Manager에서 `Pleos_Connect_v2.1.0`을 실행한다. 이 문서 작성 시 에뮬레이터는 꺼 둔 상태다. 부팅 후 다음으로 설치한다.

```powershell
$adbExe = 'C:\Users\dnrdl\AppData\Local\Android\Sdk\platform-tools\adb.exe'
& $adbExe devices
# 출력된 실제 시리얼 번호로 지정한다.
$vehicleSerial = 'emulator-5554'
& $adbExe -s $vehicleSerial install -r 'D:\projects\donghaeng-gil\app\build\outputs\apk\standard\debug\app-standard-debug.apk'
& $adbExe -s $vehicleSerial shell am start --user 10 -n io.demo.companion/.MainActivity
```

현재 AVD의 차량 사용자는 10이다. 다른 환경이면 `shell am get-current-user`로 확인한다. 설치/실행이 실패하면 명령의 오류를 확인하고 임의로 앱을 삭제하지 않는다. Debug/Release 서명이 다르면 업데이트 설치가 실패할 수 있다.

## 5. 실제 로컬 공유

별도 PowerShell 터미널을 열어 서버를 켜고 열어 둔다.

```powershell
Set-Location 'D:\projects\donghaeng-gil'
& 'D:\node.exe' .\relay\server.mjs
```

`127.0.0.1:8787`에서 실행된다. 앱에는 에뮬레이터 호스트 별칭인 `http://10.0.2.2:8787`을 입력한다. PC 브라우저/터미널의 상태 확인 주소는 `http://127.0.0.1:8787/health`다. LAN/실차에서 이 로컬 주소로 바로 접속하는 구성은 아니다. 서버 종료는 해당 터미널에서 Ctrl+C.

1. Pleos Virtual Console/차량 상태에서 P·속도0·UX 제한 없음으로 정차를 확인한다. 앱의 위치·차량 상태·알림 권한과 Android 위치 설정을 허용한다. 속도/기어를 앱 코드에 고정해 잠금을 해제하지 않는다.
2. 내비 앱을 한 번 열어 초기화하고 목적지·경로 안내를 설정한다. 내비를 설정하지 않으면 ETA가 나오지 않는 것이 정상이다.
3. 동행길 → **동행 그룹 (서버 연결)** → 차량 이름 → 서버 연결 → 그룹 만들기.
4. 상대 앱에서 같은 서버에 연결하고 화면에 표시된 초대 코드를 입력해 그룹 참여. 각자 공유 시작·알림 권한 허용·위치 전송 동의를 완료한다.
5. 양쪽의 다른 위치와 실제 SDK ETA·메시지 전송을 확인한다. 앱 수신 확인은 통신 수신이며, 메시지 확인 버튼은 사용자 확인이다.

양방향 Android 화면 검증에는 서로 구분되는 두 AVD가 필요하다. 같은 AVD를 기본 실행으로 중복 시작하면 실패할 수 있다. 이전 자동 시험은 두 인스턴스를 read-only·서로 다른 포트로 실행했으며, 실제 앱 설치 상태를 보존하는 일상 개발과 구분했다. 혼자 첫 실행을 확인할 때는 한 대의 내 차량 정보·그룹 생성까지 먼저 확인하면 된다. 모의 공유의 DEMO42를 실제 그룹 초대 코드로 사용하지 않는다.

에뮬레이터 GPS와 Pleos 내비 내부 위치가 다르면 주소·ETA를 보류한다. 시험용 GPS 예시는 아래와 같다. 내비가 같은 근처 좌표를 반환하는지 먼저 확인하고 시험 입력임을 기록한다.

```powershell
& $adbExe -s $vehicleSerial emu geo fix 127.094681 37.4127212
# 인자 순서: 경도, 위도. 앱 소스에 이 값을 고정하지 않는다.
```

## 6. 백그라운드 공유 확인

공유 시작 후 내비 화면으로 이동한다. 공유 전 화면 이탈은 연결을 종료하지만, 동의 후 시작된 공유는 서비스에서 유지된다.

```powershell
& $adbExe -s $vehicleSerial shell dumpsys activity services io.demo.companion
& $adbExe -s $vehicleSerial shell dumpsys activity activities
```

서비스의 `isForeground=true`, 알림301, 위치 유형 `0x8`을 확인한다. Activity 기록에서 동행길 화면이 STOPPED/비표시인지를 확인해 단순 분할 화면과 구분한다. 상대에서 새 좌표·최신 ETA가 계속 갱신되어야 한다. 복귀 시 같은 그룹과 공유 상태여야 한다.

알림 센터의 **동행길 · 위치 공유 중 → 공유 중지**로 종료한다. 상대 위치가 제거되고 서비스·알림이 사라지는지 확인한다. 위치 설정을 꺼도 중지되며 다시 켜거나 앱에 돌아와도 자동 공유하지 않는다. 다시 하려면 재연결·그룹 참여·새 동의가 필요할 수 있다. 자세한 정책/검증 한계는 `docs/background-sharing.md`에 있다.

시험 후 공유 중지·진단 경로 안내 종료·변경한 GPS/위치 설정 복원을 확인한다. 에뮬레이터 종료:

```powershell
& $adbExe -s $vehicleSerial emu kill
& $adbExe devices
```

## 7. 어떤 파일을 수정할지

| 작업 | 먼저 읽을 파일 |
| --- | --- |
| 내 차량 화면 | app/src/main/java/io/demo/companion/MainActivity.java |
| 주소/ETA 조건 | JourneyState.java, JourneyPublisher.java |
| 실제 SDK 수집/늦은 응답 | PleosJourneySource.java, NavigationSession.java, DestinationAddressResolver.java |
| 실제 그룹 UI | GroupSharingActivity.java |
| 화면 밖 공유/알림/종료 | SharingService.java, SharingLifetime.java |
| HTTP 송수신 | HttpSharingTransport.java, SharingTransport.java, SharingModels.java |
| 서버 인증·그룹·보관 | relay/server.mjs, relay/server.test.mjs |
| 정차 판별 | ParkingState.java, VehicleParkingSource.java |
| 모의 시연 | app/src/demo/java/io/demo/companion/ |
| 빌드·권한 | app/build.gradle, app/src/main/AndroidManifest.xml |

표에서 Java 파일명만 적힌 항목의 기본 폴더는 `app/src/main/java/io/demo/companion/`이다. 작은 변경 한 가지 → 관련 검사 → Debug 확인 → work-log 기록 → diff 검토 → 커밋 순서로 진행한다.

공통 코드·권한·소스셋을 바꾸면 네 변형 구성도 확인한다.

```powershell
.\scripts\verify-build-variants.ps1
# 컴파일·매니페스트 및 104개 검사. Release APK/AAB 패키징은 하지 않는다.
git diff --check
git diff
```

## 8. 이후 우선순위와 Git

다음 작업은 온라인 HTTPS 중계 배포 계획/인증·그룹 권한 강화다. 단일 메모리 서버의 재시작 시 그룹 소실, 계정/참여 승인 부재, 실차 전원/절전 검증을 고려한다. 배포 주소·운영 계정·과금은 정한 뒤 실제 배포한다. 공식 앱 연동은 공개 지원 확인 전까지 목표로 남긴다. 최종 팀명·키 관리 방법을 확정하기 전에는 Release 서명 키나 제출 APK를 만들지 않는다.

로컬 커밋을 검토할 때:

```powershell
git status --short --branch
git log --oneline origin/main..main
git diff origin/main..main --stat
```

현재 요청에서는 푸시하지 않는다. 나중에 직접 푸시하기로 결정했을 때만 실행한다.

```powershell
git push origin main
```

작업 기록: `docs/work-log.md`. 제출 기준: `docs/submission-release-guidelines.md`. 실제 서버 실행/API: `docs/local-relay.md`. 백그라운드 수명: `docs/background-sharing.md`.
