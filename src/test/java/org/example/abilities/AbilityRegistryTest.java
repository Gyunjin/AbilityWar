package org.example.abilities;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AbilityRegistry의 static 블록은 각 능력의 생성자를 호출해 getName()을 읽습니다.
 * 능력 생성자가 Bukkit API를 건드리면 여기서 ExceptionInInitializerError가 납니다.
 * 이 테스트는 그것도 함께 잡습니다.
 */
class AbilityRegistryTest {

    private static final List<String> EXPECTED = List.of(
            "블링커", "헐크", "포세이돈", "사망회귀", "네크로맨서", "티모",
            "마우가", "암살자", "데스웜", "바람 인도자", "윤회자");

    @Test
    void 능력이_11종_등록되어_있다() {
        assertEquals(11, AbilityRegistry.getNames().length);
    }

    @Test
    void 기대한_이름이_전부_있다() {
        Set<String> actual = new HashSet<>(Arrays.asList(AbilityRegistry.getNames()));
        for (String name : EXPECTED) {
            assertTrue(actual.contains(name), "등록되지 않은 능력: " + name);
        }
    }

    @Test
    void 이름이_중복되지_않는다() {
        String[] names = AbilityRegistry.getNames();
        assertEquals(names.length, new HashSet<>(Arrays.asList(names)).size(),
                "이름이 겹치면 레지스트리가 하나를 덮어씁니다: " + Arrays.toString(names));
    }

    @Test
    void create가_각각_새_인스턴스를_반환한다() {
        // 인스턴스를 공유하면 쿨타임/소환물 상태가 플레이어 간에 섞입니다.
        for (String name : AbilityRegistry.getNames()) {
            Ability a = AbilityRegistry.create(name);
            Ability b = AbilityRegistry.create(name);
            assertNotNull(a, name + " 생성 실패");
            assertNotNull(b, name + " 생성 실패");
            assertNotSame(a, b, name + "이 같은 인스턴스를 재사용합니다");
        }
    }

    @Test
    void create한_능력의_이름이_등록된_이름과_같다() {
        for (String name : AbilityRegistry.getNames()) {
            assertEquals(name, AbilityRegistry.create(name).getName());
        }
    }

    @Test
    void 없는_이름은_null() {
        assertNotNull(AbilityRegistry.getNames());
        assertEquals(null, AbilityRegistry.create("존재하지않는능력"));
    }

    @Test
    void isValid가_등록된_이름에만_true() {
        for (String name : AbilityRegistry.getNames()) {
            assertTrue(AbilityRegistry.isValid(name), name);
        }
        assertFalse(AbilityRegistry.isValid("존재하지않는능력"));
    }
}
