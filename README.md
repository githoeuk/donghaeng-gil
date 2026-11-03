# 동행길

> 따로 출발해도, 함께 도착하다.

여러 차량으로 이동하는 동행자들이 도착 예정 시간과 만날 장소를 공유하도록 돕는 Pleos Connect 차량용 앱 프로젝트입니다. 현대자동차그룹 차량용 앱 개발 공모전 출품을 위해 개발하고 있습니다.

## 현재 구현 상태

현재 버전은 **Pleos 에뮬레이터에서 알림 표시 방식을 검증하는 로컬 데모**입니다. 버튼을 누르면 10초 후 가상의 동행자가 보낸 메시지 또는 상태 알림을 생성합니다.

- 동행자의 도착시간 공유 메시지
- 만날 장소 변경 제안 메시지
- 그룹 메시지 알림과 읽음 처리 액션
- 도착 예정 시간 상태 알림 시험
- 표준 Picture-in-Picture(PiP) 지원 여부 시험

실제 차량 간 통신, 사용자·그룹 관리, 위치 공유, ETA 계산, 내비게이션 목적지 변경은 아직 구현하지 않았습니다. 화면과 알림에 나타나는 인물·목적지·도착시간은 고정된 예시 데이터입니다.

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

DB, 백엔드 서버, 외부 API 키, Pleos SDK 의존성 없이 Android 플랫폼 API만 사용하는 테스트 앱입니다. 최초 빌드 시 Gradle과 Android 빌드 도구 다운로드를 위한 인터넷 연결은 필요할 수 있습니다.

## 빌드 및 설치

1. Android Studio에서 저장소 루트 폴더를 엽니다.
2. Android SDK Platform 35를 설치하고 Gradle 동기화를 진행합니다.
3. 로컬 SDK 경로는 Android Studio가 생성하는 `local.properties`에 설정합니다. 이 파일은 Git에 포함하지 않습니다.
4. Pleos Connect 에뮬레이터를 실행한 뒤 `app`을 실행합니다.

Windows 터미널에서 빌드하려면:

```powershell
.\gradlew.bat assembleDebug
```

macOS / Linux에서는:

```sh
sh ./gradlew assembleDebug
```

빌드 결과: `app/build/outputs/apk/debug/app-debug.apk`

저장소에 포함한 설치 파일: [동행길 데모 APK](artifacts/donghaeng-gil-demo-debug.apk). 개발용 debug 서명 APK이며 배포용 릴리스 빌드가 아닙니다.

Windows에서는 영문 경로에 프로젝트를 두는 것을 권장합니다. 현재 한글 작업 경로에서의 검증은 `-Pandroid.overridePathCheck=true` 옵션을 추가하여 수행했습니다.

## 데모 사용 방법

현재 테스트 버튼과 예시 메시지는 영어로 표시됩니다.

1. 앱 목록에서 **동행길**을 실행합니다.
2. 필요한 경우 `Request notification permission`으로 알림 권한을 허용합니다.
3. `Clear test notifications`로 기존 시험 알림을 지웁니다.
4. `Member shares ETA after 10 sec (demo)` 또는 `Member proposes meeting-point change after 10 sec (demo)`를 누릅니다.
5. 내비게이션 화면으로 이동하여 약 10초 뒤 메시지 표시를 확인합니다.

실제 에뮬레이터 시험에서는 그룹 메시지 팝업을 확인했습니다. 표준 PiP는 해당 이미지에서 지원되지 않았고, 일반 ETA 상태 알림은 시스템 등록만 확인했으며 팝업은 표시되지 않았습니다. 이 결과를 다른 차량·Pleos 버전에 일반화할 수 없습니다. 실제 경로 안내 중 시험과 실제 차량 시험은 수행하지 않았습니다.

자세한 조건과 한계: [Pleos 테스트 결과](docs/pleos-test-results.md)

## 폴더 구성

```text
app/src/main/       Android 앱 소스와 매니페스트
app/build.gradle    앱 빌드 설정
docs/               기존 에뮬레이터 시험 기록
artifacts/          직접 설치 가능한 데모 APK
gradle/wrapper/     Gradle 실행 환경
```

## 다음 개발 범위

- 동행 그룹 생성과 참여
- 동행자 간 도착시간·합류 장소 공유
- 실제 위치·경로 데이터 연결 및 ETA 계산
- 주행 중 조작을 최소화하는 화면·알림 설계

현재 데모는 서버 없이 실행할 수 있습니다. 서로 다른 차량이 실제 데이터를 주고받는 기능에는 별도의 통신 수단이 필요하며, DB 도입 여부는 데이터 저장 요구에 맞춰 결정합니다.

## Git 관리

`.gitignore`는 빌드 산출물, Android Studio 개인 설정, 로컬 SDK 경로, 서명 키 및 환경변수를 제외합니다. DB나 API 사용 여부와 관계없이 필요합니다.

현재 별도의 오픈소스 라이선스는 지정하지 않았습니다.

