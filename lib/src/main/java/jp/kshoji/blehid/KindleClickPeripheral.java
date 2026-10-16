package jp.kshoji.blehid;

import android.bluetooth.le.AdvertiseData;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.NonNull;

/**
 * BLE page turner that a Kindle accepts as a Kindle Click (Amazon VID 0x0171, PID 0x0443)
 */
public final class KindleClickPeripheral extends HidPeripheral {

    /**
     * Characteristic Data(Report Map)
     */
    private static final byte[] REPORT_MAP = {
            USAGE_PAGE(1),      0x01,       // Generic Desktop Ctrls
            USAGE(1),           0x06,       // Keyboard
            COLLECTION(1),      0x01,       // Application
            REPORT_ID(1),       0x01,       //   Report ID 1 (a Kindle ignores Report ID 0)
            USAGE_PAGE(1),      0x07,       //   Kbrd/Keypad
            USAGE_MINIMUM(1), (byte) 0xE0,
            USAGE_MAXIMUM(1), (byte) 0xE7,
            LOGICAL_MINIMUM(1), 0x00,
            LOGICAL_MAXIMUM(1), 0x01,
            REPORT_SIZE(1),     0x01,       //   1 byte (Modifier)
            REPORT_COUNT(1),    0x08,
            INPUT(1),           0x02,       //   Data,Var,Abs
            REPORT_COUNT(1),    0x01,       //   1 byte (Reserved)
            REPORT_SIZE(1),     0x08,
            INPUT(1),           0x01,       //   Const
            REPORT_COUNT(1),    0x06,       //   6 bytes (Keys)
            REPORT_SIZE(1),     0x08,
            LOGICAL_MINIMUM(1), 0x00,
            LOGICAL_MAXIMUM(1), 0x65,       //   101 keys
            USAGE_PAGE(1),      0x07,       //   Kbrd/Keypad
            USAGE_MINIMUM(1),   0x00,
            USAGE_MAXIMUM(1),   0x65,
            INPUT(1),           0x00,       //   Data,Array,Abs
            // Consumer keys share the keyboard's collection, so the Kindle repeats a held light key
            USAGE_PAGE(1),      0x0C,       //   Consumer
            REPORT_COUNT(1),    0x01,       //   2 bytes (Consumer usage)
            REPORT_SIZE(1),     0x10,
            LOGICAL_MINIMUM(1), 0x00,
            LOGICAL_MAXIMUM(2), (byte) 0xFF, 0x03,
            USAGE_MINIMUM(1),   0x00,
            USAGE_MAXIMUM(2), (byte) 0xFF, 0x03,
            INPUT(1),           0x00,       //   Data,Array,Abs
            END_COLLECTION(0),
    };

    /**
     * PnP ID: vendor ID source Bluetooth SIG, VID 0x0171 (Amazon), PID 0x0443 (Kindle Click), version 0x0100
     */
    private static final byte[] PNP_ID = {0x01, 0x71, 0x01, 0x43, 0x04, 0x00, 0x01};

    /**
     * Consumer usages
     */
    private static final int CONSUMER_POWER = 0x30;
    private static final int CONSUMER_BRIGHTNESS_UP = 0x6F;
    private static final int CONSUMER_BRIGHTNESS_DOWN = 0x70;

    /**
     * A woken Kindle ignores input reports until it has set the remote up again, about 2 s after reconnecting.
     * A page key pressed this shortly before it subscribes is sent again, after a delay: the subscription comes
     * before the end of that setup (a Paperwhite took a page key 0.5 s after subscribing, but not 0.02 s after).
     */
    private static final long PAGE_KEY_RESEND_MS = 3000;
    private static final long PAGE_KEY_RESEND_DELAY_MS = 1000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private int keyCode;
    private int consumer;
    private int lastPageKey;
    private long lastPageKeyTime;

    /**
     * Constructor<br />
     * Before constructing the instance, check the Bluetooth availability.
     *
     * @param context the applicationContext
     */
    public KindleClickPeripheral(final Context context) throws UnsupportedOperationException {
        super(context.getApplicationContext(), true, false, false, 20);
    }

    @Override
    protected byte[] getReportMap() {
        return REPORT_MAP;
    }

    @Override
    protected int getReportId() {
        return 1;
    }

    @Override
    protected byte[] getPnpId() {
        return PNP_ID;
    }

    @Override
    protected String getFirmwareRevision() {
        // Shown as "Firmware version" in the Kindle's Settings › Accessories: the phone's model
        return Build.MODEL.length() > 20 ? Build.MODEL.substring(0, 20) : Build.MODEL;
    }

    @Override
    protected void addAdvertiseData(@NonNull final AdvertiseData.Builder advertiseData) {
        // The Kindle's pairing scan looks for Amazon's company ID followed by the PID (big-endian)
        advertiseData.addManufacturerData(0x0171, new byte[]{0x04, 0x43});
    }

    /**
     * Turn to the next page (Page Down)
     */
    public void pageNext() {
        tapPageKey(KeyboardPeripheral.KEY_PAGE_DOWN);
    }

    /**
     * Turn to the previous page (Page Up)
     */
    public void pagePrevious() {
        tapPageKey(KeyboardPeripheral.KEY_PAGE_UP);
    }

    /**
     * Press a light key; the Kindle keeps stepping the light until {@link #releaseBrightness()}
     *
     * @param up true: brighter, false: dimmer
     */
    public void pressBrightness(final boolean up) {
        consumer = up ? CONSUMER_BRIGHTNESS_UP : CONSUMER_BRIGHTNESS_DOWN;
        sendReport();
    }

    /**
     * Release the light key
     */
    public void releaseBrightness() {
        consumer = 0;
        sendReport();
    }

    /**
     * Put an awake Kindle to sleep: one Power key press, which the Click sends for its sleep gesture.
     * It also releases a held light key.
     */
    public void sleep() {
        consumer = CONSUMER_POWER;
        sendReport();
        consumer = 0;
        sendReport();
    }

    @Override
    protected void onInputReportSubscribed() {
        if (lastPageKey != 0 && SystemClock.elapsedRealtime() - lastPageKeyTime < PAGE_KEY_RESEND_MS) {
            final int code = lastPageKey;
            final long due = SystemClock.elapsedRealtime() + PAGE_KEY_RESEND_DELAY_MS;
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    // Not if the phone slept meanwhile (the delay stops counting): the key would come long after
                    if (SystemClock.elapsedRealtime() - due < PAGE_KEY_RESEND_DELAY_MS) {
                        tapKey(code);
                    }
                }
            }, PAGE_KEY_RESEND_DELAY_MS);
        }
        lastPageKey = 0;
    }

    private void tapPageKey(final int code) {
        lastPageKey = code;
        lastPageKeyTime = SystemClock.elapsedRealtime();
        tapKey(code);
    }

    private void tapKey(final int code) {
        keyCode = code;
        sendReport();
        keyCode = 0;
        sendReport();
    }

    private void sendReport() {
        addInputReport(new byte[]{0, 0, (byte) keyCode, 0, 0, 0, 0, 0, (byte) consumer, (byte) (consumer >> 8)});
    }

    @Override
    protected void onOutputReport(final byte[] outputReport) {
        // no output reports
    }
}
