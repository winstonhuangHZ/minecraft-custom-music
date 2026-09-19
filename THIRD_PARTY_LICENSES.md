# 第三方组件

## JLayer (javazoom:jlayer:1.0.1)

- 用途：没有 ffmpeg 时，用纯 Java 直接解码 MP3（`com.custommusic.audio.Mp3AudioStream`）。
- 许可：**LGPL-2.1**。
- 该库的类被直接打进本模组的 jar（`javazoom/jl/**`）。如果你需要替换它，
  可以从 [JLayer 源码](http://www.javazoom.net/javalayer/javalayer.html) 自行构建同版本 jar 并替换。

除此之外本模组没有其它第三方依赖（Fabric API 也不需要）。
