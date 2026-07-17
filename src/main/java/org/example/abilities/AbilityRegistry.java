package org.example.abilities;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;

/**
 * 능력 등록소.
 *
 * ★ 새 능력을 추가하는 방법 ★
 *   1. Ability를 구현하는 새 클래스(예: PhoenixAbility)를 만든다.
 *   2. 아래 static 블록에 register(PhoenixAbility::new); 한 줄만 추가한다.
 * 끝입니다. AbilityManager, Main 등 다른 코드는 전혀 건드릴 필요가 없습니다.
 */
public class AbilityRegistry {

    private static final Map<String, Supplier<Ability>> REGISTRY = new LinkedHashMap<>();

    static {
        register(Blinkerability::new);
        register(Hulkability::new);
        register(Poseidonability::new);
        register(DeathReversalAbility::new);
        register(Necromancerability::new);
        register(Teemoability::new);
        register(Maugaability::new);
        register(Assassinability::new);
        register(Deathwormability::new);

        // 새 능력은 이 아래에 한 줄씩 추가하면 됩니다. 예:
        // register(PhoenixAbility::new);
    }

    private static void register(Supplier<Ability> supplier) {
        String name = supplier.get().getName();
        REGISTRY.put(name, supplier);
    }

    /** 이름에 해당하는 능력의 새 인스턴스를 생성합니다. 없으면 null. */
    public static Ability create(String name) {
        for (Map.Entry<String, Supplier<Ability>> entry : REGISTRY.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue().get();
            }
        }
        return null;
    }

    /** 등록된 모든 능력 이름 목록 (탭완성, 목록 출력 등에 사용) */
    public static String[] getNames() {
        return REGISTRY.keySet().toArray(new String[0]);
    }

    public static boolean isValid(String name) {
        for (String n : REGISTRY.keySet()) {
            if (n.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    public static String randomName(Random random) {
        String[] names = getNames();
        return names[random.nextInt(names.length)];
    }
}