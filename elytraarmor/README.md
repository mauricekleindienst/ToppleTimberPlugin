# ElytraArmor

Combine any **Elytra** with any **chestplate** — fly with Elytra while keeping the chestplate's full armour rating, toughness, and all enchantments from both items.

## How to use

### Option A — Anvil (recommended)
Place an Elytra and any chestplate in an Anvil in either order.  
The output slot will show the **✦ [Name] + Elytra** combined item.  
Pick it up (costs XP levels configured in `config.yml`).

### Option B — Command
1. Hold the **Elytra in your main hand** and the **chestplate in your offhand** (or vice versa).
2. Run `/elytraarmor combine` (or `/ea combine`).

### Splitting back
Wear the combined item in your chest slot and run `/elytraarmor split`.  
You'll receive the original Elytra and chestplate back.

## What the combined item does

| Feature | Detail |
|---|---|
| **Flight** | Works exactly like a normal Elytra — equip in chest slot, jump off a height, press Space to glide |
| **Armour** | Full protection of the original chestplate (Leather → Netherite) |
| **Toughness** | Diamond / Netherite toughness included |
| **Enchantments** | All non-conflicting enchantments from **both** items are merged; duplicates keep the higher level |
| **Curse of Binding** | Excluded by default (configurable) |
| **Thorns** | Reflects damage back to attackers |
| **Fire/Blast/Projectile Protection** | Applied correctly per damage type |
| **Unbreaking / Mending** | Work as normal (item is still an Elytra under the hood) |
| **Durability** | Averaged from both input items (configurable) |

## Commands

| Command | Description |
|---|---|
| `/ea combine` | Combine held Elytra + chestplate |
| `/ea split` | Split combined item back into two pieces |
| `/ea help` | Show help |

## Permissions

| Permission | Default | Description |
|---|---|---|
| `elytraarmor.use` | Everyone | Use combine/split commands |
| `elytraarmor.admin` | OP | Admin features |

## Building

Requires **Java 17+** and **Maven**.

```bash
cd elytraarmor
mvn clean package
```

Drop `ElytraArmor-1.0.0.jar` from `target/` into your server's `plugins/` folder.

## Compatibility

- **Paper 1.21.x** (recommended) — uses PersistentDataContainer, no NMS
- Spigot 1.20.4+ may also work but is untested
