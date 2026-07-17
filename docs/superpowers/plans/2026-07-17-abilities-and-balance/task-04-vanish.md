# Task 4: Vanish

> **먼저 읽으세요:** [00-overview.md](00-overview.md) — Goal, Architecture, **Global Constraints**(모든 태스크에 적용되는 구속 조건), File Structure.
> 이 파일 하나만 보고 작업할 수 있게 되어 있지만, Global Constraints는 이 태스크에도 그대로 적용됩니다.

[← Task 3](task-03-ability-items.md) · [진척 현황](README.md) · [Task 5 →](task-05-ability-hooks.md)

---


**Files:**
- Create: `src/main/java/org/example/abilities/Vanish.java`
- Modify: `src/main/java/org/example/AbilityManager.java` (`onPlayerJoin`)

**Interfaces:**
- Consumes: 없음
- Produces:
  - `static void Vanish.hide(Plugin plugin, Player p)`
  - `static void Vanish.show(Plugin plugin, Player p)`
  - `static boolean Vanish.isHidden(Player p)`
  - `static void Vanish.reapplyFor(Plugin plugin, Player joiner)` — 접속자에게 기존 은신자들을 다시 숨김

- [ ] **Step 1: `Vanish` 작성**

`src/main/java/org/example/abilities/Vanish.java`:

```java
package org.example.abilities;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 완전 투명화. 암살자와 데스웜이 씁니다.
 *
 * 포션(INVISIBILITY)을 쓰지 않는 이유: 바닐라 투명화는 입은 갑옷과 든 아이템이 그대로
 * 보입니다. 암살자는 검을 들어야 하므로 포션만으로는 "완전 투명화"가 성립하지 않습니다.
 * 갑옷을 벗겨 숨기는 방법도 있지만 손에 든 무기는 여전히 남고, 벗긴 갑옷을 되돌리는
 * 과정에서 사망/접속종료가 끼면 장비가 사라집니다.
 *
 * 대신 viewer.hideEntity()로 다른 플레이어들의 클라이언트에서 대상을 통째로 지웁니다.
 * 갑옷, 손아이템, 이름표, 파티클까지 전부 사라집니다. 대상 본인에게는 아무 변화가 없고,
 * 서버 측 히트박스와 대미지 판정은 그대로 살아 있습니다.
 *
 * show()는 반드시 호출돼야 합니다. 누락되면 영구 투명 버그가 됩니다(티모가 겪었던 것과
 * 같은 계열). 호출 경로: 은신 만료 태스크, 공격에 의한 해제, onRevoke, 그리고 안전망으로
 * onPassiveTick의 정합성 체크.
 */
public final class Vanish {

    private static final Set<UUID> hidden = Collections.synchronizedSet(new HashSet<>());

    private Vanish() {
    }

    /** p를 다른 모든 온라인 플레이어에게서 숨깁니다. */
    public static void hide(Plugin plugin, Player p) {
        if (p == null) return;
        hidden.add(p.getUniqueId());
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.equals(p)) {
                viewer.hidePlayer(plugin, p);
            }
        }
    }

    /** 숨김을 해제합니다. 이미 보이는 상태면 무시(중복 호출은 무해). */
    public static void show(Plugin plugin, Player p) {
        if (p == null) return;
        hidden.remove(p.getUniqueId());
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.equals(p)) {
                viewer.showPlayer(plugin, p);
            }
        }
    }

    public static boolean isHidden(Player p) {
        return p != null && hidden.contains(p.getUniqueId());
    }

    /**
     * 방금 접속한 플레이어에게 현재 은신 중인 사람들을 다시 숨깁니다.
     *
     * 이게 없으면 은신 도중 접속한 플레이어에게는 hidePlayer가 걸려 있지 않아
     * 그 사람 눈에만 은신자가 보입니다.
     */
    public static void reapplyFor(Plugin plugin, Player joiner) {
        if (joiner == null) return;
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(joiner) && isHidden(other)) {
                joiner.hidePlayer(plugin, other);
            }
        }
    }
}
```

**주의:** 스펙은 `hideEntity`/`showEntity`를 언급하지만, `Player`를 숨기는 데는 `hidePlayer(Plugin, Player)`/`showPlayer(Plugin, Player)`가 정확한 오버로드다. 둘 다 존재하며 `hideEntity(Plugin, Entity)`는 임의 엔티티용이다. 플레이어 전용 오버로드를 쓴다.

- [ ] **Step 2: `AbilityManager.onPlayerJoin`에 재적용 추가**

`AbilityManager.java`의 `onPlayerJoin`을 찾아:

```java
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        if (playerAbilities.containsKey(p.getUniqueId())) return; // 능력 보유 중이면 정상 상태

        PlayerStats.resetMaxHealth(p);
        // 티모의 투명화는 스스로 만료되지만, 이전 버전에서 무한 지속시간으로 걸려
        // 저장돼 있던 플레이어를 위해 여기서도 확실히 정리합니다.
        p.removePotionEffect(PotionEffectType.INVISIBILITY);
    }
```

이렇게 바꾼다:

```java
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();

        // 은신 중인 다른 플레이어를 이 접속자에게도 숨깁니다. 이게 없으면 방금 들어온
        // 사람 눈에만 은신자가 보입니다. 능력 보유 여부와 무관하므로 조기 반환보다 앞에 둡니다.
        Vanish.reapplyFor(plugin, p);

        if (playerAbilities.containsKey(p.getUniqueId())) return; // 능력 보유 중이면 정상 상태

        PlayerStats.resetMaxHealth(p);
        // 티모의 투명화는 스스로 만료되지만, 이전 버전에서 무한 지속시간으로 걸려
        // 저장돼 있던 플레이어를 위해 여기서도 확실히 정리합니다.
        p.removePotionEffect(PotionEffectType.INVISIBILITY);
        // 능력이 없는데 은신 상태로 남아 있으면 잔재입니다.
        Vanish.show(plugin, p);
    }
```

import 추가:

```java
import org.example.abilities.Vanish;
```

- [ ] **Step 3: 빌드 확인**

Run: `./gradlew build --console=plain`
Expected: BUILD SUCCESSFUL, 33 tests

- [ ] **Step 4: 커밋**

```bash
git add src/main/java/org/example/abilities/Vanish.java src/main/java/org/example/AbilityManager.java
git commit -m "feat: 완전 투명화 Vanish 추가

hidePlayer로 클라이언트에서 통째로 지웁니다. 포션 투명화는 갑옷과 손아이템이
그대로 보여서 암살자에 쓸 수 없습니다. 은신 중 접속한 플레이어에게 재적용하는
경로를 AbilityManager.onPlayerJoin에 얹었습니다."
```

---

[← Task 3](task-03-ability-items.md) · [진척 현황](README.md) · [Task 5 →](task-05-ability-hooks.md)
