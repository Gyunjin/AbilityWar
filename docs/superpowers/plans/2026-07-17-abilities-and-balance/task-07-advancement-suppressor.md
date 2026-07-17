# Task 7: AdvancementSuppressor

> **먼저 읽으세요:** [00-overview.md](00-overview.md) — Goal, Architecture, **Global Constraints**(모든 태스크에 적용되는 구속 조건), File Structure.
> 이 파일 하나만 보고 작업할 수 있게 되어 있지만, Global Constraints는 이 태스크에도 그대로 적용됩니다.

[← Task 6](task-06-cooldown-command.md) · [진척 현황](README.md) · [Task 8 →](task-08-balance-and-hulk-fix.md)

---


**Files:**
- Create: `src/main/java/org/example/AdvancementSuppressor.java`
- Modify: `src/main/java/org/example/GameRules.java`
- Modify: `src/main/java/org/example/Main.java`

**Interfaces:**
- Consumes: 없음
- Produces: `static void GameRules.setAnnounceAdvancements(World world, boolean announce)`

- [ ] **Step 1: `GameRules`에 메서드 추가**

`GameRules.java`의 `isPvp` 메서드 아래에 삽입:

```java
    /** 발전과제 달성을 채팅에 알릴지 설정합니다. */
    public static void setAnnounceAdvancements(World world, boolean announce) {
        if (world == null) return;
        world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, announce);
    }
```

- [ ] **Step 2: `AdvancementSuppressor` 작성**

`src/main/java/org/example/AdvancementSuppressor.java`:

```java
package org.example;

import com.destroystokyo.paper.event.player.PlayerAdvancementCriterionGrantEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * 발전과제를 채팅과 토스트 양쪽에서 없앱니다.
 *
 * 왜 PlayerAdvancementDoneEvent가 아닌가: 그 이벤트는 Cancellable이 아닙니다. 있는 것은
 * message(Component) 오버로드뿐이라 채팅 브로드캐스트만 없앨 수 있고, 우측 상단 토스트는
 * 발전과제가 이미 부여된 뒤에 클라이언트가 띄우는 것이라 막지 못합니다.
 *
 * PlayerAdvancementCriterionGrantEvent(Paper)는 Cancellable입니다. 취소하면 달성 조건
 * 자체가 기록되지 않아 발전과제가 완성되지 않고, 따라서 토스트도 채팅도 발생하지 않습니다.
 * 데이터팩이 필요 없습니다.
 */
public class AdvancementSuppressor implements Listener {

    /**
     * 조합법 해금 발전과제는 통과시킵니다. 마인크래프트는 레시피북 해금을 발전과제로
     * 구현하므로, 전부 막으면 레시피북이 영영 비어 있게 됩니다. 제작 자체는 레시피를
     * 알면 되지만, 파밍 위주 게임에서 도감이 안 열리는 것은 실질적인 불편입니다.
     */
    private static final String RECIPE_PREFIX = "minecraft:recipes/";

    @EventHandler(ignoreCancelled = true)
    public void onCriterion(PlayerAdvancementCriterionGrantEvent event) {
        String key = event.getAdvancement().getKey().toString();
        if (key.startsWith(RECIPE_PREFIX)) return;
        event.setCancelled(true);
    }
}
```

- [ ] **Step 3: `Main.onEnable`에 리스너 등록**

`Main.java`에서 다음을 찾아:

```java
        getServer().getPluginManager().registerEvents(new EventSpawnCleanupListener(this), this);
```

바로 아래에 삽입:

```java
        getServer().getPluginManager().registerEvents(new AdvancementSuppressor(), this);
```

- [ ] **Step 4: 월드 생성 시 게임룰 적용**

`Main.java`의 `createFreshGameWorld()`에서 다음을 찾아:

```java
            newWorld.getWorldBorder().setCenter(0, 0);
            newWorld.getWorldBorder().setSize(cfgInitialBorderSize);
            GameRules.setPvp(newWorld, false);
```

바로 아래에 삽입:

```java
            // 발전과제 채팅 알림을 끕니다. AdvancementSuppressor가 토스트까지 막지만,
            // 예외로 통과시킨 레시피 발전과제가 채팅에 새어나가지 않도록 하는 이중 방어입니다.
            GameRules.setAnnounceAdvancements(newWorld, false);
```

`startGame()`에서 다음을 찾아:

```java
        gameWorld.getWorldBorder().setCenter(0, 0);
        gameWorld.getWorldBorder().setSize(cfgInitialBorderSize);
        GameRules.setPvp(gameWorld, false);
```

바로 아래에 삽입:

```java
        GameRules.setAnnounceAdvancements(gameWorld, false);
```

- [ ] **Step 5: 빌드 확인**

Run: `./gradlew build --console=plain`
Expected: BUILD SUCCESSFUL, 33 tests

- [ ] **Step 6: 커밋**

```bash
git add src/main/java/org/example/AdvancementSuppressor.java src/main/java/org/example/GameRules.java src/main/java/org/example/Main.java
git commit -m "feat: 발전과제 억제 (채팅 + 토스트)

PlayerAdvancementDoneEvent는 Cancellable이 아니라 토스트를 못 막습니다.
Paper의 PlayerAdvancementCriterionGrantEvent를 취소해 달성 조건 자체를
기록하지 않습니다. 레시피북 해금 발전과제는 예외로 통과시킵니다."
```

---

[← Task 6](task-06-cooldown-command.md) · [진척 현황](README.md) · [Task 8 →](task-08-balance-and-hulk-fix.md)
