# Pleos 내비 화면 PiP / 알림 테스트 결과

환경: Pleos Connect v2.1.0, Android API 34, RAM 4GB, 2560×1440. 테스트 앱 io.demo.companion (targetSdk 34).
상태: P, 0km/h. 내비 앱을 열었으나 지도 타일이 표시되지 않았고 경로 안내는 시작하지 않았다. 주행 중 동작은 검증하지 않았다.

- PiP: FEATURE_PICTURE_IN_PICTURE=false, enterPictureInPictureMode()=false. 표준 PiP 창 생성 실패.
- ETA: HIGH 채널 + CATEGORY_STATUS. 시스템 등록 성공. 관찰 시 내비 위 팝업 및 Pleos 알림센터에는 나타나지 않음.
- 그룹 메시지: HIGH 채널 + CATEGORY_MESSAGE + MessagingStyle. 내비 화면 상단 카드 표시, 수 초 후 사라짐. 종 아이콘 알림센터에도 보관됨.
- navigation-message.png: 내비 위 메시지 팝업 실제 캡처.
- notification-center.png: 내비 위 알림센터 실제 캡처.
- test-log.txt: 앱 실행 로그.

알림은 로컬 데모이며 차량 간 통신은 구현하지 않았다. 메시지 테스트는 동행자가 직접 보낸 합류 메시지를 가정한다. 자동 ETA를 메시지로 분류한 테스트가 아니다.
현재 이미지의 표준 PiP 실패가 모든 차량 또는 향후 Pleos 버전의 지원 여부를 증명하지 않는다. 위젯/별도 Pleos 확장 API, 8:2 사용자 지정 분할은 검증하지 않았다.

재현: 앱 목록에서 Companion Display Test 실행 → Clear test notifications → Group message notification after 10 sec → 하단 내비 아이콘 선택 → 약 10초 후 상단 팝업 확인. 같은 알림을 반복 갱신하면 팝업이 재표시되지 않을 수 있으므로 초기화 후 재현한다.

## 주행 중 추가 검증 (2026-11-03)

공식 Virtual Console에서 D 기어 및 가속 페달을 이용해 이동 상태 재현. 주행 제한 확인 결과 Display 0: DO=true, UxR=80(0x50). parked=0, idling/moving=0x50이며 주행 전환 로그가 존재했다. 0x50 구성은 메시지 본문 숨김까지 포함하지 않으므로 본문이 보인 결과를 타 차량에 일반화하면 안 된다.

1. 기존 그룹 메시지: D/165km/h 캡처에서 내비 위 팝업 표시 확인 (driving-message.png).
2. 동행자가 도착시간을 공유한 메시지: D/186km/h에서 I will arrive in 12 minutes. 표시 확인 (driving-shared-eta.png).
3. 동행자가 만날 장소 변경을 제안한 메시지: D/189km/h에서 변경 메시지 표시 확인 (driving-shared-destination.png).
속도는 가상 콘솔 가속에 따른 테스트 값이다. 실제 차량 시험 및 활성 경로 안내 시험이 아니다. 내비 지도 타일은 여전히 표시되지 않았다.

공유 메시지에는 CATEGORY_MESSAGE + MessagingStyle + HIGH 채널을 사용하고, BroadcastReceiver로 처리하는 Mark as read 액션을 추가했다. 이 이미지에서는 버튼이 실제 표시됐다. 버튼 동작 자체는 별도 검증하지 않았다. 차량 간 통신, 실제 ETA 계산, 실제 내비 목적지 변경은 구현하지 않았으며 로컬 가상 발신 메시지 표시만 검증했다.

자동 ETA CATEGORY_STATUS 알림은 이전 P 상태 시험에서 시스템 등록만 확인됐고 팝업은 나타나지 않았다. 이번 추가 시험에서 자동 STATUS를 이동 상태로 재시험하지는 않았다. 일반 앱의 HUN은 AAOS 기준 CALL/MESSAGE/NAVIGATION에 한정된다. 단순 상태값을 내비 안내나 타인의 메시지로 잘못 분류하지 말 것. 자동 동행 이벤트 알림의 허용 범주는 Pleos 측 확인이 남아 있다.

알림 조건 제안 (제품 설계안, 플랫폼 지원 확정 아님):
- 그룹장/동행자의 목적지 변경 공유: 변경 1건당 1회. 내비 목적지 자동 적용 금지, 별도 확인.
- ETA 지연: 마지막 통지 대비 5분 이상 지연, 60초 지속 확인, 같은 사람 10분 쿨다운.
- 도착 임박: 5분 이내 최초 진입 시 1회. ETA 왕복 변화로 반복하지 않도록 여유 구간 적용.
- 도착/합류 요청: 각 단계 1회. 위치 정확도와 정차를 확인하는 방식 검토.
- 수 초의 ETA 변화 및 매분 갱신: 내부 데이터만 갱신. 팝업 없음.
- 회전/합류 등 조작이 임박했음을 신뢰할 수 있는 데이터로 판단 가능할 때만 중요하지 않은 알림 지연. 그렇지 않으면 해당 판단이 가능하다고 가정하지 말 것.

진입 방법: GNB 사용자 편집 슬롯에 앱 아이콘 배치(공식 지원). 이는 앱 실행 바로가기이며 내비 위 요약 팝업 전용 버튼은 아님. 핸들 버튼의 임의 전역 매핑 API는 확인되지 않았다. Gleo Interface의 앱 함수/App Schema 등록으로 동행 상태 조회 음성을 설계할 수 있으나 이 앱 연동 및 에뮬레이터 지원은 미검증.

공식 자료:
- https://developer.android.com/training/cars/platforms/automotive-os/notifications
- https://document.pleos.ai/en/docs/connect/guide/getting-started/application-development/use-vehicle-emulator
- https://document.pleos.ai/en/docs/connect/guide/docs-design/components/gnb
- https://document.pleos.ai/docs/pleos-only/app-integration-guide/guide/core-concepts

테스트 후 가상 차량은 P/0km/h로 복원했다.

참고: 이 문서는 기존 테스트 프로젝트의 시험 기록입니다. 문서에 언급된 PNG와 원시 로그는 이 저장소에 포함하지 않았습니다. 동행길 이름을 반영한 빌드는 2026-11-04 assembleDebug 성공을 확인했으며 에뮬레이터 동작 시험을 새로 수행하지는 않았습니다.
