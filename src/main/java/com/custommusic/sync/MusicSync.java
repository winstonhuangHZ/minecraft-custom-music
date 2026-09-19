package com.custommusic.sync;

import com.custommusic.CustomMusicClient;
import com.custommusic.music.MusicLibrary;
import com.custommusic.pack.PackGenerator;
import com.custommusic.pack.PackManager;
import net.minecraft.client.Minecraft;

import java.util.List;

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

        Thread thread = new Thread(MusicSync::work, "CustomMusic-Sync");
        thread.setDaemon(true);
        thread.start();
    }

    private static void work() {
        try {
            MusicLibrary library = CustomMusicClient.library();
            library.scan();

            PackGenerator.Result result = PackGenerator.generate(
                    library, CustomMusicClient.config(),
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
                        + (result.converted() > 0 ? "，新转码 " + result.converted() : "");
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
