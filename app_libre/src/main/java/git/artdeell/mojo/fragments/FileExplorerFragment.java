package git.artdeell.mojo.fragments;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import git.artdeell.mojo.R;
import git.artdeell.mojo.Tools;
import git.artdeell.mojo.instances.Instance;
import git.artdeell.mojo.instances.Instances;
import git.artdeell.mojo.utils.FileUtils;
import git.artdeell.mojo.utils.ThemeColors;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class FileExplorerFragment extends Fragment {
    public static final String TAG = "FileExplorerFragment";
    public static final String EXTRA_INITIAL_PATH = "initial_path";
    public static final String EXTRA_ROOT_PATH = "root_path";

    private File mCurrentDirectory;
    private File mRootDirectory;

    private RecyclerView mRecyclerView;
    private View mEmptyView;
    private LinearLayout mBreadcrumbContainer;
    private TextView mTitleView;
    private FileAdapter mAdapter;

    private final List<File> mEntries = new ArrayList<>();
    private final SimpleDateFormat mDateFormat = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String initialPath = null;
        String rootPath = null;
        if (getArguments() != null) {
            initialPath = getArguments().getString(EXTRA_INITIAL_PATH);
            rootPath = getArguments().getString(EXTRA_ROOT_PATH);
        }

        if (initialPath != null) {
            mCurrentDirectory = new File(initialPath);
        } else {
            Instance instance = Instances.loadSelectedInstance();
            if (instance != null) {
                mCurrentDirectory = instance.getGameDirectory();
            } else if (Tools.DIR_GAME_HOME != null) {
                mCurrentDirectory = new File(Tools.DIR_GAME_HOME);
            } else {
                mCurrentDirectory = requireContext().getFilesDir();
            }
        }

        if (rootPath != null) {
            mRootDirectory = new File(rootPath);
        } else {
            mRootDirectory = mCurrentDirectory;
        }

        requireActivity().getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (canNavigateUp()) {
                    navigateUp();
                } else {
                    setEnabled(false);
                    Tools.removeCurrentFragment(requireActivity());
                }
            }
        });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_file_explorer, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mTitleView = view.findViewById(R.id.file_explorer_title);
        mBreadcrumbContainer = view.findViewById(R.id.file_breadcrumb_container);
        mRecyclerView = view.findViewById(R.id.file_explorer_recycler);
        mEmptyView = view.findViewById(R.id.file_explorer_empty);

        int spanCount = getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE ? 3 : 2;
        mRecyclerView.setLayoutManager(new GridLayoutManager(requireContext(), spanCount));
        mAdapter = new FileAdapter();
        mRecyclerView.setAdapter(mAdapter);

        ImageButton backButton = view.findViewById(R.id.file_nav_back);
        backButton.setOnClickListener(v -> {
            if (canNavigateUp()) {
                navigateUp();
            } else {
                Tools.removeCurrentFragment(requireActivity());
            }
        });

        ImageButton newFolderBtn = view.findViewById(R.id.file_action_new_folder);
        newFolderBtn.setOnClickListener(v -> showNewFolderDialog());

        ImageButton refreshBtn = view.findViewById(R.id.file_action_refresh);
        refreshBtn.setOnClickListener(v -> loadDirectory(mCurrentDirectory));

        ImageButton systemFilesBtn = view.findViewById(R.id.file_action_system);
        systemFilesBtn.setOnClickListener(v -> {
            try {
                Tools.openPath(requireContext(), mCurrentDirectory, false);
            } catch (Throwable t) {
                Toast.makeText(requireContext(), R.string.gamedir_open_failed, Toast.LENGTH_SHORT).show();
            }
        });

        setupQuickChips(view);
        loadDirectory(mCurrentDirectory);
    }

    private void setupQuickChips(@NonNull View view) {
        File baseDir = mRootDirectory != null ? mRootDirectory : mCurrentDirectory;

        bindChip(view.findViewById(R.id.chip_root), baseDir);
        bindChip(view.findViewById(R.id.chip_mods), new File(baseDir, "mods"));
        bindChip(view.findViewById(R.id.chip_resourcepacks), new File(baseDir, "resourcepacks"));
        bindChip(view.findViewById(R.id.chip_saves), new File(baseDir, "saves"));
        bindChip(view.findViewById(R.id.chip_screenshots), new File(baseDir, "screenshots"));
        bindChip(view.findViewById(R.id.chip_logs), new File(baseDir, "logs"));
        bindChip(view.findViewById(R.id.chip_config), new File(baseDir, "config"));
    }

    private void bindChip(@Nullable View chip, @NonNull File targetDir) {
        if (chip == null) return;
        chip.setOnClickListener(v -> {
            FileUtils.ensureDirectorySilently(targetDir);
            loadDirectory(targetDir);
        });
    }

    private boolean canNavigateUp() {
        return mCurrentDirectory != null && mCurrentDirectory.getParentFile() != null;
    }

    private void navigateUp() {
        if (canNavigateUp()) {
            loadDirectory(mCurrentDirectory.getParentFile());
        }
    }

    public void loadDirectory(@NonNull File dir) {
        mCurrentDirectory = dir;
        mTitleView.setText(dir.getName().isEmpty() ? "/" : dir.getName());

        updateBreadcrumbs();

        mEntries.clear();
        File[] files = dir.listFiles();
        if (files != null) {
            Arrays.sort(files, (a, b) -> {
                if (a.isDirectory() && !b.isDirectory()) return -1;
                if (!a.isDirectory() && b.isDirectory()) return 1;
                return a.getName().compareToIgnoreCase(b.getName());
            });
            mEntries.addAll(Arrays.asList(files));
        }

        mAdapter.notifyDataSetChanged();
        mEmptyView.setVisibility(mEntries.isEmpty() ? View.VISIBLE : View.GONE);
        mRecyclerView.setVisibility(mEntries.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void updateBreadcrumbs() {
        mBreadcrumbContainer.removeAllViews();
        if (mCurrentDirectory == null) return;

        List<File> hierarchy = new ArrayList<>();
        File curr = mCurrentDirectory;
        while (curr != null) {
            hierarchy.add(curr);
            if (mRootDirectory != null && curr.equals(mRootDirectory)) break;
            curr = curr.getParentFile();
        }
        Collections.reverse(hierarchy);

        for (int i = 0; i < hierarchy.size(); i++) {
            File f = hierarchy.get(i);
            TextView pill = new TextView(requireContext());
            pill.setBackgroundResource(R.drawable.bg_breadcrumb_pill);
            pill.setText(f.getName().isEmpty() ? "/" : f.getName());
            pill.setTextColor(ThemeColors.get(requireContext(), com.google.android.material.R.attr.colorOnSurface));
            pill.setTextSize(12);
            pill.setPadding(dp(10), dp(4), dp(10), dp(4));
            pill.setClickable(true);
            pill.setFocusable(true);
            pill.setOnClickListener(v -> loadDirectory(f));

            mBreadcrumbContainer.addView(pill);

            if (i < hierarchy.size() - 1) {
                TextView arrow = new TextView(requireContext());
                arrow.setText(" > ");
                arrow.setTextColor(ThemeColors.get(requireContext(), com.google.android.material.R.attr.colorOnSurfaceVariant));
                arrow.setTextSize(12);
                arrow.setPadding(dp(2), 0, dp(2), 0);
                mBreadcrumbContainer.addView(arrow);
            }
        }
    }

    private int dp(float val) {
        return Math.round(val * getResources().getDisplayMetrics().density);
    }

    private void showNewFolderDialog() {
        EditText input = new EditText(requireContext());
        input.setHint(R.string.files_new_folder_hint);
        input.setSingleLine(true);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.files_new_folder)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (!name.isEmpty()) {
                        File newDir = new File(mCurrentDirectory, name);
                        if (newDir.mkdir()) {
                            loadDirectory(mCurrentDirectory);
                        } else {
                            Toast.makeText(requireContext(), R.string.gamedir_open_failed, Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showRenameDialog(File file) {
        EditText input = new EditText(requireContext());
        input.setText(file.getName());
        input.setSelection(file.getName().length());
        input.setSingleLine(true);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.files_rename)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String newName = input.getText().toString().trim();
                    if (!newName.isEmpty() && !newName.equals(file.getName())) {
                        File dest = new File(file.getParentFile(), newName);
                        if (file.renameTo(dest)) {
                            loadDirectory(mCurrentDirectory);
                        } else {
                            Toast.makeText(requireContext(), R.string.gamedir_open_failed, Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showDeleteDialog(File file) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(file.getName())
                .setMessage(getString(R.string.files_delete_confirm, file.getName()))
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    FileUtils.deleteDirectory(file);
                    loadDirectory(mCurrentDirectory);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showFileContextMenu(File file) {
        CharSequence[] options = {
                getString(R.string.files_rename),
                getString(R.string.screenshot_share),
                getString(R.string.delete)
        };

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(file.getName())
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        showRenameDialog(file);
                    } else if (which == 1) {
                        Tools.openPath(requireContext(), file, true);
                    } else if (which == 2) {
                        showDeleteDialog(file);
                    }
                })
                .show();
    }

    private void openFile(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (name.endsWith(".txt") || name.endsWith(".log") || name.endsWith(".json")
                || name.endsWith(".cfg") || name.endsWith(".toml") || name.endsWith(".properties")
                || name.endsWith(".mcmeta")) {
            showTextViewerDialog(file);
        } else if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".webp")) {
            showImageViewerDialog(file);
        } else {
            try {
                Tools.openPath(requireContext(), file, false);
            } catch (Throwable t) {
                Toast.makeText(requireContext(), R.string.gamedir_open_failed, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showTextViewerDialog(File file) {
        ScrollView scrollView = new ScrollView(requireContext());
        TextView textView = new TextView(requireContext());
        textView.setTypeface(Typeface.MONOSPACE);
        textView.setTextSize(12);
        int pad = dp(16);
        textView.setPadding(pad, pad, pad, pad);
        textView.setTextColor(ThemeColors.get(requireContext(), com.google.android.material.R.attr.colorOnSurface));

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buf = new byte[Math.min((int) file.length(), 256 * 1024)];
            int read = fis.read(buf);
            textView.setText(new String(buf, 0, Math.max(0, read), StandardCharsets.UTF_8));
        } catch (IOException e) {
            textView.setText("Failed to read file: " + e.getMessage());
        }

        scrollView.addView(textView);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(file.getName())
                .setView(scrollView)
                .setPositiveButton(android.R.string.ok, null)
                .setNeutralButton(R.string.screenshot_share, (dialog, which) -> Tools.openPath(requireContext(), file, true))
                .show();
    }

    private void showImageViewerDialog(File file) {
        ImageView imageView = new ImageView(requireContext());
        imageView.setAdjustViewBounds(true);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int pad = dp(16);
        imageView.setPadding(pad, pad, pad, pad);

        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap);
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(file.getName())
                .setView(imageView)
                .setPositiveButton(android.R.string.ok, null)
                .setNeutralButton(R.string.screenshot_share, (dialog, which) -> Tools.openPath(requireContext(), file, true))
                .show();
    }

    private class FileAdapter extends RecyclerView.Adapter<FileViewHolder> {

        @NonNull
        @Override
        public FileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_file_card, parent, false);
            return new FileViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull FileViewHolder holder, int position) {
            File file = mEntries.get(position);
            holder.nameView.setText(file.getName());

            if (file.isDirectory()) {
                holder.iconView.setImageResource(R.drawable.ic_px_folder);
                File[] children = file.listFiles();
                int count = children != null ? children.length : 0;
                holder.detailsView.setText(getString(R.string.files_items_count, count));
            } else {
                String name = file.getName().toLowerCase(Locale.ROOT);
                if (name.endsWith(".jar")) {
                    holder.iconView.setImageResource(R.drawable.ic_px_java_run);
                } else if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".webp")) {
                    holder.iconView.setImageResource(R.drawable.ic_px_image);
                } else if (name.endsWith(".zip") || name.endsWith(".mrpack")) {
                    holder.iconView.setImageResource(R.drawable.ic_px_file_dl);
                } else if (name.endsWith(".log") || name.endsWith(".txt")) {
                    holder.iconView.setImageResource(R.drawable.ic_px_sharelog);
                } else {
                    holder.iconView.setImageResource(R.drawable.ic_px_file);
                }

                String sizeStr = Formatter.formatFileSize(requireContext(), file.length());
                String dateStr = mDateFormat.format(new Date(file.lastModified()));
                holder.detailsView.setText(sizeStr + " • " + dateStr);
            }

            holder.itemView.setOnClickListener(v -> {
                if (file.isDirectory()) {
                    loadDirectory(file);
                } else {
                    openFile(file);
                }
            });

            holder.moreView.setOnClickListener(v -> showFileContextMenu(file));
            holder.itemView.setOnLongClickListener(v -> {
                showFileContextMenu(file);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return mEntries.size();
        }
    }

    private static class FileViewHolder extends RecyclerView.ViewHolder {
        final ImageView iconView;
        final TextView nameView;
        final TextView detailsView;
        final ImageView moreView;

        FileViewHolder(@NonNull View itemView) {
            super(itemView);
            iconView = itemView.findViewById(R.id.file_item_icon);
            nameView = itemView.findViewById(R.id.file_item_name);
            detailsView = itemView.findViewById(R.id.file_item_details);
            moreView = itemView.findViewById(R.id.file_item_more);
        }
    }
}
