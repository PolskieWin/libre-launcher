package git.artdeell.mojo.modding.forge;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ExpandableListAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

public class ForgelikeVersionListAdapter extends BaseExpandableListAdapter implements ExpandableListAdapter {
    private final List<String> mGameVersions;
    private final List<List<String>> mLoaderVersions;
    private final LayoutInflater mLayoutInflater;

    public ForgelikeVersionListAdapter(List<String> forgeVersions, LayoutInflater layoutInflater, ForgelikeUtils utils) {
        this.mLayoutInflater = layoutInflater;
        java.util.Map<String, List<String>> versionMap = new java.util.LinkedHashMap<>();
        for(String version : forgeVersions) {
            if(utils.shouldSkipVersion(version)) continue;
            String gameVersion = utils.processVersionString(version);
            List<String> list = versionMap.get(gameVersion);
            if (list == null) {
                list = new ArrayList<>();
                versionMap.put(gameVersion, list);
            }
            list.add(version);
        }

        mGameVersions = new ArrayList<>(versionMap.keySet());
        mGameVersions.sort((v1, v2) -> {
            git.artdeell.mojo.modding.ComparableVersionString cv1 = git.artdeell.mojo.modding.ComparableVersionString.parse(v1);
            git.artdeell.mojo.modding.ComparableVersionString cv2 = git.artdeell.mojo.modding.ComparableVersionString.parse(v2);
            return cv2.compareTo(cv1);
        });

        mLoaderVersions = new ArrayList<>(mGameVersions.size());
        for (String gv : mGameVersions) {
            List<String> subList = versionMap.get(gv);
            if (subList != null) {
                reverseList(subList);
                mLoaderVersions.add(subList);
            }
        }
    }

    @Override
    public int getGroupCount() {
        return mGameVersions.size();
    }

    @Override
    public int getChildrenCount(int i) {
        return mLoaderVersions.get(i).size();
    }

    @Override
    public Object getGroup(int i) {
        return getGameVersion(i);
    }

    @Override
    public Object getChild(int i, int i1) {
        return getForgeVersion(i, i1);
    }

    @Override
    public long getGroupId(int i) {
        return i;
    }

    @Override
    public long getChildId(int i, int i1) {
        return i1;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public View getGroupView(int i, boolean b, View convertView, ViewGroup viewGroup) {
        if(convertView == null)
            convertView = mLayoutInflater.inflate(android.R.layout.simple_expandable_list_item_1, viewGroup, false);

        ((TextView) convertView).setText(getGameVersion(i));

        return convertView;
    }

    @Override
    public View getChildView(int i, int i1, boolean b, View convertView, ViewGroup viewGroup) {
        if(convertView == null)
            convertView = mLayoutInflater.inflate(android.R.layout.simple_expandable_list_item_1, viewGroup, false);
        ((TextView) convertView).setText(getForgeVersion(i, i1));
        return convertView;
    }

    private String getGameVersion(int i) {
        return mGameVersions.get(i);
    }

    private String getForgeVersion(int i, int i1){
        return mLoaderVersions.get(i).get(i1);
    }

    @Override
    public boolean isChildSelectable(int i, int i1) {
        return true;
    }

    private static <T> void reverseList(List<T> list) {
        for (int i = 0, j = list.size() - 1; i < j; i++, j--) {
            T temp = list.get(i);
            list.set(i, list.get(j));
            list.set(j, temp);
        }
    }
}
