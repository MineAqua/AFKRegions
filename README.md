<img width="800" height="300" alt="afkregions" src="https://github.com/user-attachments/assets/5349de94-0f04-465b-aa51-2125d9864100" />

# AFKRegions
**AFKRegions** is a Spigot/Paper plugin that detects when a player is inside a specific region and, after a configurable amount of time, automatically executes rewards (commands). Perfect for AFK reward systems, mini-games, events, or waiting areas.

## 📌 Features

- Define regions with commands.
- Automatic detection when a player enters or leaves a region.
- Execute commands as rewards after a set time in the region.
- Multiple rewards per region with configurable times and chances.
- **PlaceholderAPI** support to customize commands and messages.
- Progress display with elapsed time, max time, and percentage.
- Configurable messages for entering, leaving, and rewards.

## 📜 Commands

| Command | Description |
|---------|-------------|
| `/afkregions wand` | Selects a region (left click = pos1, right click = pos2) |
| `/afkregions create <name> <duration_s>` | Creates a region from your current selection |
| `/afkregions reward list <region>` | Lists all rewards in a region |
| `/afkregions reward add <region> <chance%> <time_s> <command...>` | Adds a reward to a region |
| `/afkregions reward remove <region> <index>` | Removes a reward from a region |
| `/afkregions remove <region>` | Deletes a region |
| `/afkregions reload` | Reloads the plugin configuration |

---

## 🔑 Permissions

| Permission | Description |
|------------|-------------|
| `afkregions.admin` | Grants access to all admin commands |

---

## 🪄 PlaceholderAPI Placeholders
These placeholders can be used via **PlaceholderAPI**.  
Replace `<identifier>` with your plugin's placeholder identifier (e.g., `%afkregions_is_afk%`).

| Placeholder | Description |
|-------------|-------------|
| `%<identifier>_is_afk%` | Returns `true` if the player is currently AFK inside a defined region, otherwise `false`. |
| `%<identifier>_region_name%` | The name of the AFK region the player is currently in. |
| `%<identifier>_time%` | The elapsed time in seconds that the player has been in the current region. |
| `%<identifier>_duration%` | The total required time in seconds to complete the region's cycle. |
| `%<identifier>_progress%` | The player's progress in the region as a percentage (`0`–`100`). |
| `%<identifier>_progress_bar%` | A visual progress bar made of text characters. |
| `%<identifier>_time_left%` | The remaining time in seconds before the region cycle completes. |

---
