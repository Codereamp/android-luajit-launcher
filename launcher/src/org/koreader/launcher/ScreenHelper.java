package org.koreader.launcher;

import android.app.Activity;
import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Window;
import android.view.WindowManager;

import java.util.concurrent.CountDownLatch;

public class ScreenHelper {

    public final static int TIMEOUT_WAKELOCK = -1;
    public final static int TIMEOUT_SYSTEM = 0;

    private String tag;
    private Context context;

    // track fullscreen state.
    private boolean is_fullscreen = true;

    // track system/application screen timeout
    public int app_timeout;
    private int sys_timeout;

    public ScreenHelper(Context context) {
        this.context = context;
        this.tag = context.getResources().getString(R.string.app_name);
        this.sys_timeout = readSettingScreenOffTimeout();
    }

    /* Screen size */
    public int getScreenWidth() {
        return getScreenSize().x;
    }

    public int getScreenHeight() {
        return getScreenSize().y;
    }

    public int getScreenAvailableHeight() {
        return getScreenSizeWithConstraints().y;
    }

    public int getStatusBarHeight() {
        Rect rectangle = new Rect();
        Window window = ((Activity)context).getWindow();
        window.getDecorView().getWindowVisibleDisplayFrame(rectangle);
        int statusBarHeight = rectangle.top;
        return statusBarHeight;
    }

    /* Screen brightness */
    public int getScreenBrightness() {
        final Box<Integer> result = new Box<Integer>();
        final CountDownLatch cd = new CountDownLatch(1);
        ((Activity)context).runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    result.value = new Integer(Settings.System.getInt(
                        context.getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS));
                } catch (Exception e) {
                    Logger.e(tag, "getBrightness error: " + e.toString());
                    result.value = new Integer(0);
                }
                cd.countDown();

            }
        });
        try {
            cd.await();
        } catch (InterruptedException ex) {
            return 0;
        }

        if (result.value == null) {
            return 0;
        }

        return result.value.intValue();
    }

    public void setScreenBrightness(final int brightness) {
        ((Activity)context).runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    //this will set the manual mode (set the automatic mode off)
                    Settings.System.putInt(context.getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);

                    Settings.System.putInt(context.getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS, brightness);
                } catch (Exception e) {
                    Logger.e(tag, "setBrightness error: " + e.toString());
                }
            }
        });
    }

    /* Screen timeout */

    /**
     * set the new timeout state
     *
     * known timeout states are TIMEOUT_SYSTEM, TIMEOUT_WAKELOCK
     * and values greater than 0 (milliseconds of the new timeout).
     *
     * @param new_timeout - new timeout state:
     */

    public void setTimeout(final int new_timeout) {
        // update app_timeout first
        app_timeout = new_timeout;
        // custom timeout in milliseconds
        if (app_timeout > TIMEOUT_SYSTEM) {
            Logger.d(tag, String.format("set timeout for %s: %d seconds", tag, app_timeout / 1000));
            writeSettingScreenOffTimeout(app_timeout);
        // default timeout (by using system settings with or without wakelocks)
        } else if ((app_timeout == TIMEOUT_SYSTEM) || (app_timeout == TIMEOUT_WAKELOCK)) {
            Logger.d(tag, String.format("set timeout for %s: (state: %d), restoring defaults: %d seconds",
                tag, app_timeout, sys_timeout / 1000));
            writeSettingScreenOffTimeout(sys_timeout);
        }
    }

    /**
     * set timeout based on activity state
     *
     * @param resumed - is the activity resumed and focused?
     */

    public void setTimeout(final boolean resumed) {
        try {
            if (resumed) {
                // back from paused: update android screen off timeout first
                sys_timeout = readSettingScreenOffTimeout();

                // apply a custom timeout for the application
                if ((sys_timeout != app_timeout) && (app_timeout > TIMEOUT_SYSTEM)) {
                    Logger.d(tag, String.format("restoring %s timeout: %d -> %d seconds",
                        tag, sys_timeout / 1000, app_timeout / 1000));

                    writeSettingScreenOffTimeout(app_timeout);
                }
            } else {
                // app paused: restore system timeout.
                if ((sys_timeout != app_timeout) && (app_timeout > TIMEOUT_SYSTEM)) {
                    Logger.d(tag, String.format("restoring system timeout: %d -> %d seconds",
                        app_timeout / 1000, sys_timeout / 1000));

                    writeSettingScreenOffTimeout(sys_timeout);
                }
            }
        } catch (Exception e) {
            Logger.e(tag, e.toString());
        }
    }

    /* Screen layout */
    public int isFullscreen() {
        return (is_fullscreen) ? 1 : 0;
    }

    public int isFullscreenDeprecated() {
        return ((((Activity)context).getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0) ? 1 : 0;
    }

    public void setFullscreen(final boolean fullscreen) {
        is_fullscreen = fullscreen;
    }

    public void setFullscreenDeprecated(final boolean fullscreen) {
        final CountDownLatch cd = new CountDownLatch(1);
        ((Activity)context).runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    final Window window = ((Activity)context).getWindow();
                    if (fullscreen) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                    }
                } catch (Exception e) {
                    Logger.e(tag, "setFullscreen error: " + e.toString());
                }
                cd.countDown();
            }
        });
        try {
            cd.await();
        } catch (InterruptedException ex) {
        }
    }

    private Point getScreenSize() {
        Point size = new Point();
        Display display = ((Activity)context).getWindowManager().getDefaultDisplay();
        try {
            // JellyBean 4.2 (API 17) and higher
            DisplayMetrics metrics = new DisplayMetrics();
            display.getRealMetrics(metrics);
            size.set(metrics.widthPixels, metrics.heightPixels);
        } catch (NoSuchMethodError e) {
            // Honeycomb 3.0 (API 11) and higher
            display.getSize(size);
        }
        return size;
    }


    private Point getScreenSizeWithConstraints() {
        Point size = new Point();
        Display display = ((Activity)context).getWindowManager().getDefaultDisplay();
        try {
            // JellyBean 4.2 (API 17) and higher
            DisplayMetrics metrics = new DisplayMetrics();
            display.getMetrics(metrics);
            size.set(metrics.widthPixels, metrics.heightPixels);
        } catch (NoSuchMethodError e) {
            // Honeycomb 3.0 (API 11) and higher
            display.getSize(size);
        }
        return size;
    }

    private class Box<T> {
        public T value;
    }

    private int readSettingScreenOffTimeout() {
        try {
            return Settings.System.getInt(context.getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT);
        } catch (Exception e) {
            Logger.e(tag, e.toString());
            return 0;
        }
    }

    private void writeSettingScreenOffTimeout(final int timeout) {
        if (timeout <= 0) return;

        try {
            Settings.System.putInt(context.getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT, timeout);
        } catch (Exception e) {
            Logger.e(tag, e.toString());
        }
    }
}