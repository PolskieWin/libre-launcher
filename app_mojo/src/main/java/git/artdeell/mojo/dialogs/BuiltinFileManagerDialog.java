package git.artdeell.mojo.dialogs;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import git.artdeell.mojo.R;
import git.artdeell.mojo.Tools;
import git.artdeell.mojo.fragments.FileExplorerFragment;

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

public class BuiltinFileManagerDialog {

    public static void show(Context context, File initialDirectory) {
        if (initialDirectory == null || !initialDirectory.exists()) {
            Toast.makeText(context, R.string.gamedir_open_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        FragmentActivity activity = getFragmentActivity(context);
        if (activity != null) {
            Bundle bundle = new Bundle();
            bundle.putString(FileExplorerFragment.EXTRA_INITIAL_PATH, initialDirectory.getAbsolutePath());
            bundle.putString(FileExplorerFragment.EXTRA_ROOT_PATH, initialDirectory.getAbsolutePath());
            Tools.swapFragmentFullScreen(activity, FileExplorerFragment.class, FileExplorerFragment.TAG, bundle);
            return;
        }

        FileBrowserView browserView = new FileBrowserView(context, initialDirectory);
        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.mcl_button_open_directory))
                .setView(browserView)
                .setPositiveButton(android.R.string.ok, null)
                .create();

        browserView.setDialog(dialog);
        dialog.show();
    }

    private static FragmentActivity getFragmentActivity(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof FragmentActivity) {
                return (FragmentActivity) context;
            }
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }

    private static class FileBrowserView extends RecyclerView {
        private final Context mContext;
        private final File mRootDirectory;
        private File mCurrentDirectory;
        private final List<File> mEntries = new ArrayList<>();
        private final FileAdapter mAdapter;
        private AlertDialog mDialog;

        public FileBrowserView(Context context, File rootDirectory) {
            super(context);
            mContext = context;
            mRootDirectory = rootDirectory;
            mCurrentDirectory = rootDirectory;

            setLayoutManager(new LinearLayoutManager(context));
            mAdapter = new FileAdapter();
            setAdapter(mAdapter);

            int padding = (int) (12 * context.getResources().getDisplayMetrics().density);
            setPadding(padding, padding / 2, padding, padding / 2);

            loadDirectory(mCurrentDirectory);
        }

        public void setDialog(AlertDialog dialog) {
            this.mDialog = dialog;
            updateTitle();
        }

        private void updateTitle() {
            if (mDialog != null) {
                String relPath = mCurrentDirectory.getAbsolutePath();
                String rootPath = mRootDirectory.getAbsolutePath();
                if (relPath.startsWith(rootPath)) {
                    relPath = relPath.substring(rootPath.length());
                    if (relPath.isEmpty()) relPath = "/";
                }
                mDialog.setTitle(mRootDirectory.getName() + ": " + relPath);
            }
        }

        private void loadDirectory(File dir) {
            mCurrentDirectory = dir;
            updateTitle();
            mEntries.clear();

            // Add parent navigation if not at root
            if (!mCurrentDirectory.equals(mRootDirectory) && mCurrentDirectory.getParentFile() != null) {
                mEntries.add(new File(mCurrentDirectory, ".."));
            }

            File[] files = mCurrentDirectory.listFiles();
            if (files != null) {
                List<File> list = Arrays.asList(files);
                Collections.sort(list, (f1, f2) -> {
                    if (f1.isDirectory() && !f2.isDirectory()) return -1;
                    if (!f1.isDirectory() && f2.isDirectory()) return 1;
                    return f1.getName().compareToIgnoreCase(f2.getName());
                });
                mEntries.addAll(list);
            }

            mAdapter.notifyDataSetChanged();
        }

        private class FileAdapter extends RecyclerView.Adapter<FileViewHolder> {

            @NonNull
            @Override
            public FileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v = LayoutInflater.from(mContext).inflate(R.layout.item_simple_list_1, parent, false);
                return new FileViewHolder(v);
            }

            @Override
            public void onBindViewHolder(@NonNull FileViewHolder holder, int position) {
                File file = mEntries.get(position);
                boolean isUp = file.getName().equals("..");
                boolean isDir = isUp || file.isDirectory();

                if (isUp) {
                    holder.title.setText(".. (Parent Directory)");
                } else {
                    String sizeText;
                    if (isDir) {
                        File[] sub = file.listFiles();
                        int count = sub == null ? 0 : sub.length;
                        sizeText = count + (count == 1 ? " item" : " items");
                    } else {
                        sizeText = Formatter.formatFileSize(mContext, file.length());
                    }
                    holder.title.setText(String.format("%s  •  %s", file.getName(), sizeText));
                }

                holder.itemView.setOnClickListener(v -> {
                    if (isUp) {
                        File parent = mCurrentDirectory.getParentFile();
                        if (parent != null) loadDirectory(parent);
                    } else if (file.isDirectory()) {
                        loadDirectory(file);
                    } else {
                        openFile(file);
                    }
                });

                holder.itemView.setOnLongClickListener(v -> {
                    if (!isUp) {
                        showFileActions(file);
                    }
                    return true;
                });
            }

            @Override
            public int getItemCount() {
                return mEntries.size();
            }
        }

        private static class FileViewHolder extends RecyclerView.ViewHolder {
            final TextView title;
            public FileViewHolder(View v) {
                super(v);
                title = (TextView) v;
            }
        }

        private void openFile(File file) {
            String name = file.getName().toLowerCase(Locale.ROOT);
            if (name.endsWith(".txt") || name.endsWith(".json") || name.endsWith(".properties")
                    || name.endsWith(".log") || name.endsWith(".cfg") || name.endsWith(".toml")
                    || name.endsWith(".yml") || name.endsWith(".yaml") || name.endsWith(".ini")
                    || name.endsWith(".csv")) {
                viewTextFile(file);
            } else {
                new MaterialAlertDialogBuilder(mContext)
                        .setTitle(file.getName())
                        .setMessage(String.format("Size: %s\nPath: %s",
                                Formatter.formatFileSize(mContext, file.length()),
                                file.getAbsolutePath()))
                        .setPositiveButton(android.R.string.ok, null)
                        .setNeutralButton(R.string.delete, (d, w) -> deleteFileConfirm(file))
                        .show();
            }
        }

        private void viewTextFile(File file) {
            try {
                if (file.length() > 2 * 1024 * 1024) {
                    Toast.makeText(mContext, "File too large to preview directly", Toast.LENGTH_SHORT).show();
                    return;
                }
                byte[] bytes = new byte[(int) file.length()];
                try (FileInputStream fis = new FileInputStream(file)) {
                    int read = fis.read(bytes);
                    if (read < 0) bytes = new byte[0];
                }
                String content = new String(bytes, StandardCharsets.UTF_8);

                ScrollView sv = new ScrollView(mContext);
                TextView tv = new TextView(mContext);
                tv.setText(content);
                tv.setTextIsSelectable(true);
                tv.setTypeface(android.graphics.Typeface.MONOSPACE);
                int pad = (int) (14 * mContext.getResources().getDisplayMetrics().density);
                tv.setPadding(pad, pad, pad, pad);
                sv.addView(tv);

                new MaterialAlertDialogBuilder(mContext)
                        .setTitle(file.getName())
                        .setView(sv)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            } catch (IOException e) {
                Toast.makeText(mContext, "Failed to read file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }

        private void showFileActions(File file) {
            String[] options = new String[]{"Rename", "Delete"};
            new MaterialAlertDialogBuilder(mContext)
                    .setTitle(file.getName())
                    .setItems(options, (dialog, which) -> {
                        if (which == 0) {
                            renameFileDialog(file);
                        } else if (which == 1) {
                            deleteFileConfirm(file);
                        }
                    })
                    .show();
        }

        private void renameFileDialog(File file) {
            EditText input = new EditText(mContext);
            input.setText(file.getName());
            input.setSelectAllOnFocus(true);
            new MaterialAlertDialogBuilder(mContext)
                    .setTitle("Rename")
                    .setView(input)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                        String newName = input.getText().toString().trim();
                        if (!newName.isEmpty() && !newName.equals(file.getName())) {
                            File target = new File(file.getParentFile(), newName);
                            if (file.renameTo(target)) {
                                loadDirectory(mCurrentDirectory);
                            } else {
                                Toast.makeText(mContext, "Failed to rename", Toast.LENGTH_SHORT).show();
                            }
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        }

        private void deleteFileConfirm(File file) {
            new MaterialAlertDialogBuilder(mContext)
                    .setTitle(R.string.delete)
                    .setMessage("Are you sure you want to delete " + file.getName() + "?")
                    .setPositiveButton(R.string.delete, (dialog, which) -> {
                        deleteRecursive(file);
                        loadDirectory(mCurrentDirectory);
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        }

        private boolean deleteRecursive(File f) {
            if (f.isDirectory()) {
                File[] children = f.listFiles();
                if (children != null) {
                    for (File c : children) deleteRecursive(c);
                }
            }
            return f.delete();
        }
    }
}
