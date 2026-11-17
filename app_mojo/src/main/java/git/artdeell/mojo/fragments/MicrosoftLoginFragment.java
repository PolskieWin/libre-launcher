package git.artdeell.mojo.fragments;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import git.artdeell.mojo.R;
import git.artdeell.mojo.Tools;
import git.artdeell.mojo.extra.ExtraConstants;
import git.artdeell.mojo.extra.ExtraCore;

public class MicrosoftLoginFragment extends Fragment {
    public static final String TAG = "MICROSOFT_LOGIN_FRAGMENT";
    private WebView mWebview;
    // Technically the client is blank (or there is none) when the fragment is initialized
    private boolean mBlankClient = true;
    private boolean mSignaled = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mWebview = (WebView) inflater.inflate(R.layout.fragment_microsoft_login, container, false);
        setWebViewSettings();
        if(savedInstanceState == null) startNewSession();
        else restoreWebViewState(savedInstanceState);
        return mWebview;
    }

    // WebView.restoreState() does not restore the WebSettings or the client, so set them there
    // separately. Note that general state should not be altered here (aka no loading pages, no manipulating back/front lists),
    // to avoid "undesirable side-effects"
    @SuppressLint("SetJavaScriptEnabled")
    private void setWebViewSettings() {
        WebSettings settings = mWebview.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        mWebview.setWebViewClient(new WebViewTrackClient());
        mBlankClient = false;
    }

    private void startNewSession() {
        CookieManager.getInstance().removeAllCookies((b)->{
            if (mWebview == null) return;
            mWebview.clearHistory();
            mWebview.clearCache(true);
            mWebview.clearFormData();
            mWebview.loadUrl("https://login.live.com/oauth20_authorize.srf" +
                    "?client_id=00000000402b5328" +
                    "&response_type=code" +
                    "&scope=service%3A%3Auser.auth.xboxlive.com%3A%3AMBI_SSL" +
                    "&redirect_url=https%3A%2F%2Flogin.live.com%2Foauth20_desktop.srf");
        });
    }

    private void restoreWebViewState(Bundle savedInstanceState) {
        Log.i("MSAuthFragment","Restoring state...");
        if(mWebview.restoreState(savedInstanceState) == null) {
            Log.w("MSAuthFragment", "Failed to restore state, starting afresh");
            startNewSession();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if(mBlankClient && mWebview != null) mWebview.setWebViewClient(new WebViewTrackClient());
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        if (mWebview != null) {
            mWebview.setWebViewClient(new WebViewClient());
            mBlankClient = true;
            super.onSaveInstanceState(outState);
            mWebview.saveState(outState);
        } else {
            super.onSaveInstanceState(outState);
        }
    }

    /* Expose webview actions to others */
    public boolean canGoBack(){ return mWebview != null && mWebview.canGoBack();}
    public void goBack(){ if(mWebview != null) mWebview.goBack();}

    /** Client to track when to sent the data to the launcher */
    class WebViewTrackClient extends WebViewClient {

        private boolean checkUrl(String url) {
            if (url == null || mSignaled) return false;

            if (url.startsWith("ms-xal-00000000402b5328") || url.startsWith("https://login.live.com/oauth20_desktop.srf")) {
                mSignaled = true;
                ExtraCore.setValue(ExtraConstants.MICROSOFT_LOGIN_TODO, url);
                Toast.makeText(requireContext(), "Login started !", Toast.LENGTH_SHORT).show();
                Tools.backToMainMenu(requireActivity());
                return true;
            }

            if (url.contains("res=cancel")) {
                mSignaled = true;
                requireActivity().onBackPressed();
                return true;
            }

            return false;
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            if (checkUrl(url)) return true;
            return super.shouldOverrideUrlLoading(view, url);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            if (request != null && request.getUrl() != null) {
                if (checkUrl(request.getUrl().toString())) return true;
            }
            return super.shouldOverrideUrlLoading(view, request);
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            checkUrl(url);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            checkUrl(url);
        }
    }
}
