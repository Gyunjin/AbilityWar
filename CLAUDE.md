# AbilityWar

마인크래프트 Paper 플러그인. 한국어 "능력자 배틀로얄" 미니게임입니다.
친구 3~6명이 모여 한 판 20~30분, 하룻밤 2~3판 하는 소규모 사설 서버용입니다.

## 빌드와 실행

```bash
./gradlew build --console=plain     # 컴파일 + 테스트
./gradlew test  --console=plain     # 테스트만
```

산출물: `build/libs/AbilityWar-1.0-SNAPSHOT.jar` → 서버 `plugins/`에 복사 후 **완전 재시작**.
`/reload` 금지 — `onEnable`/`onDisable`이 게임 상태와 월드를 다루므로 리로드로는 정상 경로를 타지 않습니다.

### 로컬 환경 (머신마다 다름)

`gradle.properties`는 **추적하지 않습니다**(머신 종속). `gradle.properties.example`을 복사해 자기 경로를 넣으세요.

Java 25 툴체인이 필요합니다. `F:\tools\java\jdk`처럼 Gradle 자동 탐지 경로가 아닌 곳에 JDK가 있으면 `org.gradle.java.installations.paths`로 알려줘야 합니다. 이게 없으면 `release version 25 not supported`로 **컴파일이 시작조차 안 되고**, IDE에는 심볼 해석 실패로 인한 가짜 lint 오류가 잔뜩 뜹니다.

**대량의 lint/심볼 오류를 보면 코드를 고치기 전에 먼저 `./gradlew compileJava`가 도는지 확인하세요.** IDE 진단은 이 프로젝트에서 자주 거짓말을 합니다(`java.lang.Object cannot be resolved` 같은 게 뜨면 100% IDE 클래스패스 문제입니다). **gradle이 진실입니다.**

## 대상 버전 — 추측 금지

**대상 서버는 마인크래프트 26.1.2입니다.** 마인크래프트는 1.21.11 이후 **캘린더 버전 체계**(26.1, 26.2 …)로 전환했습니다. 모델 지식 시점 이후의 변화라 "26.1.2는 존재하지 않는 버전"이라고 넘겨짚지 마세요.

Paper 좌표도 바뀌었습니다 — `-R0.1-SNAPSHOT`이 사라지고 빌드 번호 방식입니다:

```kotlin
compileOnly("io.papermc.paper:paper-api:26.1.2.build.74-stable")   // 현재
// 옛 방식: io.papermc.paper:paper-api:1.21-R0.1-SNAPSHOT
```

**버전과 심볼은 기억에 의존하지 말고 항상 실물로 확인하세요:**

```bash
# 심볼 존재/타입 확인 — 이게 가장 빠르고 확실합니다
JAR=$(find ~/.gradle/caches -name "paper-api-26.1.2*.jar" | grep -v sources | head -1)
javap -cp "$JAR" org.bukkit.attribute.Attribute | grep MAX_HEALTH
javap -cp "$JAR" org.bukkit.World | grep -i pvp
javap -v -cp "$JAR" org.bukkit.World | grep -A6 "getPVP"   # deprecated 여부/since
```

- 존재하는 버전 목록: `https://fill.papermc.io/v3/projects/paper` (v2 API는 sunset)
- `plugin.yml`의 `api-version` 유효값: jar 안 `apiVersioning.json`의 `currentApiVersion`

이 프로젝트는 **스펙의 API 주장을 jar로 대조하다가 여러 번 살았습니다.** 새 API를 쓰기 전에 `javap`로 확인하는 데 30초면 됩니다.

## 이 코드베이스에서 반복해서 물렸던 함정

전부 실제로 발생했고 원인을 규명한 것들입니다. **각 항목은 코드 주석에도 남아 있습니다 — 지우지 마세요.**

### 플레이어 이동은 반드시 `HealthBarListener.safeTeleport()`

`HealthBarListener`가 모든 플레이어에게 TextDisplay를 **탑승물**로 붙입니다. Bukkit의 `CraftPlayer.teleport()`는 `if (entity.isVehicle()) return false;` — **탑승자가 있으면 조용히 실패하고 false를 반환합니다.**

그래서 한때 이 플러그인의 **모든 `p.teleport()`가 항상 실패**했습니다. `/맵변경`이 "완료되었습니다"만 띄우고 아무도 안 움직였고, 게임 시작 시 스폰 이동도, 블링커 대쉬도 전부 죽어 있었습니다. 증상은 "맵이 안 바뀜"으로 나타나서 원인이 전혀 안 보였습니다.

### 엔티티 표식은 PDC만 — `FixedMetadataValue` 금지

메타데이터는 **런타임 전용이라 저장되지 않습니다.** 반면 `setPersistent(true)` 엔티티는 저장됩니다. 재시작하면 엔티티는 살아남고 표식만 사라져 **어떤 방법으로도 찾아 지울 수 없는** 철갑옷 좀비가 월드에 영구히 박제됐습니다.

PDC는 엔티티와 함께 저장됩니다. `getPersistentDataContainer().set(key, PersistentDataType.STRING, ...)`.

### 포션 효과는 유한 지속시간만 — `Integer.MAX_VALUE` 금지

포션은 오프라인 플레이어에게서 줄어들지 않고, `onRevoke(null)`(오프라인 정리)에서는 해제할 수 없습니다. 티모가 무한 투명화를 걸었다가 **재접속해도 영구히 투명한** 버그가 났습니다.

짧게 걸고 주기적으로 갱신하면 갱신이 멈추는 순간 스스로 사라집니다. 유일한 예외는 윤회자이며, 그 경우 `PotionEffect.INFINITE_DURATION`을 쓰되 `onRevoke`와 `AbilityManager.onPlayerJoin` **양쪽에서** 제거합니다.

### 오프라인 플레이어의 상태는 재접속 시점에만 고칠 수 있다

`AbilityManager.clearAbilities()`는 오프라인 플레이어에게 `onRevoke(null)`을 호출합니다. `Player`가 없으면 최대 체력 원복(`setMaxHealth`)이 불가능합니다. 헐크가 게임 종료 전에 나가면 **최대 체력 40이 플레이어 데이터에 저장**됩니다.

**실제로 고칠 수 있는 유일한 지점이 `AbilityManager.onPlayerJoin`입니다.** 능력이 없는데 잔재가 남아 있으면 거기서 정리합니다.

### 우클릭은 손별로 두 번 발생한다

`PlayerInteractEvent`는 주손/보조손 각각 발생합니다. `if (event.getHand() != EquipmentSlot.HAND) return;` 가드가 없으면 능력이 두 번 발동하거나(변경권 2개 소모), 쿨타임 안내가 매번 뜹니다.

### PVP는 1.21.9부터 게임룰이다 — 저장됩니다

`World.setPVP()/getPVP()`는 deprecated. `GameRule.PVP`로 옮겨갔고, **게임룰은 `level.dat`에 저장됩니다**(1.21까지는 런타임 필드라 재시작마다 초기화됐습니다). `GameRules.setPvp()/isPvp()` 유틸을 쓰세요.

**PVP가 꺼져 있으면 "공격 주체가 플레이어인 대미지"만 차단됩니다.** 평타·헐크 슬램(`damage(x, p)`)·티모 독침 직격은 막히고 **독(POISON)은 통과합니다.** "평타는 안 들어가는데 독만 들어간다"는 증상이 보이면 PVP를 의심하세요.

### deprecated 대체 유틸

| 금지 | 사용 |
|---|---|
| `world.setPVP()` / `getPVP()` | `GameRules.setPvp()` / `isPvp()` |
| `p.setMaxHealth()` / `getMaxHealth()` / `resetMaxHealth()` | `PlayerStats.setMaxHealth()` / `getMaxHealth()` / `resetMaxHealth()` |
| `AttributeModifier(UUID/String, ...)` 생성자 | `NamespacedKey` 생성자 2종만 |
| `zombie.setBaby(false)` | `zombie.setAdult()` |
| `Attribute.GENERIC_MAX_HEALTH` | `Attribute.MAX_HEALTH` (26.1.2에 `GENERIC_*`은 없음) |

`PlayerStats`는 최대 체력을 낮출 때 **현재 체력 클램프를 함께 처리**합니다. 빠뜨리면 `IllegalArgumentException`이 납니다.

**`ChatColor`와 `Bukkit.broadcastMessage`는 계속 씁니다.** deprecated이지만 `forRemoval`이 아니고, `sendMessage(String)`이 정식 API로 남아 있어 §코드는 영구히 유효합니다. Adventure 마이그레이션은 별도 작업이며 `isBoundItem`의 표시이름 판정을 PDC로 바꾸는 것과 묶어야 합니다.

### 타입 함정

| 타입 | 실체 | 영향 |
|---|---|---|
| `Attribute`, `Sound` | **인터페이스** (`OldEnum`) | `switch`/`EnumMap`/`.ordinal()` 불가. `getKey()`로 식별 |
| `Material`, `Particle` | **진짜 enum** | `switch` 가능 |
| `PotionEffectType` | abstract class + Registry | 전체 열거는 `Registry.MOB_EFFECT` (`Registry.EFFECT`는 obsolete) |

`Particle.BLOCK`/`FALLING_DUST`는 **BlockData 인자 필수** — 누락 시 컴파일이 아니라 **런타임** `IllegalArgumentException`.

`WorldBorder.changeSize(size, N)`의 N은 **초가 아니라 틱**입니다. 옛 `setSize(size, 초)`를 그대로 옮겼다가 축소가 20배 빨리 끝났습니다. `setSize(size, TimeUnit.SECONDS, N)`로 단위를 명시하세요.

## 테스트

**이 프로젝트는 Bukkit 의존 코드를 서버 없이 검증할 수 없습니다. 컴파일 성공은 동작을 보장하지 않습니다.**

paper-api가 `compileOnly`라서:

- ❌ **테스트 코드가 `org.bukkit` 타입을 참조하면 `compileTestJava`가 실패합니다.**
- ✅ Bukkit을 내부에서 참조하는 클래스의 **순수 메서드 호출은 정상 동작**합니다(지연 해석).

그래서 검증 가능한 계산은 **`org.example.game` 패키지에 Bukkit 없는 순수 클래스로 분리**합니다. 이게 이 코드베이스의 확립된 패턴입니다:

- `AbilityAssigner` — 중복 없는 능력 배정
- `EventPicker` — 이벤트 추첨(연속 중복 방지)
- `BountySelector` — 최다 킬러 선정

시각이 필요하면 주입 가능하게 설계합니다(테스트에서 20초를 기다릴 수 없음).

나머지(능력 발동, 스폰, 정리)는 **서버 수동 검증이 유일한 확인 수단**입니다. 계획 문서에 체크리스트가 있습니다.

## 구조

```
org.example
├── Main                    게임 루프·명령어·월드·자기장·승리 판정
├── AbilityManager          전역 리스너 1개 → 해당 플레이어 능력 인스턴스로만 위임
├── TeamManager             팀 배정 + 아군 공격 방지
├── HealthBarListener       TextDisplay 탑승물로 머리 위 이름+체력 (safeTeleport 제공)
├── GameRules / PlayerStats deprecated API 래퍼
├── abilities/              Ability 인터페이스 + 구현체. AbilityRegistry에 한 줄로 등록
├── events/                 GameEvent(3메서드) + GameEventManager. 목록에 한 줄로 등록
└── game/                   Bukkit 없는 순수 로직 (테스트 대상)
```

**핵심 설계: 중앙 위임.** 능력/이벤트가 각자 `Listener`를 등록하면 **같은 능력 보유자 수만큼 이벤트가 중복 처리됩니다.** `AbilityManager`가 전역에서 한 번만 구독하고 UUID로 정확한 인스턴스에만 넘깁니다. 이 패턴을 깨지 마세요.

능력/이벤트 추가는 **클래스 하나 + 등록 한 줄**입니다. `Main`은 손대지 않습니다.

⚠️ `AbilityRegistry.register()`는 **클래스 로딩 시 생성자를 호출해 `getName()`을 읽습니다.** 능력 생성자에서 Bukkit API를 건드리면 `ExceptionInInitializerError`로 플러그인이 통째로 죽습니다.

## 문서

| 위치 | 내용 |
|---|---|
| `docs/superpowers/specs/` | 설계 문서. **왜 그렇게 했는지**가 여기 있습니다 |
| `docs/superpowers/plans/` | 구현 계획. 태스크별 파일 + 진척 인덱스(README.md) |
| `.superpowers/sdd/progress.md` | SDD 실행 원장. 재개 시 복구 지도 |

**진행 중:** `docs/superpowers/plans/2026-07-17-abilities-and-balance/` — 능력 6종 → 11종 확장 (16태스크).

## 작업 규칙

- **커밋 메시지와 코드 주석에 "왜"를 남기세요.** 이 코드베이스의 주석은 대부분 실제 사고의 기록입니다. "무엇을"이 아니라 "왜 이렇게 안 하면 안 되는지"를 적습니다.
- **의도된 설계를 버그로 오인해 고치지 마세요.** 파밍 중 PVP 차단, 접속 종료 = 탈락, 윤회자에 시들음이 뽑혀 죽는 것 — 전부 의도된 것입니다.
- **"고쳤다"고 말하기 전에 실행해서 확인하세요.** 이 프로젝트에서 "컴파일 통과"는 아무것도 증명하지 않습니다.
