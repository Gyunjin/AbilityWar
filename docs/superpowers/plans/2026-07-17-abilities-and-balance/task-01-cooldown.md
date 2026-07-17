# Task 1: Cooldown

> **먼저 읽으세요:** [00-overview.md](00-overview.md) — Goal, Architecture, **Global Constraints**(모든 태스크에 적용되는 구속 조건), File Structure.
> 이 파일 하나만 보고 작업할 수 있게 되어 있지만, Global Constraints는 이 태스크에도 그대로 적용됩니다.

[진척 현황](README.md) · [Task 2 →](task-02-cooldown-migration.md)

---


**Files:**
- Create: `src/main/java/org/example/abilities/Cooldown.java`
- Test: `src/test/java/org/example/abilities/CooldownTest.java`

**Interfaces:**
- Consumes: 없음
- Produces:
  - `new Cooldown(long durationMs)` / `new Cooldown(long durationMs, LongSupplier clock)`
  - `boolean tryUse(Player p, String busyMessage)` — 준비됐으면 소모하고 true, 아니면 p에게 안내 후 false
  - `boolean consume()` — `tryUse`와 같지만 메시지를 보내지 않는다. Bukkit이 필요 없으므로 테스트가 이것을 쓴다.
  - `boolean isReady()`, `long remainingMs()`, `void reset()`
  - `static void setDisabled(boolean)`, `static boolean isDisabled()`

시간을 주입 가능하게 만드는 이유: 테스트에서 20초를 실제로 기다릴 수 없다. 기본 생성자는 `System::currentTimeMillis`를 쓴다.

- [ ] **Step 1: 실패하는 테스트 작성**

`src/test/java/org/example/abilities/CooldownTest.java`:

```java
package org.example.abilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cooldown은 tryUse(Player, String) 때문에 Bukkit 타입을 참조하지만, 이 테스트는
 * 그 메서드를 부르지 않으므로 paper-api 없이도 클래스가 정상 로드됩니다.
 * (테스트 코드가 Bukkit 타입을 직접 참조하면 compileTestJava가 실패합니다.)
 */
class CooldownTest {

    /** 테스트용 가짜 시계. 20초를 실제로 기다릴 수 없으므로 시간을 주입합니다. */
    private static final class FakeClock {
        long now = 1_000_000L;
        long get() { return now; }
        void advance(long ms) { now += ms; }
    }

    @AfterEach
    void resetGlobalSwitch() {
        // static 상태이므로 테스트 간 누수를 막습니다.
        Cooldown.setDisabled(false);
    }

    @Test
    void 처음에는_준비된_상태() {
        FakeClock clock = new FakeClock();
        Cooldown cd = new Cooldown(5000, clock::get);

        assertTrue(cd.isReady());
        assertEquals(0, cd.remainingMs());
    }

    @Test
    void 소모하면_준비되지_않은_상태가_된다() {
        FakeClock clock = new FakeClock();
        Cooldown cd = new Cooldown(5000, clock::get);

        assertTrue(cd.consume());
        assertFalse(cd.isReady());
        assertEquals(5000, cd.remainingMs());
    }

    @Test
    void 소모_후_시간이_지나면_다시_준비된다() {
        FakeClock clock = new FakeClock();
        Cooldown cd = new Cooldown(5000, clock::get);
        cd.consume();

        clock.advance(4999);
        assertFalse(cd.isReady());
        assertEquals(1, cd.remainingMs());

        clock.advance(1);
        assertTrue(cd.isReady());
        assertEquals(0, cd.remainingMs());
    }

    @Test
    void 준비되지_않았으면_consume이_false를_내고_시각을_갱신하지_않는다() {
        FakeClock clock = new FakeClock();
        Cooldown cd = new Cooldown(5000, clock::get);
        cd.consume();

        clock.advance(3000);
        assertFalse(cd.consume());
        // 실패한 consume이 lastUsed를 밀어버리면 쿨이 영원히 안 끝납니다.
        assertEquals(2000, cd.remainingMs());
    }

    @Test
    void reset하면_즉시_준비된다() {
        FakeClock clock = new FakeClock();
        Cooldown cd = new Cooldown(5000, clock::get);
        cd.consume();
        assertFalse(cd.isReady());

        cd.reset();
        assertTrue(cd.isReady());
        assertEquals(0, cd.remainingMs());
    }

    @Test
    void 전역_무시가_켜지면_항상_준비된_상태() {
        FakeClock clock = new FakeClock();
        Cooldown cd = new Cooldown(5000, clock::get);
        cd.consume();
        assertFalse(cd.isReady());

        Cooldown.setDisabled(true);
        assertTrue(cd.isReady());
        assertEquals(0, cd.remainingMs());
        assertTrue(cd.consume());
    }

    @Test
    void 전역_무시를_끄면_원래_쿨타임_상태로_돌아온다() {
        FakeClock clock = new FakeClock();
        Cooldown cd = new Cooldown(5000, clock::get);

        Cooldown.setDisabled(true);
        cd.consume();          // 무시 모드에서도 lastUsed는 갱신되어야 합니다
        Cooldown.setDisabled(false);

        // 무시 모드에서 갱신해두지 않았다면 여기서 isReady()가 true가 되어버립니다.
        assertFalse(cd.isReady());
        assertEquals(5000, cd.remainingMs());
    }

    @Test
    void 전역_무시는_모든_인스턴스에_적용된다() {
        FakeClock clock = new FakeClock();
        Cooldown a = new Cooldown(5000, clock::get);
        Cooldown b = new Cooldown(99999, clock::get);
        a.consume();
        b.consume();

        Cooldown.setDisabled(true);
        assertTrue(a.isReady());
        assertTrue(b.isReady());
    }

    @Test
    void isDisabled가_현재_상태를_반영한다() {
        assertFalse(Cooldown.isDisabled());
        Cooldown.setDisabled(true);
        assertTrue(Cooldown.isDisabled());
    }
}
```

- [ ] **Step 2: 테스트가 실패하는지 확인**

Run: `./gradlew test --console=plain`
Expected: FAIL — `cannot find symbol: class Cooldown`

- [ ] **Step 3: 최소 구현**

`src/main/java/org/example/abilities/Cooldown.java`:

```java
package org.example.abilities;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.function.LongSupplier;

/**
 * 능력 쿨타임. 기존 5종이 각자 lastUsed 필드와 남은시간 포맷 문자열을 복붙하고 있던 것을
 * 한 곳으로 모읍니다.
 *
 * 시계를 주입 가능하게 둔 이유: 테스트에서 20초를 실제로 기다릴 수 없습니다.
 * 운영 코드는 인자 없는 생성자를 쓰면 됩니다.
 *
 * 이 클래스는 tryUse()의 안내 메시지 때문에 Bukkit 타입을 참조하지만, 나머지 메서드는
 * 순수 계산이라 paper-api 없이도 테스트에서 호출할 수 있습니다.
 */
public final class Cooldown {

    /**
     * 전역 쿨타임 무시 스위치(/쿨타임 명령어). static인 이유: 서버 전역 토글인데
     * 인스턴스는 능력마다 따로 생기므로 다른 선택지가 없습니다.
     * 게임 종료 시 자동으로 꺼지지 않습니다 - OP가 명시적으로 끄는 물건입니다.
     */
    private static volatile boolean disabled = false;

    private final long durationMs;
    private final LongSupplier clock;
    private long lastUsed = 0;

    public Cooldown(long durationMs) {
        this(durationMs, System::currentTimeMillis);
    }

    public Cooldown(long durationMs, LongSupplier clock) {
        this.durationMs = durationMs;
        this.clock = clock;
    }

    public static void setDisabled(boolean value) {
        disabled = value;
    }

    public static boolean isDisabled() {
        return disabled;
    }

    /** 남은 밀리초. 준비됐으면 0. */
    public long remainingMs() {
        if (disabled) return 0;
        long left = (lastUsed + durationMs) - clock.getAsLong();
        return Math.max(0, left);
    }

    public boolean isReady() {
        return remainingMs() <= 0;
    }

    /**
     * 준비됐으면 소모하고 true. 아니면 아무것도 하지 않고 false.
     *
     * 무시 모드에서도 lastUsed는 갱신합니다 - 마우가의 둔화 패시브처럼 "쿨이 도는 중"을
     * 참조하는 로직이 무시 모드에서도 일관되게 "항상 준비됨"으로 보이게 하기 위함입니다.
     */
    public boolean consume() {
        if (!isReady()) return false;
        lastUsed = clock.getAsLong();
        return true;
    }

    /** 준비됐으면 소모하고 true. 아니면 p에게 남은시간을 안내하고 false. */
    public boolean tryUse(Player p, String busyMessage) {
        long left = remainingMs();
        if (left > 0) {
            p.sendMessage(ChatColor.RED + busyMessage
                    + " (남은 시간: " + String.format("%.1f", left / 1000.0) + "초)");
            return false;
        }
        lastUsed = clock.getAsLong();
        return true;
    }

    public void reset() {
        lastUsed = 0;
    }
}
```

- [ ] **Step 4: 테스트 통과 확인**

Run: `./gradlew test --console=plain`
Expected: BUILD SUCCESSFUL, 33 tests (기존 24 + 신규 9)

- [ ] **Step 5: 커밋**

```bash
git add src/main/java/org/example/abilities/Cooldown.java src/test/java/org/example/abilities/CooldownTest.java
git commit -m "feat: 공용 Cooldown 추가

기존 5종이 각자 복붙하던 lastUsed/남은시간 포맷을 한 곳으로 모읍니다.
전역 무시 스위치(/쿨타임)와 테스트용 시계 주입을 포함합니다."
```

---

[진척 현황](README.md) · [Task 2 →](task-02-cooldown-migration.md)
