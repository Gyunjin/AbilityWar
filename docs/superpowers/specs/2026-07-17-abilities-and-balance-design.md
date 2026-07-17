# AbilityWar 능력 확장 설계 — 공용 인프라 정비와 신규 능력 5종

작성일: 2026-07-17

## 배경

능력 6종(블링커, 헐크, 포세이돈, 사망회귀, 네크로맨서, 티모)으로 운영해 왔다. 이벤트
시스템이 들어가면서 파밍 구간의 공백은 메워졌지만, 능력 쪽에는 세 가지 문제가 남아 있다.

- **밸런스가 안 맞는다.** 블링커 8초, 헐크 20초 쿨은 실측 결과 너무 길어 액티브를 쓸
  기회 자체가 적다.
- **헐크에 실제 버그가 있다.** 자기 슬램으로 자기가 낙하 데미지를 맞는다(3절에 근본 원인).
- **능력 풀이 얕다.** 6종이면 3~6인 판에서 매번 같은 조합이 나온다.

그리고 개발 편의 문제가 하나. 능력 테스트를 하려면 쿨타임을 매번 기다려야 해서 한 번
확인하는 데 20초씩 걸린다.

## 목표

1. 기존 능력의 밸런스와 버그를 잡는다.
2. 능력 풀을 6종 → 11종으로 늘려 조합 다양성을 확보한다.
3. 능력 코드의 중복(쿨타임, 아이템 판정)을 공용화해 신규 5종이 얹힐 토대를 만든다.
4. OP가 쿨타임 없이 능력을 테스트할 수 있게 한다.

비목표(이번 범위 밖):

- ChatColor → Adventure 마이그레이션 — 별도 작업.
- 기존 6종 능력의 로직 변경 — 블링커/헐크의 명시된 수치와 버그 외에는 손대지 않는다.
- 능력 간 밸런스 전면 재조정 — 신규 5종을 붙여보고 실측한 뒤에 판단한다.

---

## 1. 검증된 API 사실

이 설계는 **paper-api 26.1.2.build.74-stable jar를 직접 열어 확인한 사실** 위에 서 있다.
추측한 심볼은 없다. 아래는 코드 작성 시 반드시 지켜야 할 제약이다.

| 사실 | 영향 |
| --- | --- |
| `Attribute`와 `Sound`는 **enum이 아니라 인터페이스**(`OldEnum`, `Keyed`) | `switch`, `EnumMap`, `.ordinal()`, `.name()` 사용 불가. `getKey()`로 식별한다. |
| `Material`과 `Particle`은 **진짜 enum** | `switch` 가능. 기존 `Hulkability.isSoftBlock`이 그렇게 쓰고 있다. |
| `PotionEffectType`은 **abstract class + Registry** | 전체 열거는 `Registry.MOB_EFFECT`. `Registry.EFFECT`는 `@ApiStatus.Obsolete(1.21.4)`라 쓰지 않는다. |
| `AttributeModifier`의 `String`/`UUID` 생성자 4종은 **전부 deprecated** | `NamespacedKey` 생성자 2종만 쓴다. |
| `PlayerAdvancementDoneEvent`는 **Cancellable이 아니고**, `setMessage`가 아니라 `message(Component)` | 발전과제 차단에 쓸 수 없다. 2절 참고. |
| `PlayerAdvancementCriterionGrantEvent`(Paper)는 **Cancellable** | 발전과제 차단의 실제 수단. |
| `Particle.BLOCK`/`FALLING_DUST`는 **BlockData 인자 필수** | 안 넘기면 컴파일이 아니라 **런타임** `IllegalArgumentException`. |
| `KNOCKBACK_RESISTANCE`는 `1.0 - resistance` **배율**로 동작 | 넉백 300%는 **음수** -2.0으로 만든다. 6절 참고. |
| 메아리 아이템은 `ECHO_SHARD` 하나뿐 | `ECHO_EYE`/`ECHO_ORB`는 존재하지 않는다. |

확인된 상수(전부 존재): `Attribute.{MAX_HEALTH, MOVEMENT_SPEED, ATTACK_DAMAGE,
KNOCKBACK_RESISTANCE, JUMP_STRENGTH, SCALE, ENTITY_INTERACTION_RANGE}`,
`GameRule.ANNOUNCE_ADVANCEMENTS`, `Material.{BREEZE_ROD, ECHO_SHARD, SUSPICIOUS_SAND,
INK_SAC, IRON_INGOT, SANDSTONE, RED_SAND, SMOOTH_SANDSTONE, CUT_SANDSTONE,
CHISELED_SANDSTONE}`, `PotionEffectType.{WITHER, INSTANT_DAMAGE, SLOWNESS, SPEED,
REGENERATION, RESISTANCE, INVISIBILITY}`, `Sound.{ENTITY_GENERIC_EXPLODE,
ENTITY_BREEZE_WIND_BURST, BLOCK_SAND_BREAK, ENTITY_HUSK_AMBIENT}`,
`Player.{hideEntity, showEntity, setAllowFlight}`, `PlayerToggleFlightEvent`(Cancellable).

---

## 2. 공용 인프라

신규 능력 5종을 붙이기 전에, 5종이 전부 필요로 하는 것들을 먼저 공용화한다. 지금 안 하면
같은 코드를 5번 더 복붙하게 된다.

### 2.1 `Cooldown` (org.example.abilities)

현재 기존 6종 중 쿨타임을 쓰는 5종(블링커, 헐크, 사망회귀, 네크로맨서, 티모)이 각자
`private long lastUsed`, `lastUsed + COOLDOWN_MS`, 남은시간 포맷 문자열을 복붙하고 있다.
신규 5종 중 4종도 쿨타임이 있다.

```java
public final class Cooldown {
    private final long durationMs;
    private long lastUsed = 0;

    public Cooldown(long durationMs);

    /** 준비됐으면 즉시 소모하고 true. 아니면 p에게 남은시간을 안내하고 false. */
    public boolean tryUse(Player p, String busyMessage);

    /** 소모 없이 확인만 한다. */
    public boolean isReady();

    /** 남은 밀리초. 준비됐으면 0. */
    public long remainingMs();

    public void reset();

    // --- 전역 무시 스위치 ---
    public static void setDisabled(boolean disabled);
    public static boolean isDisabled();
}
```

`isDisabled()`가 true면 `tryUse`/`isReady`는 항상 통과하고 `remainingMs()`는 0을 낸다.
`lastUsed`는 그래도 갱신한다 — 마우가의 둔화 패시브처럼 "쿨이 도는 중"을 참조하는 로직이
쿨 제거 모드에서도 일관되게 "항상 준비됨"으로 보이게 하기 위함이다.

`setDisabled`는 **static**이다. 서버 전역 토글이고 인스턴스가 능력마다 따로 생기므로
다른 선택지가 없다. 게임 종료 시 자동으로 꺼지지 않는다 — OP가 명시적으로 끄는 물건이다.

**기존 5종의 마이그레이션이 이 작업의 실질적 크기다.** 각 능력의 `lastUsed` 필드와
쿨 체크 블록을 `Cooldown` 호출로 바꾸고, `resetCooldown()`은 `cooldown.reset()`으로
위임한다. 쿨타임이 없는 포세이돈은 손대지 않는다.

### 2.2 `Vanish` (org.example.abilities)

암살자와 데스웜의 "완전 투명화"에 쓴다.

**포션 방식을 쓰지 않는 이유:** 바닐라 `INVISIBILITY`는 입은 갑옷과 든 아이템이 그대로
보인다. 암살자는 검을 들어야 하고 갑옷도 입으므로, 포션만으로는 "완전 투명화"가 성립하지
않는다. 갑옷을 벗겨 숨기는 방법도 있지만 손에 든 무기는 여전히 남고, 벗긴 갑옷을 되돌리는
과정에서 사망/접속종료가 끼면 장비가 사라진다.

대신 `viewer.hideEntity(plugin, target)`으로 **다른 플레이어들의 클라이언트에서 대상을
통째로 지운다.** 갑옷, 손아이템, 이름표, 파티클까지 전부 사라진다. 대상 본인에게는 아무
변화가 없고, 서버 측 히트박스와 대미지 판정은 그대로 살아 있다.

```java
public final class Vanish {
    /** p를 다른 모든 온라인 플레이어에게서 숨긴다. */
    public static void hide(Plugin plugin, Player p);

    /** 숨김을 해제한다. 이미 보이는 상태면 무시. */
    public static void show(Plugin plugin, Player p);

    public static boolean isHidden(Player p);
}
```

`hide` 중인 플레이어 UUID를 static Set으로 들고 있는다. **`PlayerJoinEvent`에서 재적용이
필요하다** — 은신 도중 접속한 플레이어에게는 `hideEntity`가 걸려 있지 않아 그 사람 눈에만
보인다. 이 리스너는 `AbilityManager`가 이미 `onPlayerJoin`을 갖고 있으므로 거기에 얹는다.

`show`는 반드시 호출돼야 한다. 누락되면 영구 투명 버그가 된다(티모가 겪었던 문제와 같은
계열이다). 호출 경로는 은신 만료 태스크, 공격에 의한 해제, `onRevoke`, 그리고 안전망으로
`onPassiveTick`의 정합성 체크.

### 2.3 `AbilityItems` (org.example.abilities)

기존 6종이 각자 `createItem()`과 `isHoldingX()`를 복붙하고 있고, 판정 로직도
`getType() == X && hasItemMeta() && getItemMeta().hasDisplayName() &&
getDisplayName().contains(TAG)`로 완전히 동일하다.

```java
public final class AbilityItems {
    /** 표시이름이 color+tag인 귀속 아이템을 만든다. tag는 "[능력] "으로 시작해야 한다. */
    public static ItemStack create(Material type, ChatColor color, String tag);

    /** p가 주손에 해당 태그의 아이템을 들고 있는지. */
    public static boolean isHolding(Player p, Material type, String tag);

    /** 귀속 아이템("[능력]" 태그)인지. AbilityManager.isBoundItem을 대체한다. */
    public static boolean isBound(ItemStack item);
}
```

`AbilityManager.isBoundItem`은 이 클래스로 옮기고 위임한다.

### 2.4 `AdvancementSuppressor` (org.example)

발전과제를 채팅과 토스트 양쪽에서 없앤다.

```java
public class AdvancementSuppressor implements Listener {
    @EventHandler(ignoreCancelled = true)
    public void onCriterion(PlayerAdvancementCriterionGrantEvent event) { ... }
}
```

**왜 `PlayerAdvancementDoneEvent`가 아닌가:** 이 이벤트는 Cancellable이 아니다. 있는 건
`message(Component)` 오버로드뿐이라 **채팅 브로드캐스트만** 없앨 수 있고, 우측 상단
토스트는 발전과제가 이미 부여된 뒤에 클라이언트가 띄우는 것이라 막지 못한다.

`PlayerAdvancementCriterionGrantEvent`를 취소하면 달성 조건 자체가 기록되지 않아
발전과제가 완성되지 않고, 따라서 토스트도 채팅도 발생하지 않는다. 데이터팩이 필요 없다.

**단, `minecraft:recipes/`로 시작하는 발전과제는 예외로 통과시킨다.** 마인크래프트는
조합법 해금을 발전과제로 구현한다. 전부 막으면 레시피북이 영영 비어 있게 된다(제작 자체는
레시피를 알면 되지만, 파밍 위주 게임에서 도감이 안 열리는 건 실질적인 불편이다).

`ANNOUNCE_ADVANCEMENTS=false` 게임룰도 월드 생성 시 함께 건다. 이중 방어이며, 위 예외로
통과시킨 레시피 발전과제가 채팅에 새어나가지 않도록 하는 역할도 겸한다.
`GameRules`에 `setAnnounceAdvancements(World, boolean)`을 추가하고, 기존 `setPvp`와 같은
자리에서 호출한다.

### 2.5 `Ability` 인터페이스 훅 2종 추가

**이번 설계에서 기존 코드에 가하는 유일한 구조 변경이다.**

```java
/** 이 플레이어가 다른 엔티티를 근접 공격했을 때 호출됩니다. (공격자 기준) */
default void onDealMeleeDamage(Player attacker, EntityDamageByEntityEvent event) {}

/** 이 플레이어가 공중에서 비행 토글(스페이스 두 번)을 시도할 때 호출됩니다. */
default void onToggleFlight(Player p, PlayerToggleFlightEvent event) {}
```

`onDealMeleeDamage`가 필요한 이유: 현재 `AbilityManager.onAbilityDamageByEntity`는
**맞는 쪽**이 플레이어일 때만 위임한다. 신규 5종 중 **4종**(마우가 흡혈, 암살자 배율,
데스웜 습격 트리거, 바람 인도자 밀쳐냄)이 "내가 때릴 때"를 필요로 하므로, 공격자 기준
위임이 없으면 전부 각자 Listener를 등록해야 한다. 그건 이 코드베이스가 의도적으로
없앤 패턴이다(`AbilityManager` 주석 참고: 같은 능력 보유자 수만큼 중복 처리됨).

`AbilityManager`에 디스패치를 추가한다:

```java
@EventHandler
public void onAbilityDealDamage(EntityDamageByEntityEvent event) {
    if (!plugin.isGameStarted()) return;
    if (!(event.getDamager() instanceof Player attacker)) return;
    Ability a = playerAbilities.get(attacker.getUniqueId());
    if (a != null) a.onDealMeleeDamage(attacker, event);
}

@EventHandler
public void onToggleFlight(PlayerToggleFlightEvent event) {
    Player p = event.getPlayer();
    Ability a = playerAbilities.get(p.getUniqueId());
    if (a != null) a.onToggleFlight(p, event);
}
```

기존 `onAbilityDamageByEntity`(피격자 기준)는 그대로 둔다. 한 이벤트에서 두 훅이 모두
호출될 수 있다 — 플레이어가 플레이어를 때리면 공격자의 `onDealMeleeDamage`와 피격자의
`onEntityDamageByEntity`가 각각 자기 능력 인스턴스로 간다. 이는 의도된 동작이다.

### 2.6 `/쿨타임 [on|off]` 명령어

OP 전용 전역 토글. `Cooldown.setDisabled()`를 호출한다.

`plugin.yml`에 등록하고 `Main.onCommand`에 분기를 추가한다. `isAdminCommand`에 포함시켜
기존 OP 검사(`Main.onCommand`의 `isAdminCommand` 분기)를 그대로 탄다. 탭완성으로 `on`/`off`를 제공한다.

켤 때 전원에게 안내 메시지를 뿌린다 — 테스트 모드가 켜진 줄 모르고 진짜 게임을 하면
곤란하다.

---

## 3. 기존 능력 수정

### 3.1 블링커

`COOLDOWN_MS` 8000 → **4000**. 다른 변경 없음.

### 3.2 헐크

`COOLDOWN_MS` 20000 → **10000**.

`SLAM_DAMAGE`는 **이미 6.0이므로 건드리지 않는다**(`Hulkability.SLAM_DAMAGE`). "데미지 6으로
변경" 요청은 확인 결과 현재 값과 일치했다.

### 3.3 헐크 낙하 데미지 버그 — 근본 원인과 수정

**증상:** 헐크가 액티브로 점프한 뒤 착지하면 자기 슬램에 자기가 낙하 데미지를 입는다.

**근본 원인:** `fallDamageImmune = true`가 `performSlam()` 안에서 켜진다
(`performSlam()` 내부). 그런데 `performSlam()`은 `onPlayerMove`에서 착지를 감지한 뒤에야
불린다(`onPlayerMove`의 착지 감지). 착지 시 Bukkit의 이벤트 순서는:

1. `EntityDamageEvent(cause=FALL)` — 여기서 `fallDamageImmune`은 **아직 false**
2. `PlayerMoveEvent` → `performSlam()` → 그제서야 `fallDamageImmune = true`

즉 면역은 항상 **한 발 늦게** 켜진다. 3틱 뒤 꺼지는 타이머(`runTaskLater(..., 3L)`)는
이미 지나간 데미지에 아무 소용이 없다.

**수정:** 면역을 **점프하는 순간**(`onInteract`에서 `waitingForLanding = true`와 같은
자리) 켠다. 그리고 `performSlam()` 안에서 3틱 뒤 끄는 기존 타이머를 그대로 유지한다.
착지가 감지되기 전까지는 계속 면역이므로 1번 단계에서 정상적으로 취소된다.

`waitingForLanding`과 `fallDamageImmune`을 하나로 합치지 않는 이유: 슬램 직후 3틱의
유예가 필요하다. 착지 판정과 데미지가 정확히 같은 틱에 오지 않는 경우가 있어서
`waitingForLanding`이 false가 된 뒤에도 잠시 면역이 남아야 한다.

**회귀 방지:** 이 버그는 이벤트 순서 가정에서 나왔으므로, 수정 후 실제 서버에서 헐크
액티브를 높은 곳에서 써서 체력이 줄지 않는지 확인한다(9절).

---

## 4. 마우가

컨셉: 돌진으로 진입하고 근접전에서 버티는 탱커. 진입기를 쓰면 느려지는 리스크를 진다.

- **아이템:** `IRON_INGOT`, 태그 `[능력] 돌진`, 색 `ChatColor.RED`
- **쿨타임:** 20초

### 4.1 패시브 — 둔화

돌진 쿨타임이 도는 **20초 내내** 이동속도 -20%.

`Attribute.MOVEMENT_SPEED`에 `AttributeModifier(NamespacedKey("abilitywar",
"mauga_dash_slow"), -0.2, Operation.MULTIPLY_SCALAR_1)`를 건다.

`SLOWNESS` 포션을 쓰지 않는 이유: 둔화 1이 -15%라 20%가 정확히 안 나오고, 화면에 포션
아이콘과 파티클이 떠서 상대에게 정보를 준다.

**적용:** 돌진 발동 시 즉시 추가. **해제:** 쿨타임 종료 시각에 맞춘
`runTaskLater(20초)`로 제거. 안전망으로 `onPassiveTick`에서 "쿨이 준비됐는데 모디파이어가
남아 있으면 제거"를 확인한다(태스크가 유실되는 경우 대비). `onRevoke`에서도 반드시 제거 —
안 하면 능력이 바뀐 뒤에도 영구히 느려진다.

**쿨 제거 모드와의 상호작용:** `Cooldown.isDisabled()`면 `isReady()`가 항상 true이므로
안전망이 다음 틱에 모디파이어를 걷어간다. 의도된 동작이다.

### 4.2 패시브 — 흡혈

근접 타격 시 **30% 확률**로 회복.

- 체력이 최대치 미만이면: `setHealth(min(현재 + 1.0, 최대))`
- 체력이 최대치면: `ABSORPTION` 흡수 하트를 **최대 4.0(노란 하트 2개)까지** 1.0씩 누적

흡수 하트는 `PotionEffectType.ABSORPTION` 포션이 아니라 `p.setAbsorptionAmount()`로
직접 다룬다. 포션은 레벨당 4.0씩 고정 부여라 1.0씩 누적이 불가능하고, 만료 시 통째로
사라진다.

훅은 `onDealMeleeDamage`. **발사체는 제외한다** — `event.getDamager()`가 공격자 본인일
때만 처리한다(명세가 "근접 타격"이므로).

### 4.3 액티브 — 돌진

우클릭 발동. 시퀀스:

1. `RESISTANCE` amp 4(저항 5) 부여 — 바닐라에서 레벨당 20% 감소이므로 **레벨 5는 실제로
   100% 감소, 즉 무적**이다. 명세의 "저항 5(무적)"와 일치한다.
2. 바라보는 방향으로 돌진. 매 틱 `setVelocity(direction * 1.2)`를 반복 태스크로 유지한다.
   한 번만 주면 마찰로 즉시 감속한다.
3. 매 틱 파티클 재생.

**종료 조건 3가지**(반복 태스크가 감시):

- **벽 충돌** — 다음 틱 위치의 블록이 `isPassable()`이 아니면
- **3초 경과** — 60틱
- **좌클릭** — `onInteract`에서 `LEFT_CLICK_AIR`/`LEFT_CLICK_BLOCK`이면서 돌진 진행 중일 때

**종료 시 효과:**

- 주변 **4블록** 내 적에게 대미지 **8**
- 적을 위로 띄움
- `Sound.ENTITY_GENERIC_EXPLODE` + `Particle.EXPLOSION_EMITTER`
- `RESISTANCE` 제거

**띄운 적의 착지 처리:** 띄워진 각 적을 UUID로 추적하고, 반복 태스크(최대 10초 감시)로
`isOnGround()`가 다시 true가 되는 순간 `SLOWNESS` amp 9(구속 10)를 **2초** 건다. 10초
안에 착지하지 않으면(허공에 날아가 사라졌거나 죽었으면) 추적을 포기한다.

**모든 태스크는 `onRevoke`에서 취소해야 한다.** 돌진 중에 능력이 바뀌면 저항 5가 영구히
남는다.

---

## 5. 암살자

컨셉: 어둠 속에 숨어 단 한 번의 강력한 일격을 노린다.

- **아이템:** `INK_SAC`, 태그 `[능력] 그림자 은신`, 색 `ChatColor.DARK_GRAY`
- **쿨타임:** 20초

### 5.1 액티브 — 은신

우클릭 시 **5초간**:

- `Vanish.hide()` — 완전 투명 (2.2절)
- `SPEED` amp 1(신속 2)

### 5.2 일격

은신 중 근접 타격 시(`onDealMeleeDamage`):

- **등 뒤 판정 성공: 대미지 ×2.5**
- **그 외: 대미지 ×2**
- 어느 쪽이든 **공격 즉시 은신 해제** (`Vanish.show()` + `SPEED` 제거 + 만료 태스크 취소)

**등 뒤 판정:** 피격자의 시선 방향 벡터와 (공격자 위치 → 피격자 위치) 벡터를 각각
정규화해 내적한다. 내적 > 0.5(약 60도 이내)면 등 뒤로 본다. 피격자가 바라보는 방향과
공격이 들어온 방향이 같으면 = 뒤에서 맞은 것이다. Y축은 무시하고 수평 성분만 쓴다 —
위/아래에서 때린 걸 "등 뒤"로 치면 어색하다.

피격자가 `LivingEntity`가 아니면(방어구 거치대 등) 배율만 적용하고 등 뒤 판정은 건너뛴다.

`event.setDamage(event.getDamage() * 배율)`로 적용한다.

### 5.3 만료

5초가 지나면 은신이 스스로 풀린다. 반복/지연 태스크로 처리하고 `onRevoke`에서 취소한다.
`Vanish.show()`는 만료·공격·`onRevoke` 세 경로 모두에서 호출된다(중복 호출은 무해).

---

## 6. 데스웜

컨셉: 모래 위에서만 진가를 발휘하는 사막의 포식자. 지형이 곧 능력이다.

- **아이템:** `SUSPICIOUS_SAND`, 태그 `[능력] 사막의 포식자`, 색 `ChatColor.YELLOW`
- **쿨타임:** 25초

### 6.1 모래 지형 판정

발밑 블록(`p.getLocation().add(0, -1, 0)`)이 다음 중 하나면 "모래 위":

`SAND`, `RED_SAND`, `SANDSTONE`, `SMOOTH_SANDSTONE`, `CUT_SANDSTONE`,
`CHISELED_SANDSTONE`, `RED_SANDSTONE`, `SMOOTH_RED_SANDSTONE`, `CUT_RED_SANDSTONE`,
`CHISELED_RED_SANDSTONE`, `SUSPICIOUS_SAND`

`Material`은 enum이므로 `switch`로 판정한다(1절). 붉은 사암 계열도 포함한다 — 명세의
"사암"에 붉은 사암을 제외할 이유가 없고, 사막 지형에 자연 생성된다.

### 6.2 패시브

모래 위에 있으면 `REGENERATION` amp 0(재생 1) + `RESISTANCE` amp 0(저항 1)을 상시 부여.

`onPassiveTick`(1초 주기)에서 판정하고, **지속시간 40틱(2초)로 갱신**한다. 무한 지속을
쓰지 않는 이유는 티모 주석에 이미 적혀 있다 — 오프라인 플레이어에게서 해제할 수 없어
영구 버프 버그가 된다. 모래를 벗어나면 최대 2초 뒤 자연히 사라진다.

`ambient=false, particles=false, icon=false`로 걸어 상대에게 정보를 주지 않는다.

### 6.3 액티브 — 잠행

**발동 조건: 발밑이 모래 위일 때만.** 아니면 안내 메시지를 띄우고 **쿨타임을 소모하지
않는다**.

발동 시 **5초간**:

- `Vanish.hide()` — 완전 투명
- `SPEED` amp 2(신속 3)
- 매 틱 `Sound.ENTITY_HUSK_AMBIENT`를 낮은 피치로 — 땅속을 기어가는 소리
- 매 틱 발밑에 `Particle.BLOCK`에 `Material.SAND.createBlockData()`를 넘겨 모래 파티클.
  **BlockData 인자 누락 시 런타임 예외다**(1절).

**실패 조건:** 잠행 중 매 틱 발밑을 확인해 **모래가 아닌 블록 위로 올라가면 즉시 실패**.
은신을 풀고 습격 없이 종료하며, **쿨타임은 그대로 소모된 채 남는다**(명세: "쿨타임은
다시 기다려야함"). 실패 안내 메시지를 띄운다.

### 6.4 습격

**트리거 2가지:**

- 5초 경과
- 잠행 중 적을 선제 공격(`onDealMeleeDamage`)

**효과:**

- 주변 **3블록** 내 적에게 대미지 **7**
- 위로 **높게** 띄움 (마우가의 띄우기보다 강하게 — 명세가 "높게"를 명시)
- 은신 해제

선제 공격으로 트리거된 경우, 그 타격 자체의 대미지는 배율 없이 그대로 두고 습격 광역
대미지가 별도로 들어간다. 즉 맞은 대상은 평타 + 7을 함께 받는다.

**실패로 끝난 경우 습격은 발생하지 않는다** (6.3절).

---

## 7. 바람 인도자

컨셉: 하늘을 지배한다. 공격력은 없지만 위치를 지배한다.

- **아이템:** `BREEZE_ROD`, 태그 `[능력] 바람의 인도`, 색 `ChatColor.AQUA`
- **쿨타임:** 없음

### 7.1 더블 점프

`p.setAllowFlight(true)`를 상시 유지한다. 플레이어가 공중에서 스페이스를 두 번 누르면
`PlayerToggleFlightEvent`가 발생한다.

`onToggleFlight`에서:

1. `event.setCancelled(true)` — 크리에이티브 비행 진입을 막는다
2. `p.setFlying(false)`
3. 이미 이번 공중 체공에서 더블점프를 썼으면 여기서 종료
4. 안 썼으면: 바라보는 방향 + 위쪽으로 `setVelocity`, `Sound.ENTITY_BREEZE_JUMP` 재생,
   사용 플래그 설정

**착지 전 1회 제한:** `onPlayerMove`에서 `isOnGround()`가 true면 플래그를 초기화한다.
`Hulkability`가 착지 감지에 이미 쓰는 패턴과 같다.

`setAllowFlight(true)`는 `onGrant`에서 켜고 **`onRevoke`에서 반드시 `false`로 되돌린다** —
안 하면 능력이 바뀐 뒤에도 비행이 남는다. 관전자 모드(`/관전`)와 충돌하지 않는지
확인이 필요하다(9절).

### 7.2 낙하 데미지 면역

`onEntityDamage`에서 `cause == FALL`이면 무조건 취소. 상시 적용이라 조건이 없다.

### 7.3 밀쳐내기

`onDealMeleeDamage`에서 **주손에 브리즈 막대를 들고 있을 때만**:

- 피격자를 (공격자 → 피격자) 수평 방향으로 **약 15칸** 날아가도록 `setVelocity`
- 위로도 함께 띄움
- `Sound.ENTITY_BREEZE_WIND_BURST`

15칸은 속도가 아니라 **도달 거리** 목표다. 바닐라 넉백 II가 약 6칸이므로 그 2.5배다.
초기 속도 → 도달 거리는 마찰/중력에 좌우되므로 정확한 계산이 불가능하다. **초기값
`Vector(수평 * 2.6, 0.6, 수평 * 2.6)` 정도로 두고 실측으로 보정한다**(9절). 이 수치는
설계상 근사값이며, 실측 결과를 코드 주석에 남긴다.

브리즈 막대 자체의 대미지는 바닐라 기본값(막대기 수준)이다. 별도 조정하지 않는다 —
이 능력의 가치는 대미지가 아니라 위치 지배다.

---

## 8. 윤회자

컨셉: 순수한 도박. 매 판, 매 회귀마다 완전히 다른 캐릭터가 된다.

- **아이템:** `ECHO_SHARD`, 태그 `[능력] 회귀`, 색 `ChatColor.LIGHT_PURPLE`
- **쿨타임:** 3분

### 8.1 무작위 효과 3종

`Registry.MOB_EFFECT`로 **바닐라 효과 40종 전부**를 열거해 그중 3개를 중복 없이 뽑아
무한 지속으로 건다.

**제외 없이 전부 포함한다 — 사용자의 명시적 결정이다.** 그 결과:

- `WITHER`가 뽑히면 회귀 쿨(3분) 전에 **확정 사망**한다. 회복 수단이 없다.
- `INSTANT_DAMAGE`는 즉발 효과라 무한 지속을 걸어도 **부여 순간 1회만** 적용된다
  (amp 0 기준 6 데미지). 즉사는 아니지만 회귀할 때마다 다시 맞는다.
- `POISON`은 체력 1에서 멈추므로 단독으로는 죽이지 않는다.

이건 버그가 아니라 설계된 리스크다. 코드 주석에 이 결정을 명시해, 나중에 "시들음이
걸려서 죽는데요"를 버그로 오인해 고치지 않도록 한다.

**무한 지속의 예외적 허용:** 이 코드베이스는 무한 지속 포션을 금기시한다(티모 영구 투명
버그). 하지만 윤회자는 명세가 "무한 지속으로 적용됨"을 요구하고, 능력 자체가 살아 있는
동안 유지돼야 하므로 다른 선택지가 없다. **대신 `onRevoke`에서 3종을 명시적으로 제거하고,
`AbilityManager.onPlayerJoin`의 잔재 정리에 윤회자 효과 제거를 추가한다.** 오프라인 중
능력이 정리된 경우 재접속 시점이 유일한 복구 지점이다(기존 최대체력 정리와 같은 이유).

`Integer.MAX_VALUE` 대신 `PotionEffect.INFINITE_DURATION`(-1)을 쓴다.

### 8.2 무작위 스탯 6종

전부 `AttributeModifier`로 적용한다. 키는 `NamespacedKey("abilitywar",
"reincarnator_<속성>")`. **`NamespacedKey` 생성자만 쓴다**(1절).

| 스탯 | 속성 | Operation | 범위 |
| --- | --- | --- | --- |
| 공격 사거리 | `ENTITY_INTERACTION_RANGE` | `ADD_NUMBER` | -1.5 ~ +1.5 |
| 근접 대미지 | `ATTACK_DAMAGE` | `MULTIPLY_SCALAR_1` | -0.5 ~ +0.5 |
| 모델 크기 | `SCALE` | `ADD_NUMBER` | -0.5 ~ +0.5 (기본 1.0 → 0.5~1.5배) |
| 이동 속도 | `MOVEMENT_SPEED` | `MULTIPLY_SCALAR_1` | -0.4 ~ +0.4 |
| 점프력 | `JUMP_STRENGTH` | (아래 참고) | 높이 -2 ~ +2블록 |
| 피격 넉백 | `KNOCKBACK_RESISTANCE` | `ADD_NUMBER` | 아래 참고 |
| 최대 체력 | `MAX_HEALTH` | (아래 참고) | 2.0 ~ 40.0 (하트 1~20개) |

**넉백 0~300%:** `KNOCKBACK_RESISTANCE`는 이름과 달리 넉백에 `1.0 - resistance`를 곱한다.
따라서:

- 넉백 0%(철벽) → resistance **1.0**
- 넉백 100%(기본) → resistance **0.0**
- 넉백 300%(종이) → resistance **-2.0**

즉 `resistance = 1.0 - (배율)`이고, 배율을 0.0~3.0에서 뽑는다. **음수 저항이 실제로 넉백을
증폭시키는지 실측 확인이 필요하다**(9절). 증폭되지 않으면 `setVelocity` 기반 대체 구현으로
전환한다.

**점프 높이 ±2블록:** `JUMP_STRENGTH`는 블록 높이가 아니라 **초기 속도**(기본 0.42,
약 1.25블록)다. 높이는 속도의 제곱에 비례하므로 선형 대응이 안 된다.

```
목표높이 = 1.25 + random(-2.0, +2.0)         // -0.75 ~ 3.25
목표높이 = max(목표높이, 0.25)                // 하한 클램프: 0 이하면 점프 불가가 된다
JUMP_STRENGTH = 0.42 * sqrt(목표높이 / 1.25)
```

하한을 0.25블록으로 두는 이유: 명세의 -2블록은 기본 1.25블록에서 빼면 음수가 된다. 점프가
아예 불가능해지면 지형에 갇혀 게임이 끝나므로, "거의 못 뛴다"까지만 허용한다.

**최대 체력:** `AttributeModifier`가 아니라 기존 `PlayerStats.setMaxHealth()`를 쓴다.
헐크가 이미 쓰는 경로이고, 체력 클램프를 함께 처리해준다(`PlayerStats` 주석 참고).
`onRevoke`에서 `PlayerStats.resetMaxHealth()`. 최대 체력이 줄어드는 방향일 때 현재 체력이
초과하는 문제는 `PlayerStats`가 알아서 처리한다.

### 8.3 액티브 — 회귀

`ECHO_SHARD` 우클릭, 쿨 3분. **기존 효과 3종과 스탯 6종을 전부 제거한 뒤 새로 굴린다.**

제거 → 재적용 순서를 반드시 지킨다. 모디파이어를 안 지우고 또 걸면 중첩된다.

재굴림 결과를 플레이어에게 요약해 보여준다 — 뭐가 바뀌었는지 모르면 도박의 재미가 없다.

### 8.4 정리

`onRevoke`에서: 효과 3종 제거, 모디파이어 6종 제거, `PlayerStats.resetMaxHealth()`.
**`SCALE` 모디파이어 누락은 특히 위험하다** — 히트박스가 0.5배인 채로 남으면 다음 판까지
영향이 간다.

---

## 9. 검증

이 설계에는 **jar 검증으로 해소되지 않는 런타임 가정이 4개** 있다. 전부 실제 서버에서
확인해야 하며, 단위 테스트로는 잡히지 않는다.

| # | 가정 | 확인 방법 | 실패 시 대안 |
| --- | --- | --- | --- |
| 1 | 헐크 낙하 데미지 버그가 실제로 고쳐진다 | 높은 곳에서 액티브 사용 → 체력 불변 확인 | 이벤트 우선순위 조정 |
| 2 | 음수 `KNOCKBACK_RESISTANCE`가 넉백을 증폭한다 | 윤회자에 -2.0 고정 후 피격 | `setVelocity` 기반 구현 |
| 3 | 바람 인도자 밀쳐내기가 약 15칸 | 평지에서 타격 후 거리 측정 | 계수 보정 (수치를 주석에 기록) |
| 4 | `setAllowFlight(true)`가 `/관전`과 충돌하지 않는다 | 바람 인도자 사망 → 관전 모드 진입 | 관전 진입 시 플래그 해제 |

**단위 테스트:** 기존 테스트(`AbilityAssignerTest` 등)는 Bukkit에 의존하지 않는 순수 로직만
다룬다. 같은 기준으로 다음을 테스트한다:

- `Cooldown` — 준비/소모/리셋/전역 무시 플래그. Bukkit 의존 없음(`tryUse`의 Player 인자는
  메시지 전송용이므로 `isReady()`/`remainingMs()` 중심으로 테스트하고, 시간은 주입 가능하게
  설계한다)
- 윤회자 점프력 역산 — `높이 → JUMP_STRENGTH` 순수 함수로 분리해 경계값(-2, 0, +2) 테스트
- 윤회자 넉백 매핑 — `배율 → resistance` 순수 함수로 분리해 0%/100%/300% 테스트
- 암살자 등 뒤 판정 — 벡터 내적 로직을 순수 함수로 분리해 정면/측면/후면 테스트

**`AbilityRegistry` 등록 확인:** 신규 5종이 `getNames()`에 나오고 `create()`가 각각 새
인스턴스를 반환하는지. 기존 `SmokeTest` 패턴을 따른다.

---

## 10. 구현 순서

각 단계는 독립적으로 컴파일되고 커밋 가능하다.

1. **공용 인프라** — `Cooldown`, `AbilityItems`, `Vanish`, `Ability` 훅 2종 +
   `AbilityManager` 디스패치. 기존 6종을 `Cooldown`/`AbilityItems`로 마이그레이션.
2. **밸런스 + 명령어** — 블링커/헐크 쿨, 헐크 낙하 버그, `/쿨타임`,
   `AdvancementSuppressor` + 게임룰.
3. **능력 3종** — 마우가, 암살자, 데스웜. (`Vanish`와 `onDealMeleeDamage`를 공유)
4. **능력 2종** — 바람 인도자, 윤회자. (`onToggleFlight`, Attribute 조작)
5. **실측 검증** — 9절의 런타임 가정 4개 확인 및 수치 보정.

1단계를 먼저 하는 이유: 2~4단계가 전부 여기에 의존한다. 특히 1단계의 기존 능력
마이그레이션은 **동작 변경이 없는 순수 리팩터링**이므로, 여기서 회귀가 나면 이후 단계와
섞이기 전에 잡을 수 있다.
