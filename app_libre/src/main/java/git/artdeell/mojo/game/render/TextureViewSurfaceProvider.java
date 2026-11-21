package git.artdeell.mojo.game.render;

import static git.artdeell.mojo.CallbackBridge.windowHeight;
import static git.artdeell.mojo.CallbackBridge.windowWidth;

import android.content.Context;
import android.os.Build;
import android.graphics.SurfaceTexture;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;

import androidx.annotation.NonNull;

import git.artdeell.mojo.Tools;

public class TextureViewSurfaceProvider implements SurfaceProvider {
    private TextureView mTextureView;
    private SurfaceCallback mCallback;

    @Override
    public View create(Context context, SurfaceCallback callback) {
        mCallback = callback;
        mTextureView = new TextureView(context);
        mTextureView.setOpaque(true);
        mTextureView.setAlpha(1.0f);
        mTextureView.setSurfaceTextureListener(new CallbackAdapter());
        return mTextureView;
    }

    @Override
    public void updateSize() {
        SurfaceTexture surfaceTexture = mTextureView.getSurfaceTexture();
        if(surfaceTexture != null) {
            surfaceTexture.setDefaultBufferSize(windowWidth, windowHeight);
            Tools.runOnUiThread(()->mCallback.onSurfaceResized());
        }
    }

    private class CallbackAdapter implements TextureView.SurfaceTextureListener {

        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surfaceTexture, int i, int i1) {
            if(windowWidth != 0 && windowHeight != 0)
                surfaceTexture.setDefaultBufferSize(windowWidth, windowHeight);
            Surface surface = new Surface(surfaceTexture);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && surface.isValid()) {
                try {
                    surface.setFrameRate(0f, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT);
                } catch (Throwable ignored) {}
            }
            mCallback.onSurfaceAvailable(surface);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surfaceTexture) {
            mCallback.onSurfaceDestroyed();
            return true;
        }

        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surfaceTexture, int i, int i1) {
            mCallback.onSurfaceResized();
        }

        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surfaceTexture) {

        }
    }
}
