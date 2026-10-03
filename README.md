# SoulPet

> Not a tool. A soul on your screen.
> 不是工具，是住在你屏幕上的小生命。

[English](#english) · [中文](#中文)

---

## 中文

SoulPet 是一只住在你手机悬浮窗里的 AI 宠物。它有形象、有人格、有记忆，还会**真的帮你干活**。

### 它和别的不一样

- **🐾 有形象**：像素宠物悬浮在所有 app 上，会游荡、发呆、睡觉，你聊天时它会安静陪你
- **🧠 有记忆**：三层记忆（对话历史 / 它主动记住的关于你的事 / 人格文件），杀进程不失忆，数据全在你手机里
- **🎭 有人格**：内置多种人格，聊天中就能让它改自己的性格——人格是磁盘上的 `.md` 文件，调教即沉淀
- **💪 能干活**：查电量、读剪贴板只是开始；装了 Termux 后它拥有整条 Linux 手臂（拍照、定位、发短信、跑脚本），命令输出自动回传
- **❤️ 会主动找你**：电量低、早安、被冷落太久、你安排的定时任务——它都会主动发通知
- **🔊 有声音**：原生 TTS 朗读回复 + 程序生成的 8-bit 音效
- **🎨 可换皮**：兼容 [CoPet](https://github.com/ChanceYu/CoPet) 宠物包格式（`pet.json` + `spritesheet.webp`），社区皮肤直接导入

### 核心理念

SoulPet 的护城河不是功能，是**沉淀在你手里的东西**：人格文件、记忆、调教脚本。这些东西长在本地，换手机可以带走，厂商关停服务它也不死。

### 快速开始

1. 下载 [Releases](../../releases) 里的 APK 安装
2. 打开 SoulPet → 授予悬浮窗权限 → 自动召唤
3. 高级设置 → 模型设置 → 填你的 OpenAI 兼容接口（Base URL + API Key + 模型名）
   - 推荐 [智谱 GLM](https://open.bigmodel.cn/)（国内直连、有免费额度）或 DeepSeek
4. 点宠物开聊

**解锁完全体（可选）**：
1. 安装 [Termux](https://f-droid.org/packages/com.termux/) 和 [Termux:API](https://f-droid.org/packages/com.termux.api/)
2. Termux 里跑：`mkdir -p ~/.termux && echo "allow-external-apps=true" >> ~/.termux/termux.properties`，重启 Termux
3. 高级设置 → 命令回传授权 + 模型设置页按需配置
4. 试试对它说："让手机震动一下" / "我现在在哪" / "拍张照"

### 构建

需要 JDK 17 + Android SDK (platform 34)：

```bash
./gradlew assembleDebug
```

### 技术栈

纯 Kotlin，零三方依赖。悬浮窗 WindowManager + 程序化 Canvas 渲染 + OpenAI 兼容流式接口 + function calling + Termux RUN_COMMAND 桥。

---

## English

SoulPet is an AI pet living in your Android floating window. It has a body, a personality, memories — and it **actually does things for you**.

### Why it's different

- **🐾 Embodied**: a pixel pet floating above all apps — wanders, dozes, sits quietly while you chat
- **🧠 Memory**: 3-layer memory (chat history / facts it chooses to remember / persona files), all local, survives restarts
- **🎭 Trainable persona**: reshape its personality mid-chat; personas are plain `.md` files on disk
- **💪 Real hands**: battery, clipboard, and with Termux installed — the whole Linux toolchain (camera, GPS, SMS, scripts) with command output fed back
- **❤️ Proactive**: low battery, good morning, missed-you, scheduled tasks — it reaches out first
- **🔊 Voice & sound**: native TTS replies + procedurally generated 8-bit sound effects
- **🎨 Skinnable**: compatible with [CoPet](https://github.com/ChanceYu/CoPet) pet packages

### Quick start

1. Install the APK from [Releases](../../releases)
2. Grant overlay permission → pet appears
3. Advanced settings → Model settings → fill in your OpenAI-compatible endpoint
4. Tap the pet to chat

### Build

JDK 17 + Android SDK (platform 34), then `./gradlew assembleDebug`.

### License

MIT
