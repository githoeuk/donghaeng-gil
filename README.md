# 동행길

> 따로 출발해도, 함께 도착하다.

여러 차량으로 이동하는 동행자들이 도착 예정 시간과 만날 장소를 공유하도록 돕는 Pleos Connect 차량용 앱 프로젝트입니다. 현대자동차그룹 차량용 앱 개발 공모전 출품을 위해 개발하고 있습니다.

## 작업 전 참고

[제출용 Release 가이드라인](docs/submission-release-guidelines.md)을 기능·빌드·권한·시연 작업의 기준으로 사용합니다. 제출용 모의 시연 포함과 서명 기준을 정리한 문서입니다. 빌드 변형 분리와 실제 정차·주행 게이트는 적용됐지만 서명 설정·제출 APK 생성과 Release 런타임 검증은 아직 완료되지 않았습니다.

## 현재 구현 상태

메인 화면은 **내 차량의 실제 위치와 Pleos 내비게이션의 목적지·남은 시간**을 표시합니다.

- Android 위치 API에서 위치·정확도·생성 시각을 수신하고, 화면에는 현재 위치와 일치하는 Pleos 주소를 표시합니다.
- Pleos NaviHelper 3.0.5에서 현재 위치 주소와 목적지의 장소명·주소·남은 시간·남은 거리를 조회합니다.
- 남은 시간과 수신 시각으로 도착 예정 시각을 표시합니다.
- 목적지 미설정, 위치 권한 없음, 연결 실패, 데이터 갱신 지연을 구분합니다.
- GPS와 내비 위치가 300m 이상(위치 정확도 오차 범위의 2배도 초과) 다르면 ‘위치 확인 필요’를 표시합니다.
- 위치 정확도가 100m보다 나쁘거나 최신 위치를 확인할 수 없어도 ETA에 확인이 필요하다고 표시합니다.
- 화면을 벗어나면 위치 수신과 내비 조회를 중단합니다. 위치를 서버로 전송하거나 저장하지 않습니다.

**현재는 내 차량 정보 연동과 모의 공유, 제출용 빌드 구성 분리 단계입니다.** 실제 동행 차량 간 통신, 그룹 생성·참여, 다른 차량의 위치 공유는 아직 구현하지 않았습니다.

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

## 메인 화면 사용 방법

1. 앱 목록에서 **동행길**을 실행합니다.
2. **내 위치 사용**을 눌러 위치 권한을 허용합니다. 정확한 위치를 권장하며, 대략적 위치만 허용하거나 거부해도 앱은 종료되지 않습니다.
3. Pleos 내비게이션에서 목적지를 설정하고 경로 안내를 시작합니다.
4. 동행길로 돌아오면 내 차량 위치, 내비 목적지, 예상 소요 시간과 도착 예정 시각을 확인할 수 있습니다.
5. 경로를 취소하면 도착시간 표시도 해제됩니다. 연결이 실패하면 **다시 연결**로 재시도할 수 있습니다.

에뮬레이터에서 Android GPS와 내비 내부 위치가 서로 다르게 움직이는 현상을 확인했습니다. 위치가 불일치하면 화면에는 내비가 제공한 소요 시간을 참고 정보로 구분하며, 현재 GPS 기준 도착 예정 시각으로 확정해 표시하지 않습니다. 이 동작이 실제 차량에서도 동일하다고 가정하지 않습니다.

## 개발용 시험 화면

Debug 빌드의 **알림 시험** 버튼으로 기존 로컬 알림·PiP 데모를 열 수 있습니다. 이 화면의 인물·도착시간·메시지는 예시이며 실제 동행자가 보낸 데이터가 아닙니다. Release에는 이 데모 화면과 별도 SDK 진단 Activity가 포함되지 않습니다.

이전 알림 시험의 조건과 한계는 [Pleos 테스트 기록](docs/pleos-test-results.md)을 참고하세요.

## 모의 공유 시연과 빌드 변형

메인 화면의 **가상 차량으로 체험 (정차 중)**에서 데모 그룹 생성·코드 참여(`DEMO42`), 모의 차량 3대의 위치·ETA 갱신, 상황 메시지·확인 응답, 연결 끊김·오래된 데이터·공유 중지·그룹 나가기를 시험할 수 있습니다.

**한 에뮬레이터 내부의 가상 시연입니다.** 공식 앱 연동이나 실제 차량 간 통신이 아니며, 실제 위치를 외부에 보내지 않습니다. 화면을 벗어나면 데모 상태를 삭제합니다. 모의 Activity·통신 구현은 standardRelease에는 없고 submissionRelease에는 포함됩니다. Debug와 제출용 모두 실제 차량 속도 0·기어 P·플랫폼 UX 제한 없음이 확인될 때만 시연을 시작합니다. 권한·상태가 없거나 주행·갱신 지연이면 전체 시연을 중단하고 잠급니다. 정차 복귀 후에는 새 세션을 명시적으로 시작해야 합니다. [정차 판별 기준과 검증](docs/parking-safety.md)을 참고하세요.

[통신 구조와 시연 방법](docs/communication-plan.md) · [최근 작업 결과](docs/work-log.md)

| 변형 | 모의 코드 | SDK 진단·알림 시험 | 디버깅 |
| --- | --- | --- | --- |
| standardDebug | 포함, 실제 정차 때만 실행 | 포함 | 허용 |
| submissionDebug | 포함, 실제 정차 때만 실행 | 포함 | 허용 |
| standardRelease | 제외 | 제외 | 비허용 |
| submissionRelease | 포함, 실제 정차 때만 실행 | 제외 | 비허용 |

모의 Activity는 모든 포함 변형에서 exported=false이며 DUMP 권한에 의존하지 않습니다. Release에는 Debug 소스셋 전체를 포함하지 않습니다. 모든 변형의 applicationId는 `io.demo.companion`으로 같으므로 동시에 설치하는 구성이 아닙니다.

현재 **Release APK/AAB 패키징은 의도적으로 차단**되어 있습니다. 서명 설정과 제출 안전 기준을 연결하기 전에는 `assembleStandardRelease`, `assembleSubmissionRelease`, `assembleRelease`, `bundle*Release`로 unsigned 산출물을 만들지 않습니다. 서명 키나 제출 APK는 아직 없습니다. 구성만 검사하려면 다음을 실행합니다.

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

이제 `동행 차량과 공유`에서 두 에뮬레이터가 실제 SDK 위치·내비 ETA와 상황 메시지를 공유할 수 있다. Node.js 22 이상에서 `node relay/server.mjs`를 실행하고 Debug 앱에서 `http://10.0.2.2:8787`에 연결한다. 정차 후 그룹 생성/코드 참여, 각자 명시적인 위치 공유 동의가 필요하다. 네트워크가 없는 기존 모의 시연과 별도다. 공유 시작 후 내비 등 다른 앱에서도 location Foreground Service로 위치·ETA를 공유하며 지속 알림에서 중지할 수 있다. 화면 복귀 시 같은 그룹을 표시한다. [백그라운드 공유 조건](docs/background-sharing.md)을 따른다. 자세한 실행법, API, 온라인 HTTPS 배포 준비와 한계는 [실제 중계 문서](docs/local-relay.md)에 정리했다. 공식 마이현대·Kia App 연동이나 실차 검증은 완료하지 않았다.

직접 빌드·설치·공유 검증과 후속 개발을 진행하려면 [개발 인계 안내](docs/developer-handoff.md)를 참고하세요. PowerShell 명령과 코드별 역할을 정리했습니다.

## 그룹 접근 관리

실제 공유는 [그룹 접근 권한 기준](docs/group-access-control.md)을 따른다. 그룹장이 초대 코드를 재발급/폐기하고 참여 요청을 승인/거절하거나 구성원을 내보낼 수 있다. 코드 입력만으로 위치·ETA를 받지는 못하며 승인 후에도 각 차량의 공유 동의가 필요하다. 그룹장 탈퇴/연결 만료 시 그룹 전체가 종료된다. 최신 앱과 accessVersion 2 서버를 함께 사용한다. 계정/차량 소유 인증과 온라인 배포는 별도 후속 작업이다.

## HTTPS 배포 준비

[Caddy/Compose 배포 설정과 운영 가이드](docs/https-relay-operations.md)를 제공한다. 로컬 TLS 시험은 명시적 `donghaengLocalTlsCa` 속성을 준 Debug에서만 정확한 로컬 호스트에 시험 CA를 추가한다. 기본 Debug·Release는 해당 CA를 포함하지 않는다. 공인 도메인/서버를 이용한 온라인 배포 여부와 두 에뮬레이터의 검증 결과는 work-log를 따른다.
