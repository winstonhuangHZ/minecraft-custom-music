# Custom Music

一个 Fabric 客户端模组：把 mp3 丢进文件夹，模组自动转码成 Minecraft 能播的 OGG，
并接管游戏背景音乐。支持按专辑分组的歌单队列，右上角还有一个显示专辑封面 / 歌名 /
播放进度的小标签。纯客户端，服务器不用装任何东西。

## 特性

- 支持 mp3 / m4a / aac / flac / wav / ogg 等，自动转成 OGG Vorbis
- 游戏内图形界面：试听、启用/禁用、上下调整顺序、一键重扫
- **歌单播放**：按子文件夹自动分组成功专辑，可以「立即播放」或「加入队列」，
  放完一张自动换下一张（限制首数 / 分钟数都行），支持顺序或随机
- **右上角小标签**：专辑封面 + 歌名 + 艺术家 + 播放进度条，可以在界面里一键开关；
  没有内嵌封面就不显示封面
- **服务器下发的资源包也盖不住你** —— 见下面的技术说明
- 支持 ModMenu（装了才有设置按钮），也自带快捷键
- 增量转码：已转过的文件按修改时间跳过，加歌后重扫只处理新歌

## 环境要求

| 依赖 | 版本 |
|---|---|
| Minecraft | 26.2 |
| Fabric Loader | 0.19.3+ |
| Fabric API | 0.154.2+26.2 |
| ffmpeg | 任意版本，建议完整版（带 libvorbis） |
| ModMenu | 可选 |

## 安装

1. 装好 Fabric Loader 和 Fabric API，把 `custom-music-1.0.0.jar` 丢进 `mods/`
2. 启动一次游戏，会生成 `.minecraft/config/custommusic/music/`
3. 把你的歌丢进那个文件夹
4. 游戏里按 `M` 打开界面，点「重新扫描并转码」

## 界面

- `M` 键打开（可在按键设置里改；装了 ModMenu 的话模组列表里也有设置按钮）
- 点整行 = 启用/禁用；行右侧 `▶` 试听、`↑` `↓` 调顺序
- 底部：重新扫描并转码 / 重新加载资源包 / 全部启用 / 全部禁用 / 打开音乐文件夹
- 「歌单播放」按钮进入歌单界面：
  - 列表里每行是一张专辑（文件夹名 + 曲目数），右侧 `▶` 立即播放、`+` 加入队列
  - 顶栏显示「正在播放」和队列顺序（`▶` 标出当前这张）
  - 底部：立即播放选中的 / 加入队列 / 跳过本张 / 播放顺序 / 右上角标签开关 / 清空队列

## 配置

`config/custommusic/config.json`

| 字段 | 说明 |
|---|---|
| `musicFolder` | 音乐文件夹，留空用默认的 `config/custommusic/music` |
| `ffmpegPath` | ffmpeg 路径，可以填绝对路径 |
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

## License

MIT
