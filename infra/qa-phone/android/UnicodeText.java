package com.ssafy.qa;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Base64;

/** Receives only the operator's text over stdin; never reads the phone clipboard. */
public final class UnicodeText {
    public static void main(String[] args) throws Exception {
        String encoded = new BufferedReader(new InputStreamReader(System.in)).readLine();
        if (encoded == null || encoded.length() > 1024) throw new IllegalArgumentException("text limit");
        String text = new String(Base64.getDecoder().decode(encoded), "UTF-8");
        if (text.isEmpty() || text.length() > 128 || text.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("invalid text");
        }
        Class<?> binderType = Class.forName("android.os.IBinder");
        Object binder = Class.forName("android.os.ServiceManager")
                .getMethod("getService", String.class).invoke(null, "clipboard");
        Object clipboard = Class.forName("android.content.IClipboard$Stub")
                .getMethod("asInterface", binderType).invoke(null, binder);
        Class<?> clipType = Class.forName("android.content.ClipData");
        Object clip = clipType.getMethod("newPlainText", CharSequence.class, CharSequence.class)
                .invoke(null, "QA input", text);
        int user = (Integer) Class.forName("android.app.ActivityManager").getMethod("getCurrentUser").invoke(null);
        Class<?> service = Class.forName("android.content.IClipboard");
        try {
            service.getMethod("setPrimaryClip", clipType, String.class, int.class)
                    .invoke(clipboard, clip, "com.android.shell", user);
        } catch (NoSuchMethodException legacyUnavailable) {
            // Android 16: attribution tag and default physical device ID (0).
            service.getMethod("setPrimaryClip", clipType, String.class, String.class, int.class, int.class)
                    .invoke(clipboard, clip, "com.android.shell", null, user, 0);
        }
        System.out.println("READY");
    }
}
