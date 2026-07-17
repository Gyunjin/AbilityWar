# Task 6: /쿨타임 명령어

> **먼저 읽으세요:** [00-overview.md](00-overview.md) — Goal, Architecture, **Global Constraints**(모든 태스크에 적용되는 구속 조건), File Structure.
> 이 파일 하나만 보고 작업할 수 있게 되어 있지만, Global Constraints는 이 태스크에도 그대로 적용됩니다.

[← Task 5](task-05-ability-hooks.md) · [진척 현황](README.md) · [Task 7 →](task-07-advancement-suppressor.md)

---


**Files:**
- Modify: `src/main/resources/plugin.yml`
- Modify: `src/main/java/org/example/Main.java`

**Interfaces:**
- Consumes: `Cooldown.setDisabled(boolean)`, `Cooldown.isDisabled()` (Task 1)
- Produces: 없음

- [ ] **Step 1: `plugin.yml`에 명령어 등록**

`src/main/resources/plugin.yml`의 `commands:` 블록 끝(마지막 항목인 `관전:` 아래)에 추가:

```yaml
  쿨타임:
    description: 모든 능력의 쿨타임을 무시합니다. (테스트용)
    usage: /쿨타임 [on|off]
```

- [ ] **Step 2: `Main.onEnable`에 executor 등록**

`Main.java`에서 다음을 찾아:

```java
        if (this.getCommand("관전") != null) {
            this.getCommand("관전").setExecutor(this);
            this.getCommand("관전").setTabCompleter(this);
        }
```

바로 아래에 삽입:

```java
        if (this.getCommand("쿨타임") != null) {
            this.getCommand("쿨타임").setExecutor(this);
            this.getCommand("쿨타임").setTabCompleter(this);
        }
```

- [ ] **Step 3: `isAdminCommand`에 추가**

`Main.java`의 `isAdminCommand`에서 `case "팀설정":` 바로 아래에 삽입:

```java
            case "쿨타임":
```

- [ ] **Step 4: `onCommand`에 분기 추가**

`Main.java`의 `onCommand`에서 `관전` 처리 블록 바로 아래(`return false;` 직전)에 삽입:

```java
        if (cmd.getName().equalsIgnoreCase("쿨타임")) {
            if (args.length < 1) {
                player.sendMessage(ChatColor.YELLOW + "현재 쿨타임 무시: "
                        + (Cooldown.isDisabled() ? "켜짐 (테스트 모드)" : "꺼짐"));
                player.sendMessage(ChatColor.GRAY + "사용법: /쿨타임 [on|off]");
                return true;
            }

            boolean on = args[0].equalsIgnoreCase("on");
            Cooldown.setDisabled(on);

            // 테스트 모드가 켜진 줄 모르고 진짜 게임을 하면 곤란하므로 전원에게 알립니다.
            if (on) {
                Bukkit.broadcastMessage(ChatColor.LIGHT_PURPLE + "[능력자] 쿨타임 무시가 켜졌습니다. "
                        + ChatColor.GRAY + "(테스트 모드 - 모든 능력을 즉시 재사용할 수 있습니다)");
            } else {
                Bukkit.broadcastMessage(ChatColor.LIGHT_PURPLE + "[능력자] 쿨타임 무시가 꺼졌습니다. "
                        + ChatColor.GRAY + "(정상 모드)");
            }
            return true;
        }

```

import 추가:

```java
import org.example.abilities.Cooldown;
```

- [ ] **Step 5: 탭 완성 추가**

`Main.java`의 `onTabComplete`에서 `관전` 처리 블록 바로 아래에 삽입:

```java
        if (command.getName().equalsIgnoreCase("쿨타임") && args.length == 1) {
            for (String opt : new String[]{"on", "off"}) {
                if (opt.startsWith(args[0].toLowerCase())) completions.add(opt);
            }
        }
```

- [ ] **Step 6: 빌드 확인**

Run: `./gradlew build --console=plain`
Expected: BUILD SUCCESSFUL, 33 tests

- [ ] **Step 7: 커밋**

```bash
git add src/main/resources/plugin.yml src/main/java/org/example/Main.java
git commit -m "feat: /쿨타임 [on|off] 추가 (OP 전용)

능력 테스트 시 매번 20초를 기다리지 않도록 전역 쿨타임 무시를 제공합니다.
켤 때 전원에게 알립니다 - 테스트 모드인 줄 모르고 진짜 게임을 하면 곤란합니다."
```

---

[← Task 5](task-05-ability-hooks.md) · [진척 현황](README.md) · [Task 7 →](task-07-advancement-suppressor.md)
