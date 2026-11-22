package local.tv.streamshell;

import android.webkit.WebSettings;
import android.webkit.WebView;

import androidx.webkit.UserAgentMetadata;
import androidx.webkit.WebSettingsCompat;
import androidx.webkit.WebViewFeature;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Makes the WebView present itself as desktop Chrome, so HBO Max serves its web player
 * instead of a "get the app" page. The Chrome version is taken from the TV's own WebView,
 * so it stays current as the WebView updates.
 */
final class DesktopIdentity {
    private static final Pattern CHROME_MAJOR = Pattern.compile("Chrome/(\\d+)");

    private DesktopIdentity() {}

    static void apply(WebView webView) {
        WebSettings settings = webView.getSettings();
        String system = WebSettings.getDefaultUserAgent(webView.getContext());
        Matcher m = CHROME_MAJOR.matcher(system);
        String major = m.find() ? m.group(1) : "140";
        settings.setUserAgentString("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 "
                + "(KHTML, like Gecko) Chrome/" + major + ".0.0.0 Safari/537.36");

        // Client hints (Sec-CH-UA-*, navigator.userAgentData) must tell the same story as the string.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) {
            UserAgentMetadata current = WebSettingsCompat.getUserAgentMetadata(settings);
            WebSettingsCompat.setUserAgentMetadata(settings, new UserAgentMetadata.Builder(current)
                    .setBrandVersionList(desktopBrands(current.getBrandVersionList()))
                    .setPlatform("Linux")
                    .setPlatformVersion("")
                    .setModel("")
                    .setArchitecture("x86")
                    .setBitness(64)
                    .setMobile(false)
                    .build());
        }
    }

    /** Same brand list, with "Android WebView" renamed to "Google Chrome". */
    private static List<UserAgentMetadata.BrandVersion> desktopBrands(List<UserAgentMetadata.BrandVersion> brands) {
        List<UserAgentMetadata.BrandVersion> out = new ArrayList<>(brands.size());
        for (UserAgentMetadata.BrandVersion brand : brands) {
            if ("Android WebView".equals(brand.getBrand())) {
                out.add(new UserAgentMetadata.BrandVersion.Builder()
                        .setBrand("Google Chrome")
                        .setMajorVersion(brand.getMajorVersion())
                        .setFullVersion(brand.getFullVersion())
                        .build());
            } else {
                out.add(brand);
            }
        }
        return out;
    }
}
