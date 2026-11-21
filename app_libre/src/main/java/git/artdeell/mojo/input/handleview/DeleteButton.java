package git.artdeell.mojo.input.handleview;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.widget.Button;

import androidx.annotation.Nullable;

import git.artdeell.mojo.R;
import git.artdeell.mojo.input.buttons.ControlInterface;

@SuppressLint("AppCompatCustomView")
public class DeleteButton extends Button implements ActionButtonInterface {
    public DeleteButton(Context context) {super(context); init();}
    public DeleteButton(Context context, @Nullable AttributeSet attrs) {super(context, attrs); init();}

    public void init() {
        setOnClickListener(this);
        setAllCaps(true);
        setText(R.string.global_delete);
        setBackgroundResource(R.drawable.bg_drawer_item);
        setTextColor(git.artdeell.mojo.utils.ThemeColors.get(getContext(), com.google.android.material.R.attr.colorError));
        setTextSize(12);
    }

    private ControlInterface mCurrentlySelectedButton = null;

    @Override
    public boolean shouldBeVisible() {
        return mCurrentlySelectedButton != null;
    }

    @Override
    public void setFollowedView(ControlInterface view) {
        mCurrentlySelectedButton = view;
    }

    @Override
    public void onClick() {
        performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
        if(mCurrentlySelectedButton == null) return;

        mCurrentlySelectedButton.removeButton();
    }
}
