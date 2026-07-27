# 📱 iCraft — A Smartphone Inside Minecraft

iCraft adds a fully functional smartphone to your Minecraft world. Chat with players, take photos, check the weather, make calls, and much more — all from your in-game phone.

> ⚠️ This repository contains the **source code** of the mod. If you're looking to download it to play, check the links below.

[![CurseForge](https://img.shields.io/badge/CurseForge-Download-F16436?style=for-the-badge&logo=curseforge&logoColor=white)](https://www.curseforge.com/minecraft/mc-mods/icraft-a-smartphone)
[![Modrinth](https://img.shields.io/badge/Modrinth-Download-1BD96A?style=for-the-badge&logo=modrinth&logoColor=white)](https://modrinth.com/mod/icraft)

---

## ✨ Features

- 💬 **Chat** — Send and receive messages with other players on the server in real time.
- 📞 **Calls** — Make voice calls to other players directly from Contacts *(requires Simple Voice Chat)*.
- 📷 **Camera** — Take photos of your world with filters and save them to your gallery.
- 🖼️ **Gallery** — Browse your saved photos, share them in chat, send them to the printer, or delete them.
- 🖨️ **Printer** — Print your photos using the printer block. Requires **printed paper** as a material. The printed photo can then be hung on the wall as a painting.
- 🌤️ **Weather** — Check current and upcoming weather conditions.
- 🕐 **Clock** — See the Overworld time, day count, and your real local time.
- 🗺️ **Map** — View your world map with your current position *(requires Xaero's Minimap)*.
- 📝 **Notes** — Write and save personal notes that persist between sessions.
- 👥 **Contacts** — See which players are on the server and their connection status, and message or call them directly.
- 🔔 **Sounds** — Configure notification, call, and click sounds from a single Sounds tab, with instant preview. Includes Do Not Disturb.
- 🎨 **Themes & Cases** — Customize your phone's color, browse wallpapers from a thumbnail gallery, and change your phone's physical case from your inventory.
- 🔒 **Lock Screen** — A lock screen greets you every time you open the phone.
- ⚙️ **Settings** — Customize your phone: theme, wallpaper, sounds, icon editor, and more.

---

## 🧩 Compatibility

| | |
|---|---|
| **Minecraft** | 1.21.1 (Java Edition) |
| **Loaders** | Fabric, NeoForge |
| **Supported environments** | Client and server |
| **License** | MIT |

---

## 🔧 Dependencies

| Mod | Required | Notes |
|---|---|---|
| [Fabric API](https://modrinth.com/mod/fabric-api) | ✅ Yes | Required |
| [Architectury API](https://modrinth.com/mod/architectury-api) | ✅ Yes | Core library after the Architectury migration |
| [SkinRestorer](https://modrinth.com/mod/skinrestorer) | ✅ Yes | Client & server mod |
| [Xaero's World Map](https://modrinth.com/mod/xaeros-world-map) | ⚠️ Optional | Required for the Map app |
| [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat) | ⚠️ Optional | Required for the Call feature |

---

## 🚀 How to get the phone

Currently in **Beta**, the phone is obtained via command: `/give @s icraft:smartphone`

---

## 🛠️ Server Commands

| Command | Description |
|---|---|
| `/icraft chat <on\|off>` | Enable or disable vanilla Minecraft chat alongside iCraft's chat. |
| `/icraft images <on\|off>` | Enable or disable sharing photos to the global chat. |
| `/icraft adminmessage <text>` | Send a system message to the global chat (supports color codes with `&`). |
| `/icraft sendphoto <file>` | Send an admin photo to the global chat. |
| `/icraft clearchats` | Clear the chat on all connected clients. |

To use `/icraft sendphoto`, first drop your `.png` image into the **`iCraft/admin_photos/`** folder inside your world save. Once it's there, the command will auto-suggest the filename and broadcast it to the global chat. Max image size: 512 KB.

---

## 🏗️ Project Structure

This is a **multi-loader** project (Fabric + NeoForge) with shared code:

- `common/` → Code shared across all loaders
- `fabric/` → Fabric-specific implementation
- `neoforge/` → NeoForge-specific implementation

---

## ⚙️ Building

**Requirements:** JDK 21, Git

```bash
git clone https://github.com/LosTomobri/icraft-sourcecode.git
cd icraft-sourcecode
./gradlew build
```

Compiled `.jar` files will be located in the `build/libs/` folders of each subproject (`fabric/`, `neoforge/`).

---

## 📄 License

This project is licensed under MIT — see [LICENSE.txt](LICENSE.txt) for details.

---

*iCraft is designed to be compatible with economy and payment mods — let players create their own in-game transactions.*
