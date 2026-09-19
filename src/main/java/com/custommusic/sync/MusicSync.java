package com.custommusic.sync;

import com.custommusic.CustomMusicClient;
import com.custommusic.config.CustomMusicConfig;
import com.custommusic.music.MusicLibrary;
import com.custommusic.pack.MusicEvents;
import com.custommusic.pack.PackGenerator;
import com.custommusic.pack.PackManager;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.Map;
import java.util.SortedSet;

/** 后台线程扫描 + 转码 + 生成资源包，完成后回主线程重载。 */
public final class MusicSync {

    public enum State {
        IDLE, RUNNING, DONE, ERROR
    }

    private static volatile State state = State.IDLE;
    private static volatile String message = "";
    private static volatile int done;
    private static volatile int total;
    private static volatile List<String> lastErrors = List.of();

    private MusicSync() {
    }

    public static State state() {
        return state;
    }

    public static String message() {
        return message;
    }

    public static int done() {
        return done;
    }

    public static int total() {
        return total;
    }

    public static List<String> lastErrors() {
        return lastErrors;
    }

    public static boolean isRunning() {
        return state == State.RUNNING;
    }

    /** 启动一次同步，已经在跑就忽略。 */
    public static void start() {
        if (state == State.RUNNING) {
            return;
        }
        state = State.RUNNING;
        message = "正在准备…";
        done = 0;
        total = 0;
        lastErrors = List.of();

        // 枚举配乐事件要读资源管理器，放在主线程做，别丢到工作线程里
        Map<String, SortedSet<String>> events = resolveEvents();

        Thread thread = new Thread(() -> work(events), "CustomMusic-Sync");
        thread.setDaemon(true);
        thread.start();
    }

    /** 决定这次要覆盖哪些配乐事件：全量枚举 or 配置里手写的那几个。 */
    private static Map<String, SortedSet<String>> resolveEvents() {
        CustomMusicConfig config = CustomMusicClient.config();
        if (config.isSituational()) {
            // 情境模式靠拦截游戏的选择来替换，不需要动原版事件，
            // 未配置的情境就保持原版配乐
            CustomMusicClient.LOG.info("情境模式：不覆盖原版配乐事件，按规则替换");
            return new java.util.TreeMap<>();
        }
        Map<String, SortedSet<String>> events = config.overrideAllMusic
                ? MusicEvents.discover()
                : MusicEvents.fromConfig(config.overrideEvents);
        if (events.isEmpty()) {
            CustomMusicClient.LOG.warn("没枚举到配乐事件，退回配置里的 overrideEvents");
            events = MusicEvents.fromConfig(config.overrideEvents);
        }
        MusicEvents.applyExclusions(events, config.overrideExclude);

        int count = events.values().stream().mapToInt(SortedSet::size).sum();
        CustomMusicClient.LOG.info("本次覆盖 {} 个配乐事件（覆盖全部配乐={}）", count, config.overrideAllMusic);
        return events;
    }

    private static void work(Map<String, SortedSet<String>> events) {
        try {
            MusicLibrary library = CustomMusicClient.library();
            library.scan();

            PackGenerator.Result result = PackGenerator.generate(
                    library, CustomMusicClient.config(), events,
                    (done, total, current) -> {
                        MusicSync.done = done;
                        MusicSync.total = total;
                        MusicSync.message = total == 0
                                ? ""
                                : "转码 " + done + "/" + total + "  " + current;
                    });

            lastErrors = result.errors();
            CustomMusicClient.config().lastGeneratedAt = System.currentTimeMillis();
            CustomMusicClient.config().save();

            if (result.ok()) {
                state = State.DONE;
                message = "共 " + (result.converted() + result.cached()) + " 首"
                        + (result.converted() > 0 ? "，新转码 " + result.converted() : "")
                        + (result.fallback() > 0 ? "，其中 " + result.fallback() + " 首走 MP3 直读" : "");
            } else {
                state = State.ERROR;
                message = result.failed() + " 首失败，详见日志";
                for (String error : result.errors()) {
                    CustomMusicClient.LOG.warn("同步问题: {}", error);
                }
            }

            Minecraft.getInstance().execute(PackManager::enableAndReload);
        } catch (Throwable t) {
            state = State.ERROR;
            message = "同步失败: " + t.getMessage();
            CustomMusicClient.LOG.error("同步失败", t);
        }
    }
}
