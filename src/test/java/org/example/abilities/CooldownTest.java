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
