# 提案：用数据文件自定义文字键盘布局

关联 issue：#377（由输入法引擎控制键盘布局），以及按 duplicate 关到这里的 #727、#736、#271。

## 背景

#377 里列的需求，比如微软双拼的「；」、常驻数字行、Dvorak/Colemak/AZERTY、西里尔字母布局，大多只是**换一下文字键盘上的键**，不需要新的交互方式。

现在 `TextKeyboard.Layout` 已经是一份纯数据（`List<List<KeyDef>>`），`BaseKeyboard` 完全根据它来建视图。缺的只是让这份数据可以从文件读进来，并且按输入法选用。

## 目标与非目标

目标：

- 用户或插件提供一个 json 文件，就能替换文字键盘的布局。
- 可以按输入法提供不同的布局。
- 不新增视图结构，不新增按键行为，不新增设置项。

不在本提案范围内（之后可以另行讨论）：

- 自定义按键动作、图标、弹出菜单。
- 九宫格、日文 12 键等需要新交互的键盘。
- 数字键盘和符号选择器。
- 应用内的布局编辑界面。

## 设计

### 文件位置

沿用 quickphrase 的目录约定，用户文件优先：

| 来源 | 路径 |
|---|---|
| 用户 | `<外部存储>/data/keyboard-layout/<name>.json` |
| 插件 / 系统 | `<dataDir>/usr/share/fcitx5/keyboard-layout/<name>.json` |

- 插件通过现有的 `DataDescriptor` 机制附带布局文件，不需要新的插件 API。
- 用户目录在外部存储下，所以会自动包含在现有的备份导出里，也可以通过文档提供器直接编辑。

### 选用规则

1. `<当前输入法 uniqueName>.json`，比如 `shuangpin.json`、`rime.json`、`keyboard-us.json`
2. `default.json`
3. 内置的 `TextKeyboard.Layout`

以下时机会重新检查：每次开始输入（`onStartInput`），以及切换输入法时（`onImeUpdate`）。文件的修改时间没变就直接用缓存，不会重新解析。

### 文件格式

```json
{
  "version": 1,
  "rows": [
    [
      { "type": "alphabet", "main": "Q", "alt": "1" },
      { "type": "alphabet", "main": "W", "alt": "2" }
    ],
    [
      { "type": "caps" },
      { "type": "alphabet", "main": "Z", "alt": "'" },
      { "type": "backspace" }
    ],
    [
      { "type": "layout-switch", "label": "?123" },
      { "type": "comma" },
      { "type": "language" },
      { "type": "space" },
      { "type": "symbol", "text": ".", "variant": "alternative" },
      { "type": "return" }
    ]
  ]
}
```

每种 `type` 都只对应一个现有的 `KeyDefPreset`。所以布局文件只能重新排列已有的键，不会引入新的行为。

| type | 对应 preset | 字段（括号内为默认值） |
|---|---|---|
| `alphabet` | `AlphabetKey` | `main`、`alt`、`width`(0.1)、`variant`(normal) |
| `symbol` | `SymbolKey` | `text`、`width`(0.1)、`variant`(normal) |
| `layout-switch` | `LayoutSwitchKey` | `label`、`to`("")、`width`(0.15) |
| `comma` | `CommaKey` | `width`(0.1)、`variant`(alternative) |
| `caps` | `CapsKey` | `width`(0.15) |
| `backspace` | `BackspaceKey` | `width`(0.15) |
| `quickphrase` | `QuickPhraseKey` | `width`(0.1) |
| `language` | `LanguageKey` | `width`(0.1) |
| `space` | `SpaceKey` | 无，宽度固定为填满剩余空间 |
| `return` | `ReturnKey` | `width`(0.15) |

`variant` 的取值：`normal`、`alt-foreground`、`alternative`、`accent`。

### 校验

文件不合法时会记日志并回退到下一级布局，不会崩溃。校验规则：

- `version` 必须是 1；不允许出现未知的字段或 `type`，方便及早发现拼写错误。
- 至少有一行，并且每行至少有一个键。
- `main`、`alt`、`text` 必须恰好是一个字符（一个 code point）。原因是按键最终以单个 keysym 发给 fcitx，多个字符发不出去，空字符串还会在 `FcitxKeyAction` 里崩溃。
- `width` 必须在 (0, 1] 范围内，每行宽度之和不能超过 1。
- caps、backspace、quickphrase、language、space、return 在整个布局里最多各出现一次，因为 `TextKeyboard` 是按 view id 查找它们的。

### 对现有代码的改动

- `TextKeyboard`：通过构造参数接收布局。caps、space 等键的 view 改成可空，因为布局里可以没有这些键。
- `KeyDefPreset`：`AlphabetKey`、`CapsKey`、`QuickPhraseKey`、`LanguageKey` 增加 `percentWidth` 参数，默认值和现在一样。这样像西里尔字母那样一行 11 个键的布局也能排下。
- `KeyboardWindow`：布局变化时重建 `TextKeyboard`，并重新应用当前的标点映射。
- 新增 `data/keyboard/KeyboardLayoutFile.kt`（格式和校验，纯 Kotlin，带单元测试）和 `KeyboardLayoutManager.kt`（查找文件、缓存、转换成 KeyDef）。

没有文件时的行为和现在完全一样。

## PR 拆分

| 顺序 | 分支 | 内容 |
|---|---|---|
| 0 | `pr0-keyboard-layout-proposal` | 本文档，先在 #377 讨论，不提交成 PR |
| 1 | `pr1-fix-textkeyboard-label-update` | 修 `updateAlphabetKeys` 中 `forEach` 里的 `return` 提前退出整个函数；`updatePunctuationKeys` 遇到空字符串会崩溃。当前布局下都不会触发 |
| 2 | `pr2-keyboard-layout-file` | 布局文件格式、加载、`default.json`，以及上面提到的 preset 和 view 改动。基于 pr1 |
| 3 | `pr3-per-im-keyboard-layout` | 按输入法 `uniqueName` 选用布局。基于 pr2 |

每个分支都基于上一个分支，按顺序合并。

## 开放问题

1. **按什么选布局。** 目前用的是输入法的 `uniqueName`，纯 Kotlin 实现，不需要改 native。另一种做法是用 fcitx5 输入法分组里每个输入法已有的 `Layout` 字段（`InputMethodGroupItemConfig.layout`，以及分组的 `Default Layout`）。这样更贴近 #377「由输入法引擎控制」的意思，但需要通过 JNI 把这个字段传给 Kotlin，Android 上也没有编辑它的界面。维护者更倾向哪种？
2. **要不要再加一级 `languageCode` 回退**，比如让 `zh_CN.json` 对所有中文输入法生效。
3. **目录名。** `keyboard-layout` 只是暂定，如果有更合适的名字可以改。
4. **主线程 IO。** 每次 `onStartInput` 都会对一到两个文件调用 `stat`；只有修改时间变了才会读文件和解析。如果连这个也不能接受，可以改成只在输入法切换或输入视图重建时检查。
5. **已有的一个小问题。** `onPunctuationUpdate` 只会发给当前显示的键盘。如果标点映射变化时显示的是数字键盘，切回文字键盘后标点不会更新。这个问题和本提案无关，可以另开 PR 修。

## 验证情况

- `KeyboardLayoutFile` 和 `KeyboardLayoutFileTest` 已经在独立的 JVM 工程里用仓库指定的 Kotlin 2.4.10、kotlinx-serialization 1.11.0 编译，6 个测试全部通过。其中包括「用 json 完整表达内置布局」和「一行 11 个键的西里尔布局」。
- 依赖 Android 的部分（`KeyboardLayoutManager`、`KeyboardWindow`、`TextKeyboard`、`KeyDefPreset`）**还没有编译和真机测试**，提交前需要在本地跑 `./gradlew :app:assembleDebug :app:testDebugUnitTest`，再实际试一遍。
