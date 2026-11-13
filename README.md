# NextSpot — 주차 자리 핸드오버 · Parking Handover

모든 Pleos 차량이 주차 센서가 됩니다. 한 차가 떠나면, 근처에서 자리를 찾는 다음 차가 바로 그 자리로 안내됩니다.
Every Pleos car becomes a parking sensor: when one car leaves, the next car searching nearby is guided to that exact spot.

## 이 앱이 하는 일 · What the demo does

| 차 A (떠나는 차) · Car A | 차 B (찾는 차) · Car B |
| --- | --- |
| 1. 문 열고 닫기 + 2. 내비 설정 → "곧 빌 가능성" 게시 | "주차 자리 찾기" |
| 3. 주차 브레이크 해제 (또는 P단 해제) → "확정" | 음성: "150미터 앞에 자리가 곧 납니다. 갈까요?" |
| 45초 안에 확정이 없으면 → 제안 철회 | "응, 갈게" → 내비 목적지를 그 자리로 설정 |
| 음성: "다음 차가 이 자리로 오고 있어요" | "주차했어요" → 차 A에 크레딧 +1 |

## 빌드 종류 · Two build variants

- **simDebug**: 아무 안드로이드 에뮬레이터에서 실행. 차 신호는 버튼으로 대신합니다. Runs on any Android emulator; buttons stand in for car signals. **Start here.**
- **pleosDebug**: Pleos Connect Emulator용. 실제 SDK 신호(문, 기어, 주차 브레이크, 내비)를 씁니다. 버튼도 그대로 동작합니다. For the Pleos Connect Emulator; uses real SDK signals, buttons still work.

Android Studio 왼쪽 아래 **Build Variants** 창에서 고릅니다. Pick it in the **Build Variants** panel (bottom-left in Android Studio).

---

## 1단계: Firebase 설정 (10분) · Step 1: Firebase

1. https://console.firebase.google.com → **프로젝트 추가 (Add project)** → 이름 `NextSpot`.
2. **Android 앱 추가 (Add app → Android)** → 패키지 이름 (package name): `com.nextspot.app`
3. **google-services.json** 다운로드 → 이 프로젝트의 `app/` 폴더에 넣기. Put it in the `app/` folder.
4. **Build → Firestore Database → 데이터베이스 만들기 → 테스트 모드로 시작 (Start in test mode)**.

> 테스트 모드는 30일 후 만료됩니다. 공모전 데모에는 충분합니다. Test mode expires after 30 days; fine for the demo.

## 2단계: 시뮬레이션으로 실행 · Step 2: Run the sim version

1. Android Studio → **Open** → 이 `NextSpot` 폴더 선택. Gradle 동기화가 끝날 때까지 기다리기.
2. **Build Variants** → `simDebug`.
3. **Device Manager**에서 에뮬레이터 2개 만들기 (예: Pixel, API 34). Create two emulators.
4. 둘 다 실행 → 각각 앱 설치 (Run ▶, 위쪽 기기 목록에서 기기 선택). Run the app on both.
5. 한쪽은 **차 A**, 다른 쪽은 **차 B** 선택.
6. 차 B: **주차 자리 찾기**. 차 A: **1 → 2** 버튼 → 차 B에 제안이 뜨고 음성이 나옵니다. **응, 갈게** → 차 A: **3** → 차 B: **주차했어요**.

## 3단계: Pleos Connect Emulator · Step 3: Pleos

1. Pleos Playground에서 받은 **에뮬레이터 URL**을 Android Studio **SDK Manager → SDK Update Sites → Add**에 등록.
2. **SDK Platforms → API 34 Pleos Connect system image** 설치 → Android Studio 재시작.
3. **Device Manager → Create device → Automotive → Pleos Connect** (고급 설정: Cold boot, RAM 4GB).
4. 에뮬레이터 실행 후 Pleos 프로젝트 페이지의 **CRN adb 명령 3줄** 실행 (프로젝트에 에뮬레이터 등록).
5. **Build Variants** → `pleosDebug` → 실행.
6. 에뮬레이터의 차량 설정 패널에서 문, 기어, 주차 브레이크 값을 바꿔 보세요. 값이 안 바뀌면 화면 버튼을 쓰면 됩니다.

## 제출용 APK · Release APK for submission

**Build → Build App Bundle(s) / APK(s) → Build APK(s)** (pleosRelease) 또는 터미널:

```
./gradlew assemblePleosRelease
```

파일: `app/build/outputs/apk/pleos/release/app-pleos-release.apk` → 이름을 `팀명_NextSpot_release.apk`로 바꿔 GitHub `apk/` 폴더에 넣기.

## 보안 · Security

- **클라이언트 시크릿(Pleos)과 google-services.json은 GitHub에 올리지 마세요.** `.gitignore`에 이미 들어 있습니다. Never commit the Pleos client secret or `google-services.json`.
- 저장소를 공개해야 한다면, 심사위원에게는 README에 "Firebase 설정 방법"만 남기면 됩니다.

## 코드 구조 · Code map

| 파일 · File | 역할 · Role |
| --- | --- |
| `car/DepartureDetector.kt` | 핵심 로직: 예고 → 확정 → 철회 · core logic: likely → confirmed → withdrawn |
| `data/SpotRepository.kt` | Firestore 읽기/쓰기, 먼저 수락한 차만 배정 (트랜잭션) · first-accept-wins transaction |
| `ui/CarAScreen.kt`, `ui/CarBScreen.kt` | 두 화면 · the two screens |
| `src/pleos/.../Platform.kt` | Pleos SDK: Door, Gear, NaviHelper + 주차 브레이크 (AAOS) |
| `src/sim/.../Platform.kt` | 시뮬레이션 (버튼만) · simulation |
| `data/Geo.kt` | 데모 위치 (부산대 근처) · demo positions |

## 다음에 할 것 · Next steps

- 음성 대답 "응"을 Pleos SpeechToText SDK로 받기 (지금은 버튼). Voice "yes" via Pleos STT (now a button).
- 음성 안내를 Pleos TextToSpeech SDK로 바꾸기 (지금은 안드로이드 기본 TTS). Swap to Pleos TTS.
- 크레딧으로 우선 배정, 전용 그룹 관리. Credit-based priority, group management.
- 자리 찾기 시작 계기 표현: (1) 내비 목적지 근처 도착 (2) 운전자 음성 "주차 자리 찾아줘". 화면에 계기를 보여 주려면 `DemoEngine`에 `searchTrigger` 상태와 음성 명령 인식(Pleos STT)이 필요합니다 (zol). Show how "find a spot" starts: near the destination, or by voice.
