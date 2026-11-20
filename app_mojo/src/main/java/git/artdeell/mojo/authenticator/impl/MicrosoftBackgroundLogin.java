package git.artdeell.mojo.authenticator.impl;

import static git.artdeell.mojo.MojoApplication.sExecutorService;

import android.net.Uri;
import android.util.ArrayMap;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;

import com.kdt.mcgui.ProgressLayout;

import git.artdeell.mojo.R;
import git.artdeell.mojo.Tools;
import git.artdeell.mojo.authenticator.AuthType;
import git.artdeell.mojo.authenticator.BackgroundLogin;
import git.artdeell.mojo.authenticator.accounts.Account;
import git.artdeell.mojo.authenticator.accounts.Accounts;
import git.artdeell.mojo.authenticator.listener.LoginListener;
import git.artdeell.mojo.utils.PresentableException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Callable;

/** Allow to perform a background login on a given account (ported from Amethyst Launcher) */
public class MicrosoftBackgroundLogin implements BackgroundLogin {
    public static final BackgroundLogin.Creator CREATOR = MicrosoftBackgroundLogin::new;

    private static final String authTokenUrl = "https://login.live.com/oauth20_token.srf";
    private static final String xblAuthUrl = "https://user.auth.xboxlive.com/user/authenticate";
    private static final String xstsAuthUrl = "https://xsts.auth.xboxlive.com/xsts/authorize";
    private static final String mcLoginUrl = "https://api.minecraftservices.com/authentication/login_with_xbox";
    private static final String mcProfileUrl = "https://api.minecraftservices.com/minecraft/profile";
    private static final String mcStoreUrl = "https://api.minecraftservices.com/entitlements/mcstore";

    private static final Map<Long, Integer> XSTS_ERRORS;
    static {
        XSTS_ERRORS = new ArrayMap<>();
        XSTS_ERRORS.put(2148916233L, R.string.xerr_no_account);
        XSTS_ERRORS.put(2148916235L, R.string.xerr_not_available);
        XSTS_ERRORS.put(2148916236L, R.string.xerr_adult_verification);
        XSTS_ERRORS.put(2148916237L, R.string.xerr_adult_verification);
        XSTS_ERRORS.put(2148916238L, R.string.xerr_child);
    }

    /* Fields used to fill the account */
    public String msRefreshToken;
    public String mcName;
    public String mcToken;
    public String mcUuid;
    public String msXsts;
    public boolean doesOwnGame, hasProfile = false;
    public long expiresAt;

    public MicrosoftBackgroundLogin() {}

    private void acquireAccountDetails(
            @NonNull LoginListener loginListener, Callable<Void> continuation,
            String code, boolean isRefresh
    ) {
        ProgressLayout.setProgress(ProgressLayout.AUTHENTICATE, 0);
        sExecutorService.execute(() -> {
            loginListener.setMaxLoginProgress(5);
            try {
                notifyProgress(loginListener, 1);
                String accessToken = acquireAccessToken(isRefresh, code);
                notifyProgress(loginListener, 2);
                String xboxLiveToken = acquireXBLToken(accessToken);
                notifyProgress(loginListener, 3);
                String[] xsts = acquireXsts(xboxLiveToken);
                notifyProgress(loginListener, 4);
                String token = acquireToken(xsts[0], xsts[1]);
                notifyProgress(loginListener, 5);
                checkProfile(token);
                fetchOwnedItems(token);

                if (!doesOwnGame) {
                    throw new PresentableException(R.string.mc_not_owned);
                }
                if (!hasProfile || mcName == null || mcUuid == null || mcName.trim().isEmpty() || mcUuid.trim().isEmpty() || mcUuid.equals("00000000-0000-0000-0000-000000000000") || mcName.equals("Demo.Player") || mcName.equals("Steve")) {
                    throw new PresentableException(R.string.minecraft_no_username_set);
                }

                msXsts = xsts[0];
                continuation.call();
            } catch (Exception e) {
                Log.e("MicroAuth", "Exception thrown during authentication", e);
                Tools.runOnUiThread(() -> loginListener.onLoginError(e));
            } finally {
                ProgressLayout.clearProgress(ProgressLayout.AUTHENTICATE);
            }
        });
    }

    private void fillAccount(Account acc) {
        acc.xuid = msXsts;
        acc.accessToken = mcToken;
        acc.username = mcName;
        acc.profileId = mcUuid;
        acc.authType = AuthType.MICROSOFT;
        acc.isMicrosoft = true;
        acc.refreshToken = msRefreshToken;
        acc.msaRefreshToken = msRefreshToken;
        acc.expiresAt = expiresAt;
        acc.updateSkinFace();
    }

    @Override
    public void createAccount(@NonNull LoginListener loginListener, String code) {
        String authCode = code;
        if (code != null && (code.contains("code=") || code.contains("ms-xal-") || code.contains("http"))) {
            try {
                Uri uri = Uri.parse(code);
                String paramCode = uri.getQueryParameter("code");
                if (paramCode != null && !paramCode.isEmpty()) {
                    authCode = paramCode;
                }
            } catch (Exception e) {
                Log.w("MicroAuth", "Failed to parse code as URI", e);
            }
        }
        final String finalAuthCode = authCode;
        acquireAccountDetails(loginListener, () -> {
            Account account = Accounts.create(this::fillAccount);
            Accounts.setCurrent(account);
            Tools.runOnUiThread(() -> loginListener.onLoginDone(account));
            return null;
        }, finalAuthCode, false);
    }

    @Override
    public void refreshAccount(@NonNull LoginListener loginListener, Account account) {
        acquireAccountDetails(loginListener, () -> {
            if (!doesOwnGame || !hasProfile) {
                throw new PresentableException(R.string.mc_not_owned);
            }
            fillAccount(account);
            account.save();
            Accounts.setCurrent(account);
            Tools.runOnUiThread(() -> loginListener.onLoginDone(account));
            return null;
        }, account.refreshToken, true);
    }

    private String acquireAccessToken(boolean isRefresh, String authcode) throws IOException, JSONException, PresentableException {
        URL url = new URL(authTokenUrl);
        Log.i("MicrosoftLogin", "isRefresh=" + isRefresh + ", authCode= " + authcode);

        String formData = convertToFormData(
                "client_id", "00000000402b5328",
                isRefresh ? "refresh_token" : "code", authcode,
                "grant_type", isRefresh ? "refresh_token" : "authorization_code",
                "redirect_url", "https://login.live.com/oauth20_desktop.srf",
                "scope", "service::user.auth.xboxlive.com::MBI_SSL"
        );

        Log.i("MicroAuth", formData);

        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setRequestProperty("charset", "utf-8");
        conn.setRequestProperty("Content-Length", Integer.toString(formData.getBytes(StandardCharsets.UTF_8).length));
        conn.setRequestMethod("POST");
        conn.setUseCaches(false);
        conn.setDoInput(true);
        conn.setDoOutput(true);
        conn.connect();
        try (OutputStream wr = conn.getOutputStream()) {
            wr.write(formData.getBytes(StandardCharsets.UTF_8));
        }
        if (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
            JSONObject jo = new JSONObject(Tools.read(conn.getInputStream()));
            msRefreshToken = jo.getString("refresh_token");
            conn.disconnect();
            Log.i("MicrosoftLogin", "Access Token = " + jo.getString("access_token"));
            return jo.getString("access_token");
        } else {
            throw getResponseThrowable(conn);
        }
    }

    private String acquireXBLToken(String accessToken) throws IOException, JSONException, PresentableException {
        URL url = new URL(xblAuthUrl);

        JSONObject data = new JSONObject();
        JSONObject properties = new JSONObject();
        properties.put("AuthMethod", "RPS");
        properties.put("SiteName", "user.auth.xboxlive.com");
        properties.put("RpsTicket", accessToken);
        data.put("Properties", properties);
        data.put("RelyingParty", "http://auth.xboxlive.com");
        data.put("TokenType", "JWT");

        String req = data.toString();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        setCommonProperties(conn, req);
        conn.connect();

        try (OutputStream wr = conn.getOutputStream()) {
            wr.write(req.getBytes(StandardCharsets.UTF_8));
        }
        if (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
            JSONObject jo = new JSONObject(Tools.read(conn.getInputStream()));
            conn.disconnect();
            Log.i("MicrosoftLogin", "Xbl Token = " + jo.getString("Token"));
            return jo.getString("Token");
        } else {
            throw getResponseThrowable(conn);
        }
    }

    /** @return [uhs, token] */
    private @NonNull String[] acquireXsts(String xblToken) throws IOException, JSONException, PresentableException {
        URL url = new URL(xstsAuthUrl);

        JSONObject data = new JSONObject();
        JSONObject properties = new JSONObject();
        properties.put("SandboxId", "RETAIL");
        properties.put("UserTokens", new JSONArray(Collections.singleton(xblToken)));
        data.put("Properties", properties);
        data.put("RelyingParty", "rp://api.minecraftservices.com/");
        data.put("TokenType", "JWT");

        String req = data.toString();
        Log.i("MicroAuth", req);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        setCommonProperties(conn, req);
        Log.i("MicroAuth", conn.getRequestMethod());
        conn.connect();

        try (OutputStream wr = conn.getOutputStream()) {
            wr.write(req.getBytes(StandardCharsets.UTF_8));
        }

        if (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
            JSONObject jo = new JSONObject(Tools.read(conn.getInputStream()));
            String uhs = jo.getJSONObject("DisplayClaims").getJSONArray("xui").getJSONObject(0).getString("uhs");
            String token = jo.getString("Token");
            conn.disconnect();
            Log.i("MicrosoftLogin", "Xbl Xsts = " + token + "; Uhs = " + uhs);
            return new String[]{uhs, token};
        } else if (conn.getResponseCode() == 401) {
            String responseContents = Tools.read(conn.getErrorStream());
            JSONObject jo = new JSONObject(responseContents);
            long xerr = jo.optLong("XErr", -1);
            Integer locale_id = XSTS_ERRORS.get(xerr);
            if (locale_id != null) {
                throw new PresentableException(new RuntimeException(responseContents), locale_id);
            }
            throw new PresentableException(new RuntimeException(responseContents), R.string.xerr_unknown, xerr);
        } else {
            throw getResponseThrowable(conn);
        }
    }

    private String acquireToken(String xblUhs, String xblXsts) throws IOException, JSONException, PresentableException {
        URL url = new URL(mcLoginUrl);

        JSONObject data = new JSONObject();
        data.put("identityToken", "XBL3.0 x=" + xblUhs + ";" + xblXsts);

        String req = data.toString();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        setCommonProperties(conn, req);
        conn.connect();

        try (OutputStream wr = conn.getOutputStream()) {
            wr.write(req.getBytes(StandardCharsets.UTF_8));
        }

        if (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
            expiresAt = System.currentTimeMillis() + 86400000;
            JSONObject jo = new JSONObject(Tools.read(conn.getInputStream()));
            conn.disconnect();
            Log.i("MicrosoftLogin", "MC token: " + jo.getString("access_token"));
            mcToken = jo.getString("access_token");
            return jo.getString("access_token");
        } else {
            throw getResponseThrowable(conn);
        }
    }

    private void checkProfile(String mcAccessToken) throws IOException, JSONException, PresentableException {
        URL url = new URL(mcProfileUrl);
        hasProfile = false;
        mcName = null;
        mcUuid = null;

        for (int retryCount = 0; retryCount < 5; ++retryCount) {
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("Authorization", "Bearer " + mcAccessToken);
            conn.setUseCaches(false);
            conn.connect();

            if (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
                String s = Tools.read(conn.getInputStream());
                conn.disconnect();
                Log.i("MicrosoftLogin", "profile:" + s);
                JSONObject jsonObject = new JSONObject(s);
                String name = jsonObject.optString("name", null);
                String uuid = jsonObject.optString("id", null);
                if (name != null && !name.trim().isEmpty() && uuid != null && !uuid.trim().isEmpty()) {
                    String uuidDashes = uuid.replaceFirst(
                            "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)", "$1-$2-$3-$4-$5"
                    );
                    if (!uuidDashes.equals("00000000-0000-0000-0000-000000000000")) {
                        hasProfile = true;
                        mcName = name;
                        mcUuid = uuidDashes;
                    }
                }
                break;
            } else if (conn.getResponseCode() == 404) {
                Log.i("MicrosoftLogin", "Account does not have a Minecraft Java profile set.");
                hasProfile = false;
                break;
            } else if (conn.getResponseCode() == 401) {
                throw getResponseThrowable(conn);
            } else if (retryCount == 4) {
                throw getResponseThrowable(conn);
            }
            try {
                Thread.sleep(500L * (1L << retryCount));
            } catch (InterruptedException ignored) {}
        }
    }

    private void fetchOwnedItems(String mcAccessToken) throws IOException, PresentableException {
        doesOwnGame = false;
        URL url = new URL(mcStoreUrl);
        String s = "";

        for (int retryCount = 0; retryCount < 5; ++retryCount) {
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("Authorization", "Bearer " + mcAccessToken);
            conn.setUseCaches(false);
            conn.connect();
            if (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300) {
                s = Tools.read(conn.getInputStream());
                conn.disconnect();
                break;
            } else if (conn.getResponseCode() == 401 || conn.getResponseCode() == 403 || conn.getResponseCode() == 404) {
                conn.disconnect();
                doesOwnGame = false;
                return;
            } else if (retryCount == 4) {
                throw getResponseThrowable(conn);
            }
            try {
                Thread.sleep(500L * (1L << retryCount));
            } catch (InterruptedException ignored) {}
        }

        if (s == null || s.trim().isEmpty()) {
            doesOwnGame = false;
            return;
        }

        try {
            JSONObject root = new JSONObject(s);

            JSONArray items = root.optJSONArray("items");
            if (items != null) {
                for (int i = 0; i < items.length(); ++i) {
                    JSONObject item = items.optJSONObject(i);
                    if (item != null) {
                        String name = item.optString("name", "");
                        if ("product_minecraft".equalsIgnoreCase(name) || "game_minecraft".equalsIgnoreCase(name)) {
                            doesOwnGame = true;
                            return;
                        }
                    }
                }
            }

            String jwtSignature = root.optString("signature", null);
            if (jwtSignature != null && jwtSignature.contains(".")) {
                String[] parts = jwtSignature.split("\\.");
                if (parts.length >= 2) {
                    String jwtBody = parts[1];
                    String signature = new String(
                            Base64.decode(jwtBody, Base64.DEFAULT),
                            StandardCharsets.UTF_8
                    );
                    JSONObject jsonSignature = new JSONObject(signature);
                    JSONArray entitlements = jsonSignature.optJSONArray("entitlements");
                    if (entitlements != null) {
                        for (int i = 0; i < entitlements.length(); ++i) {
                            String entName = null;
                            JSONObject entObj = entitlements.optJSONObject(i);
                            if (entObj != null) {
                                entName = entObj.optString("name", null);
                            } else {
                                entName = entitlements.optString(i, null);
                            }
                            if (entName != null && ("product_minecraft".equalsIgnoreCase(entName) || "game_minecraft".equalsIgnoreCase(entName))) {
                                doesOwnGame = true;
                                return;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.w("MicrosoftLogin", "Failed to parse Minecraft store entitlements response", e);
            doesOwnGame = false;
        }
    }

    /** Wrapper to ease notifying the listener */
    private void notifyProgress(LoginListener listener, int step) {
        Tools.runOnUiThread(() -> listener.onLoginProgress(step));
        ProgressLayout.setProgress(ProgressLayout.AUTHENTICATE, step * 20);
    }

    /** Set common properties for the connection. Given that all requests are POST, interactivity is always enabled */
    private static void setCommonProperties(HttpURLConnection conn, String formData) {
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("charset", "utf-8");
        try {
            conn.setRequestProperty("Content-Length", Integer.toString(formData.getBytes(StandardCharsets.UTF_8).length));
            conn.setRequestMethod("POST");
        } catch (ProtocolException e) {
            Log.e("MicrosoftAuth", e.toString());
        }
        conn.setUseCaches(false);
        conn.setDoInput(true);
        conn.setDoOutput(true);
    }

    private static String convertToFormData(String... data) throws UnsupportedEncodingException {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < data.length; i += 2) {
            if (builder.length() > 0) builder.append("&");
            builder.append(URLEncoder.encode(data[i], "UTF-8"))
                    .append("=")
                    .append(URLEncoder.encode(data[i + 1], "UTF-8"));
        }
        return builder.toString();
    }

    private PresentableException getResponseThrowable(HttpURLConnection conn) throws IOException {
        Log.i("MicrosoftLogin", "Error code: " + conn.getResponseCode() + ": " + conn.getResponseMessage());
        if (conn.getResponseCode() == 429) {
            return new PresentableException(R.string.microsoft_login_retry_later);
        }
        return new PresentableException(R.string.global_error, R.string.microsoft_login_generic, conn.getResponseMessage());
    }
}
