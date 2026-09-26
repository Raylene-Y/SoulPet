# PocketPet

> 开源、可编程、住在手机悬浮窗里的 AI 宠物 Agent。

## 定位

不是桌宠 app，是**桌宠养成框架**：
- 宠物有形象（悬浮窗像素宠物）、有人格（LLM + 记忆）、真能干活（原生 API + Termux 桥）
- 行为脚本化，调教即沉淀，宠物包可分享
- 宠物包格式兼容 [CoPet](https://github.com/ChanceYu/CoPet)（`pet.json` + `spritesheet.webp`）

## 架构（双层能力）

```
L1 内置层：悬浮窗渲染 + LLM 对话 + 原生 API（电量/通知/定时/剪贴板/TTS）
L2 Termux 桥（可选）：com.termux.permission.RUN_COMMAND → 任意 shell 能力
```

## 构建

需要 JDK 17 + Android SDK（platform 34, build-tools）。

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

## 路线图

- [x] M0 悬浮窗像素史莱姆（程序化帧动画 + 拖拽 + 贴边）
- [ ] M1 状态机完善（闲逛/睡觉/贴边爬）
- [ ] M2 LLM 对话（点宠物 → 聊天）
- [ ] M3 工具调用（电量/通知/剪贴板，function calling）
- [ ] M4 行为脚本化 + 调教对话生成脚本
- [ ] M5 Termux 桥
- [ ] M6 CoPet 宠物包导入
