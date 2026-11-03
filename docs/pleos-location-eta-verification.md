# Pleos 위치·ETA 연동 검증

검증일: 2026-11-04 (한국 시간). 에뮬레이터 로그의 날짜/시각은 호스트와 다르므로 로그 시각을 실제 시험 날짜로 해석하지 않는다.

증거: [선별한 원본 콜백 로그](pleos-probe-evidence.txt). 빌드 검증: assembleDebug 및 assembleRelease.

## 결과

| 항목 | 결과 | 해석 |
| --- | --- | --- |
| NaviHelper 연결 | 성공 | SDK 3.0.5 → 에뮬레이터 Navi Service 3.0.4 |
| 내비 현재 위치 조회 | 콜백 수신 성공 | 위도 37.4127212 / 경도 127.094681. GPS 변경을 따라가는 것은 확인하지 못함 |
| Android GPS 갱신 | 성공 | 두 가상 좌표를 주입하자 앱 콜백의 좌표도 변경됨 |
| 목적지별 ETA 조회 | 성공 | `requestTrip()` → `onTripInfo()` |
| 경로 안내 중 남은 시간 조회 | 성공 | `getDestinationInfo()` → `remainTime=300`, `onDrivingInfo()` → `duration=300` |
| FusedLocation 1.1.2 도입 | 미완료 | 공식 문서의 Maven 좌표에서 배포 아티팩트를 찾을 수 없어 의존성 제외 |

실제 SDK/시스템 콜백으로 값을 받았다. 그러나 **가상 기기와 가상 목적지로 한 시험이며, 실제 차량·실제 운전자 위치·이동 중 ETA 정확도 검증은 아니다.** 메인 데모 화면의 고정 숫자를 이 결과로 바꾼 것도 아니다.

## 환경과 재현

- AVD: Pleos_Connect_v2.1.0, Android API 34, 사용자 10.
- 앱: `io.demo.companion`, debug 빌드.
- SDK: `ai.pleos.playground:NaviHelper:3.0.5` (debugImplementation).
- SDK 의존 데이터 모듈: navi-data 3.0.4. 공식 sources JAR의 필드 주석으로 거리(m), ETA/남은 시간(s)를 확인했다.
- debug 전용 `PleosDiagnosticsActivity`에서 서비스 연결 콜백 이후 리스너를 등록하고 3초 간격으로 11회 조회한다.
- 진단 Activity는 DUMP 권한으로 외부 접근을 제한한다. 앱 자체 또는 adb shell에서 실행할 수 있다.
- 아래 명령의 사용자 번호와 기기 serial은 실제 실행 환경에 맞춰 바꾼다.

```sh
adb -s emulator-5554 install -r --user 10 app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell pm grant --user 10 io.demo.companion android.permission.ACCESS_FINE_LOCATION
adb -s emulator-5554 shell pm grant --user 10 io.demo.companion android.permission.ACCESS_COARSE_LOCATION
adb -s emulator-5554 shell am start --user 10 -n io.demo.companion/.PleosDiagnosticsActivity
adb -s emulator-5554 logcat -d -s DonghaengProbe:I
```

`--ez probeTrip true`를 추가하면 서로 다른 두 가상 목적지의 ETA를 6초 간격으로 요청한다. `--ez probeRoute true`도 추가하면 첫 가상 목적지로 경로 안내를 요청하며 24초 후 취소한다. **경로 시험은 에뮬레이터 전용으로 실행한다. 기존 경로가 없는 상태에서 시작하며 실제 운전 중 실행하지 않는다.** 실행 중인 진단 Activity에 다른 옵션으로 재시험하려면 먼저 종료한 뒤 실행한다.

## 수신한 값

동일한 내비 기준 위치에서 다음 두 목적지를 순차 조회했다.

| 가상 목적지 | 경로 거리 | 예상 소요 시간 |
| --- | --- | --- |
| 37.4200, 127.1000 | 1,876m | 300초 (5분) |
| 37.5000, 127.0300 | 14,068m | 1,260초 (21분) |

첫 목적지의 별도 사전 시험에서는 297초가 반환됐다. 값은 시험 시점에 따라 달라질 수 있다. `eta`는 절대 도착 시각이 아닌 초 단위 예상 소요 시간이다. 수신 시각에 더해 도착 시각을 표시하되 오래된 값에는 갱신 시각을 함께 표시해야 한다.

첫 목적지로 안내를 시작하자 다음 콜백도 수신했다.

```text
NAVIGATION_STATUS ROUTE_DRIVE
ROUTE_STARTED duration=300 distance=1800
DESTINATION latitude=37.42 longitude=127.1 remainDistance=1800 remainTime=300
DRIVING destination.duration=300 destination.distance=1800
CANCEL_TEST_ROUTE
ROUTE_CANCELLED
```

경로 안내 취소 뒤 목적지 좌표·이름이 비고 남은 시간이 0으로 돌아왔다. 목적지 미설정 상태의 0을 '곧 도착'으로 표시해서는 안 된다. `isRouting`은 위 안내 중에도 false였으므로 이 필드 하나만으로 안내 활성 여부를 판단하지 않는다. `NavigationStatus`, `NaviMode`, 목적지 존재 여부를 함께 검토한다.

## 위치 소스 차이

초기 Android GPS 좌표와 내비 좌표가 서로 달랐다. 다음 명령으로 명시적인 가상 좌표 두 개를 주입했다.

```sh
adb -s emulator-5554 emu geo fix 127.094681 37.4127212
# 첫 GPS 콜백을 받은 후 다음 좌표를 주입
adb -s emulator-5554 emu geo fix 127.095681 37.4137212
```

앱의 GPS 콜백은 각각 `(37.41272, 127.09468)`과 `(37.41372, 127.09568)`을 전달했다. 관찰한 위치 정확도는 5m, 위치 생성 후 경과 시간은 수 ms 수준이었다. 에뮬레이터가 보고한 값이며 실제 GPS 정확도를 뜻하지 않는다.

이때 NaviHelper의 `getCurrentLocationInfo()`는 계속 기존 `(37.4127212, 127.094681)`을 반환했다. 따라서 **내비의 현재 위치 콜백 수신 성공을 곧바로 실시간 GPS 추적 성공으로 해석할 수 없다.** 원인(내비 내부 상태, 위치 연동 설정, 에뮬레이터 구현)은 확정하지 않았다. GPS를 원래 시험 전 좌표로 복원하고 테스트 경로 취소를 확인했으며 진단 앱을 종료했다.

## 동행길에 적용할 때

1. 위치와 ETA를 각각 source·수신 시각과 함께 보관한다. GPS는 정확도와 위치 생성 후 경과 시간도 확인한다.
2. 현재 에뮬레이터에서 Android GPS는 위치 갱신의 검증된 경로다. 실제 Pleos 차량에서도 같은 지원이 보장되는 것은 아니며, FusedLocation 공개·지원 상태를 별도로 확인한다.
3. 안내 중 ETA는 목적지와 함께 `onDrivingInfo()`/`getDestinationInfo()`에서 받는다. 안내 전 ETA는 `requestTrip()` 결과로 구분한다.
4. 내비 기준 위치와 GPS가 크게 다르면 같은 기준에서 계산한 값인 것처럼 결합하지 않는다. 갱신 대기 또는 내비 연동 확인 상태로 표시한다.
5. 다음 단계는 동행자 상태 모델·화면에 위 소스 구분을 반영한 뒤 두 기기 공유를 연결하는 것이다.

## 공식 근거

- [NaviHelper 개요](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/intro)
- [Maven 설정](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/importing-sdk)
- [권한](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/declaring-permissions)
- [초기화](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/initialization-and-release)
- [이벤트 리스너](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/event-listeners)
- [FusedLocation 도입 문서](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/FusedLocation/project-setup)
- [SDK 제공 상태 안내](https://document.pleos.ai/en/docs/pleos-only/create-my-project)
- 필드 단위 확인: 공식 Maven `ai.pleos.playground:navi-data:3.0.4:sources`의 TripInfo.kt, DestinationInfo.kt, DrivingState.kt.
