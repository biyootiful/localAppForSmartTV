package local.tv.streamshell;

import android.net.Uri;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** The one service this app opens, and how to recognise its pages. */
final class Sites {
    static final String HOME_URL = "https://play.hbomax.com/";

    /** Origin patterns in the form androidx.webkit expects; "*." does not cover the bare domain. */
    static final Set<String> HBO_ORIGIN_RULES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "https://hbomax.com", "https://*.hbomax.com",
            "https://max.com", "https://*.max.com")));

    private static final String[] HBO_DOMAINS = {"hbomax.com", "max.com"};

    private Sites() {}

    static boolean isHbo(Uri uri) {
        if (uri == null || !"https".equals(uri.getScheme())) return false;
        String host = uri.getHost();
        if (host == null) return false;
        host = host.toLowerCase();
        for (String domain : HBO_DOMAINS) {
            if (host.equals(domain) || host.endsWith("." + domain)) return true;
        }
        return false;
    }
}
