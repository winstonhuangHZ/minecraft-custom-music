# Custom Music

一个 Fabric 客户端模组：把 mp3 丢进文件夹，模组自动转码成 Minecraft 能播的 OGG，
并接管游戏背景音乐。有两种模式——**音乐播放器**（按歌单队列连着放）和
**情境配乐**（给不同生物群系/维度指定各自的曲子）。右上角还有一个显示专辑封面 /
歌名 / 播放进度的小标签。纯客户端，服务器不用装任何东西。

## 特性

- 支持 mp3 / m4a / aac / flac / wav / ogg 等，自动转成 OGG Vorbis
- 游戏内图形界面：试听、启用/禁用、上下调整顺序、一键重扫
- **歌单播放**：按子文件夹自动分组成功专辑，可以「立即播放」或「加入队列」，
  放完一张自动换下一张（限制首数 / 分钟数都行），支持顺序或随机
- **情境配乐**：给任意情境指定曲子，比如下界放 Dazed and Confused 和 Kashmir、
  主菜单放别的。26.2 一共有 **32 个配乐情境**（各生物群系、下界、末地、水下、菜单…），
  模组直接扫描所有资源包的 sounds.json 自动枚举，不用手写列表
- **右上角小标签**：专辑封面 + 歌名 + 艺术家 + 播放进度条，可以在界面里一键开关；
  没有内嵌封面就不显示封面
- **服务器下发的资源包也盖不住你** —— 见下面的技术说明
- 支持 ModMenu（装了才有设置按钮），也自带快捷键
- 增量转码：已转过的文件按修改时间跳过，加歌后重扫只处理新歌

## 环境要求

| 依赖 | 版本 |
|---|---|
| Minecraft | 26.2（这一条必须对上） |
| Fabric Loader | 0.16.0+（实测 0.19.3） |
| Java | 25（Minecraft 26.2 本身就要求 25） |
| ffmpeg | **可选**，装了能支持全部格式；不装也能放 mp3 |
| ModMenu | 可选，**推荐**（装了才能在模组列表里直接点设置） |

**不需要 Fabric API。** 生命周期事件、快捷键注册、HUD 绘制这三处原本要用 Fabric API，
现在都用 Mixin 直接接原版（`Minecraft.tick`、`Options.load`、`Hud.extractRenderState`），
所以依赖只剩「Minecraft + Fabric Loader」，少一个装错/漏装就启动不了的环节。

唯一不能放开的是 **Minecraft 版本**：Mixin 是按方法签名注入的（`MusicManager.startPlaying`、
`PackRepository.rebuildSelected`、`Hud.extractRenderState` 等），换版本必须重新适配。
写成 `~26.2` 是为了版本对不上时干净地拒绝加载，而不是进游戏后崩在某个注入点上。

### 关于 ffmpeg（现在是可选的）

Minecraft 只能播 OGG Vorbis，而纯 Java 没有可用的 Vorbis **编码器**（JOrbis 只能解码）。
所以有两条路，模组会自己选：

| 情况 | 行为 |
|---|---|
| 系统里有 ffmpeg | 自动找到，mp3/flac/m4a/wav… 全部转成 OGG 播放（质量最好，进度条精确） |
| 系统里没有 ffmpeg | **mp3 直接由纯 Java 解码器播放**，完全不经过 OGG，也不需要任何外部工具；其它格式暂不支持 |
| 配置里把 `ffmpegPath` 写成 `none` | 明确禁用 ffmpeg，强制走直读 |

ffmpeg 的查找顺序：配置里的路径 → `PATH` → 常见安装位置（macOS 的 Homebrew/MacPorts、
Windows 的 winget/scoop/chocolatey/Program Files、Linux 的 /usr/bin、/snap/bin 等）。
找不到就会在日志和界面里提示，并自动降级到直读模式。

以后装了 ffmpeg 也不用做别的：占位文件会被识别为「需要重新转码」，下次扫描自动升级成真 OGG。

### ModMenu 支持（可选，推荐）

本模组**不依赖** ModMenu，装不装都能用：

| 情况 | 怎么打开界面 |
|---|---|
| 装了 ModMenu | 「模组」列表里本模组有「设置」按钮，点进去就是主界面 |
| 没装 ModMenu | 游戏里按 `M`（可在「选项 → 控制 → 按键绑定 → 自定义音乐」里改） |

装 ModMenu 的好处是能在模组列表里看到本模组和它的说明，入口更直观。
注意 ModMenu 自己依赖 Fabric API，但**本模组不需要**——也就是说你完全可以只装
Minecraft + Fabric Loader + 本模组，用 `M` 键进界面。

模组启动时会在日志里提示当前属于哪种情况，方便排查。

## 安装

1. 装好 Fabric Loader，把 `custom-music-1.0.2.jar` 丢进 `mods/`（Fabric API 不需要，ModMenu 可选）
2. 启动一次游戏，会生成 `.minecraft/config/custommusic/music/`
3. 把你的歌丢进那个文件夹
4. 游戏里按 `M` 打开界面，点「重新扫描并转码」

## 界面

### 怎么开始播放

| 想做什么 | 怎么做 |
|---|---|
| 后台一直放（把想听的歌连起来） | 主界面点 **「▶ 播放全部」**；它把当前启用的曲目全部排成队列开始放，**关掉界面也会继续** |
| 按专辑放 | 主界面点「歌单播放」→ 点一张专辑 → **「立即播放选中的」**（或那行右侧的 ▶） |
| 排队换着放 | 在歌单界面点「加入队列」，放完一张自动换下一张 |
| 只放某一首 | 曲目列表里点那一行的 **▶**（试听）。它不打断已有队列，队列轮到下一首时会让位 |
| 停下来 | 主界面或歌单界面上的 **「■ 停止播放」** |

界面上也有一行绿色提示写着这些，不用回来翻文档。

### 播放器模式到底覆盖了什么

它其实是两层机制，搞清楚这两层就能明白「不管你在哪」这句话的边界：

1. **运行时接管**：原版每次要开一首新歌都会走 `MusicManager.startPlaying`，
   我们把它换成队列里的下一首。所以**只要队列非空，不管你在哪个维度、哪个生物群系、
   在水下还是在菜单，放的都是你的队列**。不接管的情况只有：正在放唱片机（原版会暂停背景音乐）、
   世界加载界面、以及你在 `overrideExclude` 里排除掉的情境（默认是末影龙和终末之诗）。
2. **资源包覆盖**（兜底）：「全部配乐」开关打开时，32 个 `music.*` 事件全部改写成你的曲目列表。
   这一层在接管失效时起作用：比如 `loopQueue=false` 且队列放完了，我们把控制权交回原版，
   原版从被覆盖的事件里随机挑一首 —— 仍然是你的歌，只是变成随机顺序 + 原版的那个长间隔。
   把开关关掉，未覆盖的情境就恢复原版音乐。

唱片机（`music_disc.*`）不在覆盖范围内，扔一张唱片还是会放那张唱片。

顺带一提：长期「电台」式听感建议 `loopQueue=true` + `trackGapSeconds` 调小（比如 1-2 秒），
这样队列永远不会放完，也就不会掉回原版的随机逻辑。

- `M` 键打开（可在按键设置里改；装了 ModMenu 的话模组列表里也有设置按钮）
- 点整行 = 启用/禁用；行右侧 `▶` 试听、`↑` `↓` 调顺序
- 底部：重新扫描并转码 / 重新加载资源包 / 全部启用 / 全部禁用 / 打开音乐文件夹
- 「歌单播放」按钮进入歌单界面：
  - 列表里每行是一张专辑（文件夹名 + 曲目数），右侧 `▶` 立即播放、`+` 加入队列
  - 顶栏显示「正在播放」和队列顺序（`▶` 标出当前这张）
  - 底部：立即播放选中的 / 加入队列 / 跳过本张 / 播放顺序 / 右上角标签开关 / 清空队列
- 顶部的模式按钮在两种模式间切换：
  - **模式：播放器** —— 按队列连着放，忽略游戏情境；「全部配乐」开关决定是否连原版配乐事件一起盖掉
  - **模式：情境** —— 进入情境界面：列出全部 32 个情境（显示成「下界 · 下界荒地」这种），
    点一个情境进去，再点歌单或单曲就能给它分配/取消，「改为选单曲」可以在歌单和单曲之间切换。
    改完立刻生效，不用重新生成资源包。**没配规则的情境保持原版配乐。**

## 配置

`config/custommusic/config.json`

| 字段 | 说明 |
|---|---|
| `musicFolder` | 音乐文件夹，留空用默认的 `config/custommusic/music` |
| `mode` | `PLAYER`（播放器）或 `SITUATIONAL`（情境） |
| `assignments` | 情境规则：情境事件 id → 选择器列表（歌单 id 或单个曲目文件），见下面的例子 |
| `overrideAllMusic` | 播放器模式下是否覆盖全部配乐事件（默认开） |
| `overrideExclude` | 播放器模式下不动的事件，默认 `music.dragon`、`music.credits` |
| `ffmpegPath` | ffmpeg 路径；默认会自动探测，填 `none` 表示禁用 ffmpeg 改用纯 Java 直读 |
| `vorbisQuality` | 转码质量 0-10，默认 5 |
| `overrideEvents` | 要覆盖的原版音乐事件，默认 game/creative/menu/under_water |
| `autoSyncOnStart` | 启动时自动扫描并转码 |
| `queue` / `queueIndex` | 歌单队列与当前播到第几张 |
| `shuffleTracks` | 歌单内随机播放 |
| `tracksPerPlaylist` | 一张歌单最多播几首，0 = 播完为止 |
| `minutesPerPlaylist` | 一张歌单最多播几分钟，0 = 不限制 |
| `loopQueue` | 队列放完后是否从头循环 |
| `trackGapSeconds` | 两首歌之间的间隔秒数 |
| `showHud` | 是否显示右上角「正在播放」小标签 |

情境模式的配置长这样（给下界两个群系分别点歌）：

```json
{
  "mode": "SITUATIONAL",
  "assignments": {
    "minecraft:music.nether.nether_wastes": [
      "Led Zeppelin"
    ],
    "minecraft:music.nether.basalt_deltas": [
      "Led Zeppelin/Dazed and Confused.mp3",
      "Led Zeppelin/Kashmir.mp3"
    ],
    "minecraft:music.menu": [
      "Menus"
    ]
  }
}
```

选择器写**歌单 id（文件夹相对路径）**就是整张歌单，写**曲目文件路径**就是单曲，可以混着写。

## 技术说明

几个 26.x 上真实会踩的坑，都在这版里处理掉了：

1. **纯 Java 没有可用的 Vorbis 编码器**，所以转码走外部 ffmpeg 进程。
   会先探测 `libvorbis`，精简版 ffmpeg 只有内置 `vorbis` 编码器（需要 `-strict -2`），
   音质差一些但能正常播，模组会自动回退并在日志里提示。
2. **服务器资源包优先级**：原版会把「必需包」（原版包、服务器下发的包）插到资源包列表顶部，
   而列表顺序就是加载优先级，所以默认情况下服务器包会盖掉玩家的包。
   `PackRepositoryMixin` 在 `rebuildSelected` 返回前把本模组的包挪到第一位。
3. **`pack.mcmeta` 格式变了**：26.x 要求 `min_format` / `max_format`（值为 `[主版本, 次版本]`），
   老的 `pack_format` + `supported_formats` 会被判定成 `UNKNOWN`（不兼容）。
4. **`sounds.json` 的 key 不能带命名空间**：命名空间由文件所在目录决定。
   `assets/minecraft/sounds.json` 里只能写 `music.game` 这种路径，
   所以每首歌的事件单独生成在 `assets/custommusic/sounds.json` 里（key 是 `track.xxx`），
   再用 `minecraft:music/xxx` 指回音频文件。
5. **接管背景音乐**：原版每首新歌都会走 `MusicManager.startPlaying(Music)`，
   `MusicManagerMixin` 在这里换成歌单里的曲子；同时
   - 原版 `canReplace()` 只比较事件 id，我们的自定义事件和情境音乐（菜单/群系）永远不相等，
     它会在下一个 tick 就把我们的歌停掉（表现就是「每首只放一秒」），所以在放我们的歌时让它返回 false；
   - 一首放完之后原版要等「情境音乐的最大间隔」才开下一首，`tick` 末尾会把间隔压成配置的秒数，
     这样一整张专辑是连着放的。
6. **播放失败不能算放完**：资源包重载期间 `SoundEngine` 处于未加载状态，
   一切播放请求都返回 `NOT_STARTED`。如果把这种失败当成「这首放完了」，
   整个队列会在一秒内被消耗光，所以失败会把这首放回队首重试（间隔 0.25-1 秒，连续 10 次才跳过）。
   启动时如果资源包已经启用，也不会再多余地重载一次。
7. **封面**：Minecraft 的 `NativeImage` 只认 PNG，而 mp3 里内嵌的封面几乎都是 JPEG，
   所以用 JDK 的 ImageIO 解码、缩到 128px、转成 PNG 再注册成动态贴图。
   没有内嵌封面（或者解析失败）就不显示封面，其余部分照常。
8. **播放进度**：时长用 JOrbis 读转码后 OGG 的总时长，进度按「开始播放到现在的墙钟时间」算，
   不额外起 ffprobe 进程。
9. **情境模式怎么知道"现在是什么情境"**：不用猜。原版每次换音乐都会调用
   `MusicManager.startPlaying(Music)`，而那个 `Music` 里就带着游戏选中的事件
   （比如 `minecraft:music.nether.nether_wastes`），我们拦截时顺手读出来当情境 id，
   再查你配的规则。所以情境模式**不需要覆盖原版事件**，未配置的情境原样保留原版音乐。
10. **只压缩我们自己放的歌的间隔**：原版一首放完要等「情境音乐的最大间隔」才开下一首
    （可能十几分钟），所以 tick 里会把间隔压成配置的秒数 —— 但这个压缩只对我们自己启动的
    曲目生效，否则没配规则的情境里，原版音乐也会被压成两秒一首。

## 开发

```bash
./gradlew build        # 产物在 build/libs/
./gradlew runClient    # 起开发客户端
```

启动加 `-Dcustommusic.debugScreen` 会在进入游戏后直接打开模组界面。

> `gradle.properties` 里的 `org.gradle.java.home` / `org.gradle.java.installations.paths`
> 是本机 JDK 25 的绝对路径，换机器需要改掉或删掉。

## 已验证

- 用 Minecraft 自己的 `JOrbisAudioStream` 解码转码产物：双声道 44.1kHz、时长正确、有实际音频波形
- 游戏内试听返回 `STARTED`
- 资源包实际加载顺序为 `file/CustomMusicPack, vanilla, ...`（本包在最前）
- 歌单队列实测：每首 4 秒（3 秒曲长 + 1 秒间隔）稳定交替循环，跨专辑自动换张、队列放完自动交回原版
- 内嵌封面实测：带 320x320 JPEG 封面的 mp3 → 解析出 3429 字节 → 转成 128x128 PNG 贴图注册成功
- 配乐事件枚举实测：游戏内日志 `枚举到 32 个配乐事件（minecraft=32）`，
  排除末影龙/终末之诗后 `本次覆盖 30 个配乐事件`；
  离线另跑一遍中文名映射，`minecraft:music.nether.nether_wastes` → 「下界 · 下界荒地」
- 情境模式实测：把主菜单配给一张专辑后，日志出现 `情境模式：不覆盖原版配乐事件，按规则替换`，
  资源包里 `assets/minecraft/sounds.json` 被正确删除（未配置的情境恢复原版），
  接管播放的是分配给主菜单那两首，4 秒一首交替，情境界面打开并持续运行无异常
- 切模式实测：切回播放器模式后 `assets/minecraft/sounds.json` 重新生成，播放节奏不变

唯一没能亲手验证的是**界面上点击分配**（我这边没法给游戏发鼠标事件），
分配逻辑本身是通过配置文件设好后实机验证的；界面渲染无异常。

## License

MIT
