# 동행길 작업 기록

## 2026-11-04 — 늦은 목적지 응답 방어 및 교체 가능한 모의 공유

### 작업 기준

- 활성 저장소: `D:\projects\donghaeng-gil`, 브랜치 `main`.
- 시작 HEAD: `a7f024a` (기존 미커밋 메인 화면·위치·상세주소 구현 보존).
- `D:\projects\AGENTS.md` 적용. 기존 SDK·JDK는 C:, 빌드·캐시·임시는 D: 유지.
- 이번 작업에서 커밋·푸시·스테이징하지 않았다. 기존 변경을 초기화하지 않았다.

### 구현한 내용

1. **늦은 목적지 응답 방어**
   - `NavigationSession.java`를 추가해 리스너 연결 수명과 취소·도착 종료 상태를 검사한다.
   - `PleosJourneySource.java`에서 목적지 변경·경로 시작 시 이전 리스너·SDK 연결을 해제하고 새 연결로 조회한다. 이전 연결의 큐에 남은 목적지·주소·상태 콜백을 버린다.
   - 취소·도착 후 목적지·최근 목적지 주소 응답과 늦은 driving 상태로 경로가 되살아나는 것을 막는다.
   - 초기 idle 상태와 목적지가 확인된 뒤 idle을 구분한다. 새 경로·수동 재연결로 새 세대에 진입한다.
   - 종료·변경·해제 시 목적지와 상세주소 캐시를 비운다. 동일 목적지의 더 빈약한 최근 목록 응답으로 상세주소를 되돌리지 않고, 주소 응답으로 ETA 수신 시각을 갱신하지 않는다.

2. **통신 교체 지점**
   - `SharingTransport.java`: 그룹 생성·참여, 공유 시작·중지·탈퇴, 메시지·확인, 스냅샷·리스너 계약.
   - `SharingModels.java`: 차량 최신 상태, 목적지 식별, 메시지, 전달 상태, 불변 스냅샷.
   - `SharingStore.java`: 이전 수명 콜백, 역순 위치·중복 메시지 거절, 전달 상태 보호, 그룹·공유 종료 정리.
   - 공식 앱 연동 어댑터는 지원이 확인되지 않아 만들지 않았다. 인터넷·DB·휴대폰 앱·실제 위치 전송을 추가하지 않았다.

3. **Debug 전용 모의 공유**
   - `MockSharingTransport.java`: 센서·SDK·네트워크·디스크를 사용하지 않는 가상 데이터 시나리오.
   - `SharingDemoActivity.java`: 그룹 만들기·데모 코드 참여(`DEMO42`), 공유 시작·중지·탈퇴, 차량 3대, 상황 메시지와 모의 확인 응답, 연결 장애·갱신 중단·재연결 조작.
   - 메인 화면에 **모의 공유 (정차 중)** 버튼 추가. 가상 데이터와 실제 내 차량 정보는 별도 화면으로 구분한다.
   - 차량 가·나는 같은 목적지, 다는 다른 목적지로 표시하며 다른 목적지의 도착 순서 비교를 제외한다.
   - 15초 넘게 갱신되지 않은 상태는 오래된 정보로 표시하고 최신 ETA를 숨긴다. 오프라인 상태에서는 메시지를 조용히 대기열에 넣지 않으며 실패 메시지를 자동 재전송하지 않는다.
   - 공유 중지 시 차량 수신 상태를 비우고, 탈퇴·화면 종료 시 메모리 그룹 상태를 삭제한다. 주행 최적화 메타데이터를 선언하지 않은 정차용 시험 화면이다.
   - Release에는 모의 Activity와 모의 통신 구현이 없다.

### 변경·추가 파일

- 기존 수정: `MainActivity.java`, `PleosJourneySource.java`, Debug 매니페스트, `README.md`, `docs/main-screen-integration.md`, 설치용 Debug APK.
- 신규 공용 코드: `NavigationSession.java`, `SharingModels.java`, `SharingStore.java`, `SharingTransport.java`.
- 신규 Debug 코드: `MockSharingTransport.java`, `SharingDemoActivity.java`.
- 신규 검사: `app/src/test/java/io/demo/companion/NavigationSessionTest.java`, `app/src/testDebug/java/io/demo/companion/SharingTest.java`.
- 신규 실행 도구: `scripts/test-state.ps1`.
- 설계·시연 문서: `docs/communication-plan.md`.
- 이 기록은 이번 작업 변경 목록이다. Git status에는 앞서 존재하던 변경·미추적 파일도 함께 남아 있다.

### 검증 결과

| 검증 | 결과 |
| --- | --- |
| 기존 JourneyState | 25개 통과 |
| 기존 상세주소 매칭 | 11개 통과 |
| 내비 연결 세대·콜백 순서 | 15개 통과 |
| 공유 수명·모의 흐름·중복·역순·실패·재연결 | 40개 통과 |
| 합계 | **91개 통과** |
| Debug·Release 최종 빌드 | 성공 |
| Release APK manifest·dex 검사 | 모의 Activity·모의 통신 클래스 및 INTERNET 권한 없음 |
| git diff --check | 통과 |

독립 실행 검사 명령:

```powershell
.\scripts\test-state.ps1 -JdkHome 'C:\Program Files\Amazon Corretto\jdk21.0.10_7'
& 'D:\projects\build-donghaeng.ps1' -Tasks @('assembleDebug','assembleRelease')
```

기존 Java 8 source/target의 obsolete 경고, deprecated API 및 Gradle 향후 호환성 경고는 남아 있다. 이번 작업의 빌드 실패는 없다.

Pleos Connect v2.1.0 / `emulator-5554` / 사용자 10에 APK를 설치하고 다음을 화면·UI XML로 확인했다.

- 데모 그룹 생성·공유 시작 후 가상 차량 3대와 서로 다른 목적지 표시.
- 상황 메시지에 ‘모의 확인됨’, 가상 상대 응답에 ‘모의 전달됨’ 표시.
- 갱신 중단 뒤 ‘오래된 정보 · 최신 ETA 표시 보류’ 표시.
- 연결 끊김, 공유 중지 뒤 차량 목록 제거, 그룹 나가기 뒤 메시지 제거.
- 메인 화면 복귀 및 **다시 연결** 후 실제 내비 목적지 `스타벅스 분당궁내DT점`, `경기도 성남시 분당구 대왕판교로 302`, 약 11분·5.6km 표시. ETA 숫자는 에뮬레이터 내비 응답이며 고정값이 아니다.
- 최종 앱은 에뮬레이터 메인 화면에 두었다. 모의 그룹은 종료됐다.

로컬 증거: `.gradle/sharing-active.png`, `.gradle/sharing-message.png`, `.gradle/nav-final.png`, `.gradle/nav-final.xml`. 일부 UI XML은 에뮬레이터 `/sdcard/sharing-*.xml`에 있다. 이 파일들은 Git 제출물에 추가하지 않았다.

### 한계와 미완료 항목

- SDK 공개 목적지 콜백에 요청 ID가 없다. 연결 토큰은 과거 리스너 응답을 차단하는 방어이지 개별 SDK 요청의 식별자가 아니다. SDK 서비스가 과거 결과를 새 리스너로 재전달하거나 상태 이벤트 자체를 역순으로 보내는 경우는 판별할 수 없다. SDK 보장 또는 추가 식별 수단 확인이 필요하다.
- 지연 콜백 순서는 순수 Java 세대·상태 검사로 재현했다. 실제 SDK에 임의 지연·역순 응답을 주입한 시험이나 실제 차량 주행 시험은 하지 않았다.
- 모의 그룹·초대 코드는 실제 인증·권한·다중 기기 연결이 아니다. 모의 전달·확인은 실제 상대 수신 증거가 아니다.
- 공식 앱 중계 API·차량 내장 데이터망 사용 범위·백그라운드 연결은 미확인이다.
- 공유 모델의 순서 번호는 동일 송신 수명 기준이다. 실제 연동에는 송신 세션 ID, 서버 수신 시각·시계 오차, 권한·초대 만료·접근 취소·데이터 삭제 정책이 추가로 필요하다.

### 다음 작업

1. 공식 앱 중계 SDK 제공 여부와 차량 앱의 직접 인터넷 사용 조건을 운영 측에 확인한다.
2. 지원되는 통신 방식이 정해지면 실제 SharingTransport 어댑터와 인증·그룹 권한을 구현한다.
3. 명시적 공유 시작 뒤 유효한 실제 내 차량 정보만 보내도록 수집 수명주기를 연결한다.
4. 두 에뮬레이터 간 실제 송수신·재연결·권한 격리를 검증한다. 이후 실차 검증은 별도 단계다.

## 작업별 커밋 정리

이전 절의 ‘커밋하지 않았다’는 구현 완료 당시 기록이다. 이후 사용자의 명시적 요청에 따라 다음 작업 단위로 로컬 main에 커밋했다.

- `dc2fb70` — 실제 Pleos 위치·목적지·상세주소·ETA 연동 및 늦은 응답 방어, 관련 상태 테스트.
- `7349db9` — 교체 가능한 공유 통신 계약·상태와 Debug 모의 그룹·차량·메시지·장애 시연, 관련 테스트와 실행 스크립트.
- `docs: record journey verification and refresh demo APK` — README·설계·검증·작업 기록과 최신 설치용 Debug APK를 함께 정리한 문서·산출물 커밋.

커밋 과정에서 코드의 의미를 바꾸는 수정은 하지 않았으며 일부 파일 끝의 불필요한 빈 줄만 정리했다. 앞서 통과한 91개 검사와 Debug·Release 빌드 결과는 위 구현 검증 절을 따른다. 커밋을 위해 빌드·에뮬레이터 시험을 다시 실행하지는 않았다. 원격 푸시는 하지 않았다.

## 2026-11-04 — 제출용 Release 범위와 작업 가이드라인 수립

사용자 요청으로 `docs/submission-release-guidelines.md`를 작성했다. 공식 제출 안내를 재확인하고 공식 요구와 프로젝트 자체 품질 기준을 구분했다.

- 1차 제출 범위는 실제 Pleos 정보 화면 + 명시적으로 구분된 정차 전용 모의 공유 시연이다. SDK 진단·강제 경로 시험·기존 알림 시험은 제외한다.
- 현재 Debug 전용 모의 기능을 제출 Release로 자동 포함시키지 않는다. 후속 작업에서 submissionRelease / standardRelease 구분과 전용 모의 소스셋을 적용할 계획으로 기록했다.
- 주행·정차·상태 미확인에 따른 조작 제한, 서명 키 관리·누락 시 빌드 실패·서명/설치 검증, apk 폴더·최종 팀명 기반 파일명·버전·해시 기록 기준을 정했다.
- 실제 팀명, 실제 키 보관 위치·alias·비밀번호는 미정이다. 이번 작업에서 키나 APK를 생성하지 않았다.
- 저장소 루트 `AGENTS.md`에 작업 전 가이드라인 참조 규칙을 추가하고 README에 링크를 연결했다. 부모 `D:\projects\AGENTS.md`는 변경하지 않았다.
- 문서·작업 규칙만 변경했다. 앱·Gradle·권한·서명 설정은 변경하지 않았고 빌드·에뮬레이터 시험은 실행하지 않았다. 커밋·푸시하지 않았다.
- 검증: 문서 링크 대상 존재와 Git 변경 범위·공백 검사 확인. 현재 구현이 새 제출 기준을 모두 충족했다는 의미는 아니다.

공식 출처: https://appdevchallenge.auto-hmg.io/guide/submission 및 https://appdevchallenge.auto-hmg.io/guide/judging-awards (2026-11-04 확인).

## 2026-11-04 — 일반·제출 Release 분리와 모의 기능 소스셋 구성

### 구현

- `app/build.gradle`: distribution flavor로 standard/submission을 정의해 standardDebug·submissionDebug·standardRelease·submissionRelease 네 변형을 구성했다. applicationId·SDK 버전·기존 내비 기능은 유지했다. 두 Release는 debuggable=false다.
- 모의 포함 설정은 `BuildConfig.HAS_SHARING_DEMO`로 분리했다. Debug는 모두 모의를 포함하며, Release는 submission만 포함한다. DEBUG=true로 Release를 위장하지 않는다.
- `MockSharingTransport.java`, `SharingDemoActivity.java`를 src/debug에서 **src/demo**로 이동했다. 별도 demo 매니페스트의 Activity는 exported=false이며 DUMP 권한이 없다. src/debug 전체를 제출용에 포함하지 않아 SDK 진단·강제 경로 시험·기존 알림 시험과 NAVI_ROUTE_SEARCH 권한은 Release에서 제외된다.
- MainActivity에 명시적인 모의 안내를 추가했다. Debug에서만 안내를 확인하고 개발용 시연을 실행할 수 있다. 실제 정차 상태 SDK를 아직 연결하지 않았으므로 submissionRelease는 시연 안내에서 실행을 잠그며, Activity 자체도 같은 조건으로 모의 엔진을 시작하지 않는다. 이는 안전 기능 완료가 아니라 미확인 상태에서의 임시 차단이다.
- 모의 화면 재시작 시 메시지 렌더 캐시를 초기화해 종료·복귀 후 빈 메시지 상태가 올바르게 그려지게 했다.
- 서명 설정은 만들지 않았다. `verifyReleaseDistributionConfigured`는 의도적으로 Release APK/AAB 패키징을 차단한다. 후속 서명·안전 검증 단계에서 실제 준비 상태 검사로 교체해야 한다. unsigned나 Debug 서명으로 대체하는 옵션은 없다.
- `scripts/test-state.ps1`의 이동된 모의 소스 경로를 갱신하고 `scripts/verify-build-variants.ps1`에 변형별 컴파일·매니페스트·클래스·플래그 검사 56개를 추가했다.
- README, 통신 설계, 제출 가이드라인을 현재 구성·잠김 상태·검증 명령에 맞춰 갱신했다. 앞서 미커밋이던 가이드라인·작업 규칙은 보존했다. 저장소 밖의 로컬 빌드 스크립트는 수정하지 않았다.

### 검증

- 독립 Java 상태 검사 **91개 통과**.
- 네 변형 모두 Java 컴파일·리소스 처리·매니페스트 병합 성공. assembleRelease/package/bundle은 실행하지 않았다.
- 생성된 변형별 클래스·매니페스트·BuildConfig에 대한 **56개 구성 검사 통과**. standardRelease 모의 제외, submissionRelease 모의 포함, Release 진단 도구 제외, 동일 applicationId, Release 비디버깅, 비공개 모의 Activity, 새 INTERNET 권한 없음 확인.
- `verifyReleaseDistributionConfigured` 단독 실행은 `RELEASE_DISTRIBUTION_NOT_CONFIGURED`로 **의도한 실패**를 확인했다. 컴파일 실패와 구분한다.
- 두 Release의 APK/AAB 작업 그래프를 --dry-run으로 확인해 패키징 전에 차단 작업이 실행되도록 연결된 것을 검증했다. 실제 패키징은 하지 않았다.
- 기존 app/build/outputs/apk 파일 목록과 SHA-256을 전후 비교해 **APK 생성·변경 없음**을 확인했다. artifacts의 이전 개발용 APK도 갱신하지 않았다. 서명 키·서명 설정·apk/ 제출 파일을 만들지 않았다.
- 이번 검증은 컴파일·구성 검증이다. 새 APK 설치·에뮬레이터 런타임·실차 주행·서명 검증은 하지 않았다. 이전 작업의 런타임 결과를 이번 변형 검증으로 대체하지 않는다.
- 기존 Java 8 source/target·deprecated API 경고는 남아 있다. 커밋·푸시하지 않았다.

검증 명령:

```powershell
.\scripts\test-state.ps1 -JdkHome 'C:\Program Files\Amazon Corretto\jdk21.0.10_7'
.\scripts\verify-build-variants.ps1
# 이미 컴파일한 결과만 검사할 때
.\scripts\verify-build-variants.ps1 -SkipCompile
```

### 다음 작업

실제 차량 상태 SDK로 정차·주행·상태 미확인을 판별하고 시연 조작 제한을 연결한다. 이 단계 전에는 submissionRelease의 모의 실행 잠금을 해제하지 않는다. 이후 사용자가 팀명과 키 관리 방법을 정한 뒤 서명 준비 검사·제출 산출물 생성·실제 설치 검증을 진행한다.

## 2026-11-04 — 실제 정차·주행 판별과 모의 시연 잠금 연결

### 구현 내용

- Pleos 공식 차량 속도 FAQ와 Driving Restriction 가이드를 확인했다. 속도는 GPS나 모의 차량 데이터가 아니라 AAOS CarPropertyManager의 PERF_VEHICLE_SPEED, 기어는 GEAR_SELECTION, 플랫폼 제한은 CarUxRestrictionsManager에서 읽는다. 별도 Vehicle Maven 의존성이나 비공개 API를 추가하지 않았다.
- `ParkingState.java`: 속도 정확히 0m/s·기어 P·UX 제한 없음, 1초 유지·3초 이내 신선도일 때만 허용한다. 미수신·오류·서비스 미지원·권한 없음·미래/오래된 시간·0이 아닌 전후진 속도·P 아닌 기어는 잠근다. 연결 세대와 실패 revision으로 이전 조회 결과의 재해제를 차단한다.
- `VehicleParkingSource.java`: android.car 라이브러리로 비동기 서비스 연결, 지원 속성 확인, 속도·기어·UX 구독과 주기적 현재값 조회를 구현했다. 조회는 단일 작업 스레드, 감시·조회 주기는 500ms다. 속도 원본 timestamp와 요청 시작 중 오래된 시각을 사용해 오래된 속도가 조회 성공만으로 최신이 되지 않게 했다. 기어는 on-change 특성을 고려해 현재 조회의 AVAILABLE 상태와 값·조회 신선도를 확인한다.
- MainActivity의 모의 버튼·안내 확인 순간을 실제 상태로 검사한다. 팝업 도중 조건이 깨지면 닫는다. 속도 권한 요청은 위치 권한과 구분했다.
- SharingDemoActivity도 별도 실제 상태를 확인한다. 실행 중 조건이 깨지면 모의 transport를 close하여 그룹·차량·메시지를 지우고 전체 조작을 잠금 화면으로 바꾼다. 입력 포커스·키보드도 닫는다. 모든 모의 조작·확인·tick 직전 재검사하며 중복 시작을 방지한다.
- 다시 P·0 상태가 되어도 자동 재개하지 않는다. 새 모의 시연을 명시적으로 시작하면 빈 세션을 만든다. Debug 안전 우회와 Release의 고정 잠금을 모두 제거하고 동일 실제 상태 게이트를 적용했다.
- CAR_SPEED·CAR_POWERTRAIN 읽기 권한은 demo 매니페스트에만 추가했다. standardRelease에는 없고 Debug/submissionRelease에만 있다. android.car 런타임 라이브러리는 optional로 선언했다. 네트워크·차량 제어 권한은 추가하지 않았다.
- `ParkingStateTest.java` 36개 검사와 변형 검사 16개를 추가했다. README·제출 가이드·통신 설계와 신규 `docs/parking-safety.md`에 조건·출처·검증 범위·한계를 기록했다.

### 검증 결과

| 항목 | 결과 |
| --- | --- |
| 기존 독립 Java 검사 | 91개 통과 |
| 정차 안전 검사 | 36개 통과 — 합계 **127개** |
| 네 빌드 변형 컴파일·매니페스트 | 성공 |
| 클래스·권한·빌드 플래그·라이브러리 구성 | **72개** 통과 |
| 개발용 standardDebug APK | 빌드·설치·실행 확인 |
| 제출용 Release APK/AAB·서명 키 | 생성하지 않음 |

Pleos Connect v2.1.0 / Android API 34 / emulator-5554 / 사용자 10에서 다음을 확인했다.

1. 실제 속도 권한이 없을 때 상태 설명과 권한 버튼을 표시하며 모의 진입 버튼 비활성.
2. 권한 허용 후 AAOS 실제 P·속도 0·UX 제한 없음 수신 시 진입 가능. 안내 후 모의 Activity가 자체 상태를 재확인하고 새 시연 시작을 요구함.
3. 정차 상태에서 가상 그룹·차량 3대 공유 실행.
4. 공식 Virtual Console에서 P→D로 전환하자 속도 0에서도 전체 시연 잠김·시작 버튼 비활성·그룹 삭제 확인. ACCEL로 약 28km/h까지 주행을 재현하고 제동 후 0→P로 복원.
5. P 복귀 후 자동 공유 재개 없음. 새 시연 시작 시 그룹 미참여·메시지 없음 확인.
6. 실제 CAR_SPEED 권한 회수 후 메인 진입 잠금 재확인. 마지막에는 권한 허용·P·속도 0과 메인 화면으로 복원했고 모의 세션은 종료했다.

에뮬레이터의 일반 car_service 셸 제어는 user build 보안 제한으로 사용할 수 없어 공식 Virtual Console UI로 차량 상태를 변경했다. 이는 구현 차단이 아니라 시험 도구 선택의 제약이었다.

로컬 증거: `.gradle/parking-active.png`, `.gradle/parking-drive.png`, `.gradle/parking-return.png`와 `.gradle/parking-*.xml`. Virtual Console은 http://127.0.0.1:8088/ 로 ADB 포워딩해 사용했으며 외부로 위치를 전송하지 않았다.

### 한계·남은 작업

- Debug 에뮬레이터 상태 전환 검증이며 실제 차량 주행·센서 단절·서명된 Release 실행 검증이 아니다. 서비스 실패·무응답·오래된 원본 시각·이전 연결 응답은 순수 Java 검사로 검증했고 실제 차량 서비스 강제 중단은 하지 않았다.
- 이 작업은 모의 시연 조작에 대한 게이트다. 메인 화면 전체, 기존 Debug 진단·알림 도구, 지역별 모든 UX 제한에 대한 안전 심사 완료를 의미하지 않는다.
- 최종 입력 키보드 정리·중복 시작 방어는 컴파일 검사로 확인했으며, 별도 실차 입력 시험은 하지 않았다.
- Release 패키징 차단은 유지했다. 기존 Release APK 목록·해시의 변경 없음 확인 후 이번 기록을 마무리한다. 기존 artifacts 개발용 APK는 갱신하지 않는다.
- 서명 키·서명 비밀번호·제출 APK/AAB를 만들지 않았다. 기존 미커밋 변경을 보존했고 커밋·푸시하지 않았다.

다음 단계는 팀명과 Release 키 관리 방법 확정 후 서명 설정·최종 Release 설치 검증이다. 실제 통신은 현재 SharingTransport 경계를 유지해 별도로 연결한다.

## 2026-11-04 — Release 분리·정차 판별 커밋 분리

사용자 요청으로 미커밋 작업을 두 단계로 분리했다.

- `34e42ed` — build: separate standard and submission Release variants. 가이드라인·저장소 작업 규칙, 일반/제출 변형, 공유 demo 소스셋, 서명 전 패키징 차단, 당시 56개 구성 검사와 관련 문서를 포함한다. 이 시점의 제출용 시연은 정차 판별 전 고정 잠금이다.
- `feat: gate sharing demos on live parked vehicle state` — 실제 속도·기어·UX 상태 게이트, 권한·연결·신선도 검사, 주행 시 종료·잠금, 정차 복귀 시 명시적 새 시작, 정차 검사 36개와 구성 검사 확장을 포함한다.

기존 작업 파일의 최종 코드는 유지하고 Git 인덱스에서 작업 단계를 나눴다. 문서의 어색한 문장을 정리하고, 개발 중 제출 변형 컴파일을 유지하면서 최종 서명·산출물 생성을 나중에 묶는 운영 기준을 가이드라인에 덧붙였다. 코드 기능 변경이나 새 서명 키·제출 APK 생성은 없으며, 앞서 통과한 Java 127개·변형 구성 72개와 에뮬레이터 전환 검증 결과를 따른다. 커밋 정리를 위한 빌드·주행 시험은 재실행하지 않았다. 원격 푸시는 하지 않았다.

## 2026-11-04 — 실제 로컬 중계 서버와 두 에뮬레이터 공유

사용자 요청: 나중에 온라인 배포할 수 있는 로컬 중계를 구현하고 두 에뮬레이터의 위치·ETA·메시지 실제 송수신을 확인. 이번 작업의 커밋·푸시는 하지 않는다.

### 구현

- Node.js 기본 http/crypto만 사용하는 relay/server.mjs와 통합 검사·Dockerfile을 추가했다. 외부 라이브러리·DB·클라우드 계정 없이 127.0.0.1:8787 메모리 중계로 실행한다. HOST/PORT 변경과 HTTPS 역방향 프록시로 동일 API를 온라인에 배포할 수 있게 구성했다. 이번에는 외부 배포나 Docker 빌드를 수행하지 않았다.
- 임시 bearer 세션, 무작위 초대 코드, 본인 그룹만 조회/본인 상태만 쓰기, 요청 제한·보관 만료·탈퇴 삭제를 구현했다. 공유 시작마다 epoch를 발급하고 증가 순서 번호로 늦은 업로드를 거절한다. 메시지 UUID 중복 방지, 상대 앱 수신(received)과 사용자의 명시적 확인(ack)을 분리했다. 그룹의 전원 확인이 아니라 1대 이상 확인이라는 의미다.
- SharingTransport 경계에 실제 HttpSharingTransport를 추가했다. 실제 JourneyState를 JourneyPublisher로 변환하며 유효한 최신 GPS가 없으면 상태를 철회하고 ETA 조건이 안 맞으면 -1(확인 중)을 보낸다. 임의 좌표·ETA로 대체하지 않는다. 목적지 비교 키는 좌표와 목적지 이름을 함께 사용한다. 정확한 POI ID를 쓰는 개선은 후속이다.
- GroupSharingActivity는 실제 공유 전용이며 MockSharingTransport를 사용하지 않는다. 서버·차량 이름 입력, 그룹 생성/코드 참여, 위치 전송 동의, 공유 시작/중지·탈퇴, 차량 정보, 상황 메시지·확인 UI를 제공한다. 메인에서 진입한다. 앱 화면이 보일 때만 실제 위치 약 5초/수신 약 1초 주기로 처리한다. 화면 이탈 시 SDK 수집·업로드를 중단하고 탈퇴를 최선 시도한다. 네트워크 단절이면 서버 TTL로 제거된다.
- 그룹 설정·공유 시작·메시지·확인은 기존 실제 P/속도0/UX 제한 없음 게이트를 사용한다. 주행·상태 미확인에서는 설정을 잠그고 메시지를 숨긴다. 시작된 실제 공유는 화면이 보이는 동안 지속하고 중지·탈퇴는 가능하다. 기존 모의 모드는 종전의 주행 시 종료 정책을 유지한다.
- INTERNET과 실제 그룹 게이트용 CAR_SPEED/CAR_POWERTRAIN을 모든 변형에 선언했다. Debug HTTP는 10.0.2.2/127.0.0.1/localhost로 제한, Release는 HTTPS만 허용한다. 실제 그룹 Activity는 exported=false다. 기존 Release 패키징 차단을 유지했다. Debug 진단 경로의 자동 취소는 기본 24초를 유지하고 테스트 인자로 최대 10분까지 지정할 수 있게 했다. 진단 경로는 계속 Debug 전용이다.
- 서버·앱 토큰은 메모리만 사용하고 이동 이력·오프라인 업로드 큐·메시지 자동 재발송을 만들지 않는다. 노출되는 위치·목적지·메시지를 서버 로그에 저장하지 않는다. 최신 데이터의 원본 나이와 요청 왕복 지연을 반영해 오래된 ETA를 숨긴다.

### 자동 검증

- D:\projects\build-donghaeng.ps1로 assembleStandardDebug 및 네 변형 Java 컴파일 성공. JDK21의 기존 Java8 대상 경고와 Gradle9 호환 경고는 남아 있으나 실패 없음.
- scripts/test-state.ps1: 기존 127개 + RelayTest 19개 = 146개 통과. HTTPS/로컬 URL 정책, 권한·신선도·위치 불일치·취소 상태의 실제 송신 모델을 검사했다.
- scripts/verify-build-variants.ps1: 88개 통과. 네 변형 모의/진단 포함 범위, 실제 중계·비공개 Activity, INTERNET·차량 권한, 네트워크 보안 설정·Release DEBUG=false를 검사했다.
- D:\node.exe --test relay/server.test.mjs: 실제 HTTP 서버와 독립 세션 두 개(및 비참여 세션)의 통합 시나리오 통과. 그룹 격리, 공유 전 업로드 거부, 위치/ETA 전달, 오래된 순서·원본 거절, 중복 메시지, received 전 ack 거절, 외부 그룹 확인 거절, 수신/확인 전파, 중지 후 제거·새 epoch·이전 epoch 거절, 오프라인 나이·만료를 검사했다.

### 에뮬레이터 실제 송수신

Pleos_Connect_v2.1.0 두 독립 인스턴스(emulator-5554/5556, Android14/API34, user10)에 개발용 standardDebug를 설치했다. 동일 AVD 동시 실행은 둘 다 read-only가 필요했고 두 번째 인스턴스의 gRPC 포트를 8556으로 분리했다. 테스트 시작 전 P·속도0·필요 권한을 확인했다. 처음 부팅한 B에서 내비 앱 초기화 전에는 SDK 목적지/ETA가 없었으며 실제 내비 초기화·경로 요청 후 확인했다.

- 양쪽에서 서버 연결·그룹 생성/초대 참여 후, 각자 위치 공유 동의 이전에는 위치가 표시되지 않았다. 공유 시작 후 서로 다른 GPS 좌표·현재 주소와 내비 목적지·ETA가 상대 화면에 전달됐다.
- GPS는 에뮬레이터 시험 좌표를 사용했다. A 약 37.412720,127.094680, B 약 37.412798,127.094800. B를 37.412900,127.094898 및 37.412750,127.094700으로 변경하자 A에서 변경 좌표가 보였다. 앱에 원격 차량 예시 값을 주입한 시험이 아니다.
- 내비에는 Debug SDK 진단 경로(Donghaeng diagnostic destination, 37.4200/127.1000)를 요청했다. Pleos가 반환한 남은 시간 약 9~10분/1.8km를 실제 HTTP로 공유했다. 이 ETA는 에뮬레이터 내비 응답이며 실차 교통·주행 정확도를 검증한 결과가 아니다. 초기 검증에서 양쪽 ETA·같은 목적지 표시를 확인했다. 내비가 빈 목적지를 반환할 때는 상대에도 목적지 없음/ETA 확인 중이 표시됐다.
- 최종 Debug 빌드에서 A의 '휴게소 들를게요' → B 앱 수신 확인 → B 사용자 확인 → A '상대가 확인'을 확인했다. B의 '잠시 기다려 주세요'도 A 앱 수신 후 A 확인 → 양쪽 확인 상태로 바뀌었다. 화면 증거: .gradle/relay-a-ack.png, relay-emulator-5554-acknowledged.png, relay-emulator-5556-acknowledged.png.
- B가 화면을 벗어나자 그룹에서 B의 위치가 제거됐다. 서버 종료·재시작 후 기존 세션은 UNAUTHORIZED/연결 끊김이며 A는 확정 ETA 대신 최신 위치·ETA 확인 불가를 표시했다(.gradle/relay-a-offline.png). 서버 연결을 다시 누르면 그룹 미참여·공유 중지·이전 차량/메시지 없음으로 시작했다(.gradle/relay-a-reconnected.xml). 새 그룹·새 동의 없이 이전 공유를 복원하지 않았다.
- 실시간 화면 갱신 때문에 uiautomator가 idle 오류를 내는 경우가 있었다. 실패한 XML을 성공 증거로 사용하지 않고 최종 APK 스크린샷과 실제 버튼 조작으로 수신/확인을 판독했다. 동일 문구의 불필요한 setText도 줄였다.

### 범위와 후속 작업

실행·API·보관/인증 정책·온라인 이전 방법은 docs/local-relay.md, 제출 범위는 submission-release-guidelines.md와 README에 반영했다. 서버는 단일 프로세스 임시 인증 모델이며 온라인 서비스 운영·영구 계정·차량 소유 인증·휴대폰 공식 앱 중계·실차 데이터망/백그라운드 통신·실차 안전 심사·Release 런타임은 미검증이다. 실서비스 전 HTTPS 배포와 인증/참여 승인/삭제 정책을 확정해야 한다. Release 키·비밀번호·제출 APK/AAB는 생성하지 않았고 기존 artifacts 개발 APK를 교체하지 않았다. 커밋·푸시하지 않는다.

최종 빌드 재확인: 내비 경로를 새로 요청한 뒤 양쪽 모두 수정구 시흥동·동일 진단 목적지·약 9분/1.8km·같은 목적지가 표시됐다. 최종 증거는 .gradle/relay-emulator-5554-both-eta.png 및 relay-emulator-5556-both-eta.png다. B의 공유 중지 후 A에는 B의 공유 중지 상태만 남고 위치·ETA 행이 제거됐다(.gradle/relay-a-peer-stopped.png). 테스트 서버와 두 read-only 인스턴스를 종료해 임시 그룹·토큰·메시지를 제거했다. 원래 AVD 한 대(emulator-5554)를 일반 모드로 재시작하고 최종 Debug 설치·메인 진입을 확인했다. GPS는 시험 전 기본 좌표로 복원했다. 두 번째 인스턴스와 중계 서버는 종료된 상태다.

## 2026-11-04 — 실제 중계 작업 커밋 분리

후속 사용자 요청으로 서버, 앱 연동, 문서의 세 작업으로 커밋을 나눴다. 688571a는 Node 메모리 중계·HTTP 통합 검사·Dockerfile, 793424f는 실제 앱 통신·공유 UI·네트워크 정책·송신 모델·검사 스크립트다. 문서 커밋에는 실행/온라인 이전 안내, 제출 범위와 이번 검증 기록을 포함한다. 서버 통합 검사와 diff 검사를 다시 통과했다. 코드 기능은 추가 수정하지 않았고 이전 빌드·에뮬레이터 검증 결과를 유지한다. 데이터는 H2나 파일 DB가 아닌 서버 프로세스 RAM의 Map에 임시 보관한다. 원격 푸시는 하지 않았다.
