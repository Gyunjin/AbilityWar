# Task 3: AbilityItems

> **먼저 읽으세요:** [00-overview.md](00-overview.md) — Goal, Architecture, **Global Constraints**(모든 태스크에 적용되는 구속 조건), File Structure.
> 이 파일 하나만 보고 작업할 수 있게 되어 있지만, Global Constraints는 이 태스크에도 그대로 적용됩니다.

[← Task 2](task-02-cooldown-migration.md) · [진척 현황](README.md) · [Task 4 →](task-04-vanish.md)

---


**Files:**
- Create: `src/main/java/org/example/abilities/AbilityItems.java`
- Modify: `src/main/java/org/example/abilities/Blinkerability.java`
- Modify: `src/main/java/org/example/abilities/Hulkability.java`
- Modify: `src/main/java/org/example/abilities/Necromancerability.java`
- Modify: `src/main/java/org/example/abilities/Poseidonability.java`
- Modify: `src/main/java/org/example/abilities/Teemoability.java`
- Modify: `src/main/java/org/example/AbilityManager.java`

**Interfaces:**
- Consumes: 없음
- Produces:
  - `static ItemStack AbilityItems.create(Material type, ChatColor color, String tag)`
  - `static boolean AbilityItems.isHolding(Player p, Material type, String tag)`
  - `static boolean AbilityItems.isBound(ItemStack item)`

- [ ] **Step 1: `AbilityItems` 작성**

`src/main/java/org/example/abilities/AbilityItems.java`:

```java
package org.example.abilities;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * 능력 아이템의 생성과 판정을 한 곳으로 모읍니다. 기존 6종이 각자 createItem()과
 * isHoldingX()를 복붙하고 있었고 판정 로직도 완전히 동일했습니다.
 *
 * 판정을 표시이름 문자열로 하는 것은 기존 코드의 방식을 그대로 따른 것입니다.
 * PDC 기반으로 바꾸면 모루로 이름만 바꾼 잡템이 귀속 아이템으로 인정되는 문제까지
 * 해결되지만, 그것은 이번 범위가 아닙니다.
 */
public final class AbilityItems {

    /** 모든 능력 아이템의 표시이름에 들어가는 공통 태그. 귀속 판정에 씁니다. */
    public static final String BOUND_TAG = "[능력]";

    private AbilityItems() {
    }

    /**
     * 표시이름이 color+tag인 귀속 아이템을 만듭니다.
     *
     * @param tag "[능력] "으로 시작해야 합니다. 안 그러면 귀속 판정(isBound)에 걸리지 않아
     *            사망 시 드랍되고 버릴 수 있게 됩니다.
     */
    public static ItemStack create(Material type, ChatColor color, String tag) {
        if (!tag.startsWith(BOUND_TAG)) {
            throw new IllegalArgumentException("능력 아이템 태그는 \"" + BOUND_TAG + "\"으로 시작해야 합니다: " + tag);
        }
        ItemStack item = new ItemStack(type);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color + tag);
            item.setItemMeta(meta);
        }
        return item;
    }

    /** p가 주손에 해당 태그의 아이템을 들고 있는지. */
    public static boolean isHolding(Player p, Material type, String tag) {
        ItemStack main = p.getInventory().getItemInMainHand();
        return matches(main, type, tag);
    }

    /** 귀속 아이템("[능력]" 태그)인지. */
    public static boolean isBound(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                && item.getItemMeta().getDisplayName().contains(BOUND_TAG);
    }

    private static boolean matches(ItemStack item, Material type, String tag) {
        return item != null && item.getType() == type
                && item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                && item.getItemMeta().getDisplayName().contains(tag);
    }
}
```

- [ ] **Step 2: `AbilityManager.isBoundItem`을 위임으로 교체**

`AbilityManager.java`에서 다음을 찾아:

```java
    /** 능력 아이템(태그 "[능력]")인지 확인합니다. 귀속 아이템 판정에 공용으로 사용합니다. */
    private boolean isBoundItem(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                && item.getItemMeta().getDisplayName().contains("[능력]");
    }
```

이렇게 바꾼다:

```java
    /** 능력 아이템(태그 "[능력]")인지 확인합니다. 판정 로직은 AbilityItems가 갖고 있습니다. */
    private boolean isBoundItem(ItemStack item) {
        return AbilityItems.isBound(item);
    }
```

import 추가:

```java
import org.example.abilities.AbilityItems;
```

- [ ] **Step 3: 블링커를 AbilityItems로 교체**

`Blinkerability.java`의 `createItem()`을 찾아 **삭제**하고, `onGrant`의 `p.getInventory().addItem(createItem());`을 이렇게 바꾼다:

```java
        p.getInventory().addItem(AbilityItems.create(Material.NETHER_STAR, ChatColor.AQUA, ITEM_TAG));
```

`onInteract`의 아이템 판정을 찾아:

```java
        ItemStack item = p.getInventory().getItemInMainHand();
        if (item.getType() != Material.NETHER_STAR || !item.hasItemMeta()) return;
        if (!item.getItemMeta().hasDisplayName() || !item.getItemMeta().getDisplayName().contains(ITEM_TAG)) return;
```

이렇게 바꾼다:

```java
        if (!AbilityItems.isHolding(p, Material.NETHER_STAR, ITEM_TAG)) return;
```

- [ ] **Step 4: 헐크를 AbilityItems로 교체**

`Hulkability.java`의 `createItem()`과 `isHoldingGauntlet(Player)`를 **삭제**한다.

`onGrant`:

```java
        p.getInventory().addItem(createItem());
```

→

```java
        p.getInventory().addItem(AbilityItems.create(Material.COBBLESTONE, ChatColor.RED, ITEM_TAG));
```

`onInteract`:

```java
        if (!isHoldingGauntlet(p)) return;
```

→

```java
        if (!AbilityItems.isHolding(p, Material.COBBLESTONE, ITEM_TAG)) return;
```

- [ ] **Step 5: 네크로맨서를 AbilityItems로 교체**

네크로맨서의 `createItem()`은 **lore(설명문) 2줄을 갖고 있어 다르다.** `AbilityItems.create()`는 lore를 다루지 않으므로, 생성 부분만 위임하고 lore는 그대로 얹는다.

`createItem()`을 이렇게 바꾼다:

```java
    private ItemStack createItem() {
        ItemStack item = AbilityItems.create(Material.BONE, ChatColor.DARK_RED, ITEM_TAG);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "우클릭 시 주인을 따르고 적을 공격하는 사령 좀비 3마리를 부립니다.");
            lore.add(ChatColor.GRAY + "지팡이를 들고 있으면 멀리 떨어진 소환수들이 주인 쪽으로 다가옵니다.");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
```

`isHoldingStaff(Player)`를 **삭제**하고, 호출부 `if (!isHoldingStaff(p)) return;` 2곳(`onInteract`, `onPassiveTick`)을 이렇게 바꾼다:

```java
        if (!AbilityItems.isHolding(p, Material.BONE, ITEM_TAG)) return;
```

**주의:** `onPassiveTick`의 호출은 `if (!isHoldingStaff(p)) return;` 형태다. 두 곳 모두 바꾼다.

- [ ] **Step 6: 포세이돈을 AbilityItems로 교체**

포세이돈의 `createItem()`은 **AttributeModifier와 인챈트, ItemFlag를 갖고 있어 다르다.** 생성만 위임한다.

`createItem()`의 첫 두 줄을 찾아:

```java
        ItemStack item = new ItemStack(Material.TRIDENT);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.BLUE + ITEM_TAG);
            meta.setUnbreakable(true);
```

이렇게 바꾼다:

```java
        ItemStack item = AbilityItems.create(Material.TRIDENT, ChatColor.BLUE, ITEM_TAG);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setUnbreakable(true);
```

나머지(AttributeModifier, 인챈트, ItemFlag, `item.setItemMeta(meta)`)는 그대로 둔다.

- [ ] **Step 7: 티모를 AbilityItems로 교체**

`Teemoability.java`의 `createItem()`과 `isHoldingBlowgun(Player)`를 **삭제**한다.

`onGrant`:

```java
        p.getInventory().addItem(createItem());
```

→

```java
        p.getInventory().addItem(AbilityItems.create(Material.BLAZE_ROD, ChatColor.GREEN, ITEM_TAG));
```

`onInteract`:

```java
        if (!isHoldingBlowgun(p)) return;
```

→

```java
        if (!AbilityItems.isHolding(p, Material.BLAZE_ROD, ITEM_TAG)) return;
```

- [ ] **Step 8: 잔재 확인**

Run: `grep -rn "isHoldingGauntlet\|isHoldingStaff\|isHoldingBlowgun" src/main/java/`
Expected: 출력 없음

Run: `grep -rn "getItemMeta().getDisplayName().contains" src/main/java/`
Expected: `AbilityItems.java`만 나온다

- [ ] **Step 9: 빌드 확인**

Run: `./gradlew build --console=plain`
Expected: BUILD SUCCESSFUL, 33 tests

- [ ] **Step 10: 커밋**

```bash
git add src/main/java/org/example/abilities/ src/main/java/org/example/AbilityManager.java
git commit -m "refactor: 능력 아이템 생성/판정을 AbilityItems로 공용화

기존 6종이 각자 복붙하던 createItem()/isHoldingX()를 한 곳으로 모읍니다.
lore(네크로맨서)와 인챈트/모디파이어(포세이돈)를 가진 아이템은 생성만
위임하고 나머지는 각자 유지합니다."
```

---

[← Task 2](task-02-cooldown-migration.md) · [진척 현황](README.md) · [Task 4 →](task-04-vanish.md)
