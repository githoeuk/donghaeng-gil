# 동행길

> 따로 출발해도, 함께 도착하다.

여러 차량으로 이동하는 동행자들이 도착 예정 시간과 만날 장소를 공유하도록 돕는 Pleos Connect 차량용 앱 프로젝트입니다. 현대자동차그룹 차량용 앱 개발 공모전 출품을 위해 개발하고 있습니다.

## 작업 전 참고

[제출용 Release 가이드라인](docs/submission-release-guidelines.md)을 기능·빌드·권한·시연 작업의 기준으로 사용합니다. 제출용 모의 시연 포함과 서명 기준을 정리한 문서입니다. 최종 키로 서명한 제출 변형을 Pleos에서 검증했습니다. 공모전 팀명은 **동행길**이며, 제출 파일은 [동행길_동행길_release.apk](apk/동행길_동행길_release.apk)입니다. 설치와 시연은 [Pleos 에뮬레이터 시연](#pleos-에뮬레이터-시연)을 참고하세요.

## 현재 구현 상태

메인 화면은 **내 차량의 실제 위치와 Pleos 내비게이션의 목적지·남은 시간**을 표시합니다.

- Android 위치 API에서 위치·정확도·생성 시각을 수신하고, 화면에는 현재 위치와 일치하는 Pleos 주소를 표시합니다.
- Pleos NaviHelper 3.0.5에서 현재 위치 주소와 목적지의 장소명·주소·남은 시간·남은 거리를 조회합니다.
- 남은 시간과 수신 시각으로 도착 예정 시각을 표시합니다.
- 목적지 미설정, 위치 권한 없음, 연결 실패, 데이터 갱신 지연을 구분합니다.
- GPS와 내비 위치가 300m 이상(위치 정확도 오차 범위의 2배도 초과) 다르면 ‘위치 확인 필요’를 표시합니다.
- 위치 정확도가 100m보다 나쁘거나 최신 위치를 확인할 수 없어도 ETA에 확인이 필요하다고 표시합니다.
- 내 차량 정보 화면은 화면을 벗어나면 자체 위치 수신과 내비 조회를 중단합니다.

실제 동행 공유는 별도 화면에서 중계 서버에 연결하고 그룹에 참여한 뒤 사용합니다. 각 참여자가 공유에 동의하면 위치·목적지·ETA를 전달합니다. 가상 체험은 에뮬레이터 한 대에서 실행되며 체험 데이터는 외부로 전송하지 않습니다.

[메인 화면 연동 검증](docs/main-screen-integration.md) · [기존 SDK 검증](docs/pleos-location-eta-verification.md)

현재 위치와 내비 좌표가 50m 이내이고 양쪽 데이터가 최신일 때만 내비가 제공한 주소를 현재 위치로 표시합니다. 주소가 없거나 일치하지 않으면 좌표를 보여주는 대신 주소 확인 안내를 표시합니다. 목적지 주소가 동 단위일 때는 Pleos 최근 목적지에서 장소 ID·하위 ID·제공자·이름이 같고 좌표가 100m 이내인 장소의 더 상세한 주소로 보완합니다. 일치 항목이 없으면 원래 내비 주소를 유지합니다. 최근 목적지 정보는 화면 연결 중 메모리에서만 사용하며 저장하거나 로그에 남기지 않습니다. 외부 역지오코딩 서비스는 사용하지 않습니다. 실제 그룹 공유용 INTERNET 권한은 별도로 사용합니다.

## 실행 환경

| 항목 | 설정 |
| --- | --- |
| 언어 | Java |
| Android Gradle Plugin | 8.13.0 |
| Gradle | 8.14.3 |
| JDK | 17 이상 |
| compileSdk / targetSdk / minSdk | 35 / 34 / 28 |
| 검증 환경 | Pleos Connect v2.1.0 에뮬레이터, Android API 34 |
| 애플리케이션 ID | `io.demo.companion` |
| Pleos SDK | NaviHelper 3.0.5 |

내 차량 정보와 모의 화면은 DB·서버·별도 API 키 없이 실행합니다. 실제 동행 그룹에는 중계 서버가 필요합니다. Debug와 Release 모두 공식 Pleos Maven 저장소의 NaviHelper를 사용합니다. 최초 빌드에는 Gradle·Android 빌드 도구·SDK 다운로드를 위한 인터넷 연결이 필요할 수 있습니다.

## 빌드 및 설치

1. Android Studio에서 저장소 루트를 엽니다.
2. Android SDK Platform 35를 설치하고 Gradle 동기화를 진행합니다.
3. Android Studio가 생성하는 `local.properties`에 로컬 SDK 경로를 설정합니다. 이 파일은 Git에 포함하지 않습니다.
4. Pleos Connect 에뮬레이터를 실행한 뒤 `app`을 실행합니다.

```powershell
.\gradlew.bat assembleStandardDebug
```

macOS / Linux에서는 `sh ./gradlew assembleStandardDebug`를 실행합니다.

개발용 Debug 빌드 결과: `app/build/outputs/apk/standard/debug/app-standard-debug.apk`

[설치용 Debug APK](artifacts/donghaeng-gil-demo-debug.apk)는 개발용 서명이며 정식 배포용 Release 빌드가 아닙니다.

이 PC의 작업 경로는 `D:\projects\donghaeng-gil`입니다. 소스·Gradle 캐시·임시 파일을 D드라이브에서 사용하는 로컬 빌드 스크립트는 다음과 같습니다. 이 스크립트는 저장소 외부의 PC 전용 설정입니다.

```powershell
& 'D:\projects\build-donghaeng.ps1' -Tasks @('assembleStandardDebug')
```

## Pleos 에뮬레이터 시연

### 에뮬레이터 실행과 APK 설치

1. [Pleos 공식 설치 가이드](https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)에 따라 Android Studio의 **Device Manager**에서 **Pleos Connect v2.1.0 APIs (Android API 34)** 가상 기기를 준비합니다. Cold Boot로 시작한 뒤 기본 내비가 열릴 때까지 기다립니다.
2. Android SDK의 `platform-tools`에 있는 `adb`로 서명된 제출용 APK를 설치합니다. 아래 명령은 저장소 루트에서 실행합니다. 기기 ID가 다르면 adb devices에 나온 값으로 바꿉니다. APK를 별도로 내려받았다면 다운로드한 경로를 사용합니다. 다른 서명으로 설치된 동행길이 있는 경우 새 AVD를 사용합니다.
3. 앱 목록에서 **동행길**을 엽니다. **내 위치 사용**을 눌러 정확한 위치를 허용하고, **차량 상태 접근 허용**이 표시되면 허용합니다. 가상 체험은 **P 기어·0km/h·플랫폼 제한 없음**이 확인되면 시작할 수 있습니다.

```powershell
adb devices
adb -s emulator-5554 install "apk/동행길_동행길_release.apk"
```

### 시험 GPS와 목적지 설정

1. AVD의 시험 GPS를 내비 기준점인 **위도 37.4127212, 경도 127.094681**에 맞춥니다. 아래 명령의 인자는 **경도, 위도** 순서입니다.
2. Pleos 기본 내비의 **어디로 갈까요?**에서 `Starbucks`를 검색합니다. **스타벅스 성남모란DT점**(경기도 성남시 중원구 둔촌대로 131)을 선택하고 **목적지로 → 안내 시작**을 누릅니다. 검색 결과가 비면 내비 준비가 끝났는지 확인한 뒤 검색어를 직접 입력해 다시 검색합니다.
3. 동행길의 **내 차량 정보**에서 현재 위치 **수정구 시흥동**, 가게명과 상세주소, 예상 소요 시간·도착 예정 시각·남은 거리를 확인합니다. 시간과 거리는 해당 실행의 내비 응답에 따라 달라집니다.

```powershell
adb -s emulator-5554 emu geo fix 127.094681 37.4127212
```

### 가상 체험 순서

1. 경로 안내를 유지한 채 정차 상태에서 **가상 체험 시작 → 새 체험 시작**을 누릅니다.
2. **그룹 만들기 → 가상 차량 참여 승인 → 공유 시작 → 체험 공유 시작**을 차례로 누릅니다. 가상 팀원 3대가 표시되면 **가상 차량에서 메시지 받기**를 눌러 수신 메시지 한 건을 만듭니다.
3. **내 차량 화면으로** 돌아와 실제 내비 정보와 보라색 **가상 체험** 카드의 팀원 요약을 확인합니다. Pleos 내비를 열어 같은 경로가 안내 중인지 확인합니다.
4. 동행길로 돌아와 **가상 체험 이어가기**를 누릅니다. 같은 팀원과 수신 메시지가 유지되는지 확인합니다.
5. 메인 화면의 **체험 종료**를 누르고 확인합니다. 실제 내비 경로는 유지되며 가상 팀원 요약과 이어가기 버튼은 사라집니다. 새 체험을 열면 그룹과 메시지가 비어 있습니다.

주행 잠금을 확인하려면 진행 중인 체험에서 [Pleos Virtual Console](https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/use-vehicle-emulator)로 **D 기어·22km/h**를 설정합니다. 메인 화면의 **이어가기·종료**가 비활성화되고, **P 기어·0km/h**로 돌아오면 다시 활성화됩니다.

### Pleos 지도와 위치 표시 제약

- **운영사무국 확인** — Pleos 에뮬레이터는 해외에도 배포되므로 법규상 고정밀 지도를 제공할 수 없습니다. 내장 내비게이션은 개발용입니다.
- **추가 회신 대기** — 기본 내비의 지도 배경(도로·건물·지명)이 비는 현상과 새 목적지 검색이 간헐적으로 실패하는 현상의 원인은 각각 확인 중입니다.

검증 중 지도 배경이 비어 있는 상태에서도 경로선과 회전 안내가 표시된 경우가 있었습니다. 동행길의 내 차량 정보는 Pleos SDK 응답을 사용합니다. [지도 조사 기록](docs/pleos-map-investigation.md)을 참고하세요.

Android GPS가 갱신돼도 Pleos 내비 좌표의 반영이 지연될 수 있습니다. **주소 확인 대기**나 **위치 확인 필요**가 표시되면 시험 GPS가 기준점에 있는지, 내비가 안내 중인지, 앱에 정확한 위치 권한이 있는지 확인합니다. 앱은 위치가 맞지 않을 때 내비 소요 시간을 참고 정보로 표시하며 GPS 기준 도착 예정 시각은 보류합니다.

## 개발용 시험 화면

Debug 빌드의 **알림 시험** 버튼으로 기존 로컬 알림·PiP 데모를 열 수 있습니다. 이 화면의 인물·도착시간·메시지는 예시이며 실제 동행자가 보낸 데이터가 아닙니다. Release에는 이 데모 화면과 별도 SDK 진단 Activity가 포함되지 않습니다.

이전 알림 시험의 조건과 한계는 [Pleos 테스트 기록](docs/pleos-test-results.md)을 참고하세요.

## 모의 공유 시연과 빌드 변형

메인 화면의 **가상 체험 → 가상 체험 시작**에서 데모 그룹 생성·코드 참여(`DEMO42`), 모의 차량 3대의 위치·ETA 갱신, 상황 메시지·확인 응답, 연결 끊김·오래된 데이터·공유 중지·그룹 나가기를 시험할 수 있습니다.

가상 체험은 에뮬레이터 한 대에서 재현합니다. 팀원과 수신 메시지는 체험을 이어갈 수 있도록 앱 프로세스의 메모리에 유지됩니다. **체험 종료**를 확정하거나 앱 프로세스를 종료하면 초기화됩니다. 체험 시작·이어가기와 메인 화면의 종료는 정차 상태에서 이용할 수 있습니다. [정차 판별 기준과 검증](docs/parking-safety.md)을 참고하세요.

[통신 구조와 시연 방법](docs/communication-plan.md) · [최근 작업 결과](docs/work-log.md)

| 변형 | 모의 코드 | SDK 진단·알림 시험 | 디버깅 |
| --- | --- | --- | --- |
| standardDebug | 포함, 실제 정차 때만 실행 | 포함 | 허용 |
| submissionDebug | 포함, 실제 정차 때만 실행 | 포함 | 허용 |
| standardRelease | 제외 | 제외 | 비허용 |
| submissionRelease | 포함, 실제 정차 때만 실행 | 제외 | 비허용 |

모의 Activity는 모든 포함 변형에서 exported=false이며 DUMP 권한에 의존하지 않습니다. Release에는 Debug 소스셋 전체를 포함하지 않습니다. 모든 변형의 applicationId는 `io.demo.companion`으로 같으므로 동시에 설치하는 구성이 아닙니다.

제출용 빌드 작업은 `assembleSubmissionRelease`입니다. Release 패키징에는 명시적인 서명 설정이 필요합니다. 설치·시연에는 이미 서명된 APK를 사용할 수 있으며, 빌드 구성을 검사할 때는 다음을 실행합니다.

```powershell
# 네 변형의 컴파일·매니페스트 병합 후 구성 104개 검사. APK 생성 없음.
.\scripts\verify-build-variants.ps1
# 기존 생성 결과만 검사
.\scripts\verify-build-variants.ps1 -SkipCompile
```

직접 컴파일할 때도 로컬 빌드 스크립트를 사용합니다.

```powershell
& 'D:\projects\build-donghaeng.ps1' -Tasks @('compileStandardReleaseJavaWithJavac','compileSubmissionReleaseJavaWithJavac')
```

로컬 빌드 스크립트의 기본값은 여전히 `assembleDebug`이며 두 Debug 변형을 모두 선택합니다. 개발 중에는 `-Tasks @('assembleStandardDebug')`처럼 변형을 명시하세요. 저장소의 기존 `artifacts/donghaeng-gil-demo-debug.apk`는 이전 검증용 파일이며 이번 구성 작업에서 갱신하지 않았습니다.

## 상태 처리 테스트

Android 기기 없이 실행 가능한 Java 상태 검증입니다.

```powershell
.\scripts\test-state.ps1
# JAVA_HOME 또는 PATH가 없다면 설치된 JDK를 -JdkHome으로 지정
```

상태 25개·주소 매칭 11개·내비 연결 수명 15개·모의 공유 40개·정차 안전 36개·송신 상태 및 서버 주소 19개·공유 수명 32개로 총 178개 조건을 확인합니다. 독립 실행 Java 검사이며 Android 에뮬레이터 없이 실행할 수 있습니다.

## 다음 개발 범위

- 실제 인증을 포함한 동행 그룹 생성과 참여
- 온라인 HTTPS 환경의 공유·재접속 검증
- 동행자의 합류 장소 변경 메시지
- 실제 차량 및 주행 환경 검증

`.gitignore`는 빌드 결과, 개인 SDK 경로, IDE 설정, 환경변수와 서명 키를 제외합니다. 현재 별도의 오픈소스 라이선스는 지정하지 않았습니다.

### 실제 동행 그룹 (로컬 중계)

이제 `실제 동행 공유`에서 두 에뮬레이터가 실제 SDK 위치·내비 ETA와 상황 메시지를 공유할 수 있다. Node.js 22 이상에서 `node relay/server.mjs`를 실행하고 Debug 앱에서 `http://10.0.2.2:8787`에 연결한다. 정차 후 그룹 생성/코드 참여, 각자 명시적인 위치 공유 동의가 필요하다. 네트워크가 없는 기존 모의 시연과 별도다. 공유 시작 후 내비 등 다른 앱에서도 location Foreground Service로 위치·ETA를 공유하며 지속 알림에서 중지할 수 있다. 화면 복귀 시 같은 그룹을 표시한다. [백그라운드 공유 조건](docs/background-sharing.md)을 따른다. 자세한 실행법, API, 온라인 HTTPS 배포 준비와 한계는 [실제 중계 문서](docs/local-relay.md)에 정리했다. 공식 마이현대·Kia App 연동이나 실차 검증은 완료하지 않았다.

직접 빌드·설치·공유 검증과 후속 개발을 진행하려면 [개발 인계 안내](docs/developer-handoff.md)를 참고하세요. PowerShell 명령과 코드별 역할을 정리했습니다.

## 그룹 접근 관리

실제 공유는 [그룹 접근 권한 기준](docs/group-access-control.md)을 따른다. 그룹장이 초대 코드를 재발급/폐기하고 참여 요청을 승인/거절하거나 구성원을 내보낼 수 있다. 코드 입력만으로 위치·ETA를 받지는 못하며 승인 후에도 각 차량의 공유 동의가 필요하다. 그룹장 탈퇴/연결 만료 시 그룹 전체가 종료된다. 최신 앱과 accessVersion 2 서버를 함께 사용한다. 계정/차량 소유 인증과 온라인 배포는 별도 후속 작업이다.

## HTTPS 배포 준비

[Caddy/Compose 배포 설정과 운영 가이드](docs/https-relay-operations.md)를 제공한다. 로컬 TLS 시험은 명시적 `donghaengLocalTlsCa` 속성을 준 Debug에서만 정확한 로컬 호스트에 시험 CA를 추가한다. 기본 Debug·Release는 해당 CA를 포함하지 않는다. 공인 도메인/서버를 이용한 온라인 배포 여부와 두 에뮬레이터의 검증 결과는 work-log를 따른다.
