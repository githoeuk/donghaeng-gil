# 모의 시연 정차·주행 안전 게이트

기준일: 2026-11-04 (한국 시간). 대상은 모의 공유 시연의 진입과 전체 조작이다. 일반 메인 화면·모든 기능의 주행 안전 심사 완료를 뜻하지 않는다.

## 실제 신호와 판정

Pleos 공식 FAQ는 계기판과 같은 차량 속도에 AAOS `CarPropertyManager`의 `PERF_VEHICLE_SPEED` 사용을 안내하며, 주행 제한 가이드는 플랫폼 `CarUxRestrictions` 적용을 안내한다. 앱의 GPS 속도나 모의 동행 차량 값으로 정차를 판단하지 않는다. 별도 Vehicle Maven SDK는 추가하지 않고 SDK Platform 35의 android.car 라이브러리를 사용한다.

- 속도: `PERF_VEHICLE_SPEED`, m/s. **정확히 0**일 때만 허용 후보다. 전진·후진·아주 작은 0이 아닌 값과 NaN/Infinity는 잠근다.
- 기어: `GEAR_SELECTION == VehicleGear.GEAR_PARK`일 때만 허용한다. D/R/N에서 잠깐 멈춘 것은 시연 가능한 주차로 인정하지 않는다.
- UX: `CarUxRestrictions`가 존재하고 requiresDistractionOptimization=false, ActiveRestrictions=BASELINE(0)일 때만 허용한다.
- API 지원 확인·읽기/구독 권한·서비스 연결이 모두 필요하다. 하나라도 없으면 잠근다.
- 허용 조건을 1초 이상 유지하고, 유효 샘플의 나이가 3초 이내일 때만 열 수 있다.

## 수신·신선도·끊김

`VehicleParkingSource`는 비동기 Car 연결 후 실제 속도·기어 변경과 UX 제한을 구독한다. 속도는 UI rate(5Hz)를 요청하며 기어는 on-change다. 주기적 현재 속성 조회는 별도 단일 작업 스레드에서 최대 500ms마다 수행해 UI를 막지 않는다. IPC가 지연되면 추가 조회를 무한 큐에 쌓지 않는다.

속도 원본 timestamp와 조회 시작 시각 중 오래된 쪽을 단조 시계로 검사한다. 3초 초과·미래·누락 timestamp는 잠근다. 값을 받는 시각만으로 오래된 속도를 최신으로 취급하지 않는다. 기어는 on-change 속성이므로 ‘변경 시각이 오래됨’만으로 무효화하지 않고 현재 조회의 STATUS_AVAILABLE과 값, 성공한 조회 신선도를 확인한다. UX도 현재 조회와 구독을 함께 사용한다.

움직임·P 해제·속성 unavailable/error·UX 제한은 이벤트 수신 시 즉시 잠금 상태로 전환한다. 무응답은 500ms 감시 주기로 신선도 초과를 감지해 잠근다. 서비스 연결 수명·재연결 세대와 실패 revision으로 이전 조회가 잠금을 해제하지 못하게 한다. 화면 이탈 시 구독·Car 연결·작업 스레드를 해제한다.

## 화면과 모의 엔진

- MainActivity: 실제 정차 조건 전에는 모의 버튼이 비활성이다. 상태 설명과 차량 속도 권한 요청 버튼은 별도로 표시한다.
- 모의 안내 팝업 중 조건이 깨지면 팝업을 닫는다. 확인 버튼을 누르는 순간에도 다시 검사한다.
- SharingDemoActivity: 메인에서 넘긴 boolean/Intent extra를 신뢰하지 않고 자체 실제 모니터로 조건을 다시 확인한다. 시작 버튼은 정차 확인 후에만 활성화된다.
- 시작 후 주행·미수신·오류·오래됨이면 체험 화면의 가상 갱신을 멈추고 조작 화면을 잠금 화면으로 교체한다. 그룹·차량·메시지는 프로세스 메모리에 보관한다.
- 모든 모의 조작과 확인 응답 직전, 모의 tick 직전에도 조건을 검사한다. Debug 우회나 가상 P 값으로 해제하는 옵션은 없다.
- 실제 정차가 다시 확인되면 보관한 체험을 이어갈 수 있다. 체험 종료 또는 프로세스 종료 시 초기화한다.
- 실제 속도·기어·UX 정보는 안전 판단에만 쓰며 MockSharingTransport에 넣지 않는다. 실제 GPS·목적지 정보도 모의 공유에 쓰지 않는다.

## 권한과 빌드

`android.car.permission.CAR_SPEED`는 런타임 허용이 필요한 읽기 권한이며 `android.car.permission.CAR_POWERTRAIN`은 기어 읽기용이다. 두 권한은 src/demo 매니페스트에서만 선언되어 Debug와 submissionRelease에만 포함된다. standardRelease에는 추가하지 않는다. android.car 런타임 라이브러리는 optional로 선언하며 네트워크·차량 제어 권한은 추가하지 않는다.

standardRelease는 모의 시연을 계속 제외한다. submissionRelease는 실제 차량 상태 게이트가 적용된 모의 코드를 포함하고 DEBUG=false다. 서명 설정·제출 APK/AAB 생성은 계속 차단되어 있으며, 이 작업은 차단 해제나 제출 완료를 의미하지 않는다.

## 검증 결과와 한계

- ParkingState의 36개 검사를 추가했다. 기존 91개와 합해 독립 Java 검사 127개 통과.
- 차량 권한·라이브러리·모니터 포함 검사를 더해 네 변형 구성 검사 72개 통과. 네 변형 컴파일 성공.
- 개발용 standardDebug를 Pleos Connect v2.1.0, Android API 34, 사용자 10에서 실행했다.
- 속도 권한 미허용: 시연 버튼 비활성 확인. 허용 후 P·속도 0·UX 제한 없음: 메인 안내와 모의 Activity 각각의 실제 상태 확인 후 시연 시작·그룹 공유 확인.
- 공식 Virtual Console로 P→D(속도 0 포함), 가속 약 28km/h, 제동→0→P를 재현했다. D 전환에서 전체 잠금·시작 버튼 비활성·그룹 제거 확인. P 복귀 후 자동 재개 없이 새 시작을 요구하고, 재시작 시 그룹 미참여·메시지 없음 확인.
- 실제 속도 권한 회수 후 진입 잠금 확인. 권한·기어·속도는 마지막에 시험 전의 허용/P/0 상태로 복원했다.
- 오래됨·미래 timestamp·느린 조회·서비스 실패·이전 세대 응답은 순수 Java 검사로 검증했다. 차량 서비스 자체를 강제 중단하거나 실차 센서를 끊는 시험은 하지 않았다.
- Release는 컴파일·매니페스트로만 검사했다. 서명된 Release 실행·설치·실차 검증을 했다고 주장하지 않는다.

로컬 증거는 `.gradle/parking-active.png`, `.gradle/parking-drive.png`, `.gradle/parking-return.png` 및 `.gradle/parking-*.xml`이다. 실차 주행 안전 인증이나 App Market 승인을 대신하지 않는다.

## 공식 근거

- [Pleos 차량 속도 FAQ](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-developer-guide/vehicle-app-faq)
- [Pleos Driving Restriction Development Guide](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide)
- [AAOS 속도·기어 속성](https://developer.android.com/reference/android/car/VehiclePropertyIds)
- [AAOS CarUxRestrictionsManager](https://developer.android.com/reference/android/car/drivingstate/CarUxRestrictionsManager)
- [Pleos Virtual Console 사용 가이드](https://document.pleos.ai/en/docs/pleos-only/app-test-guide/virtual-console-guide)
