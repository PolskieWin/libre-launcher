package git.artdeell.mojo.game.render;

import static git.artdeell.mojo.CallbackBridge.windowHeight;
import static git.artdeell.mojo.CallbackBridge.windowWidth;

import android.content.Context;
import android.os.Build;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import androidx.annotation.NonNull;

public class SurfaceViewSurfaceProvider implements SurfaceProvider {
    private SurfaceView mSurfaceView;
    @Override
    public View create(Context context, SurfaceCallback callback) {
        mSurfaceView = new SurfaceView(context);
        mSurfaceView.getHolder().addCallback(new CallbackAdapter(callback));
        if(windowWidth != 0 && windowHeight != 0)
            mSurfaceView.getHolder().setFixedSize(windowWidth, windowHeight);
        return mSurfaceView;
    }

    @Override
    public void updateSize() {
        mSurfaceView.getHolder().setFixedSize(windowWidth, windowHeight);
    }

    private static class CallbackAdapter implements SurfaceHolder.Callback {
        private final SurfaceCallback mCallback;

        private CallbackAdapter(SurfaceCallback mCallback) {
            this.mCallback = mCallback;
        }

        @Override
        public void surfaceChanged(@NonNull SurfaceHolder surfaceHolder, int fmt, int width, int height) {
            Surface surface = surfaceHolder.getSurface();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && surface != null && surface.isValid()) {
                try {
                    surface.setFrameRate(0f, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT);
                } catch (Throwable ignored) {}
            }
            mCallback.onSurfaceResized();
        }

        @Override
        public void surfaceCreated(@NonNull SurfaceHolder surfaceHolder) {
            Surface surface = surfaceHolder.getSurface();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && surface != null && surface.isValid()) {
                try {
                    surface.setFrameRate(0f, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT);
                } catch (Throwable ignored) {}
            }
            mCallback.onSurfaceAvailable(surface);
        }

        @Override
        public void surfaceDestroyed(@NonNull SurfaceHolder surfaceHolder) {
            mCallback.onSurfaceDestroyed();
        }
    }
}
