package com.linkdrone.remotecamera.fragment;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.linkdrone.remotecamera.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class MediaFragment extends Fragment implements
        ListView.OnItemClickListener, ListView.OnCreateContextMenuListener {
    private final static String TAG = "CameraFrag";
    private static final String SD_SLOT = "D:";
    private static final String SD_DIR = "/tmp/fuse_d/";
    private static final String HOME_DIR = "/tmp/fuse_d/";
    
    static private String mPWD;
    static private DentryAdapter mAdapter;
    private IFragmentListener mListener;
    private ListView mListView;
    private TextView mTextViewPath;


    public MediaFragment() {
        if (mPWD == null)
            mPWD = HOME_DIR;
    }

    public void reset() {
        mPWD = HOME_DIR;
        mAdapter = null;
    }
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_media, container, false);

        mTextViewPath = (TextView) view.findViewById(R.id.textViewDentryPath);

        // Setup the list view
        mListView = (ListView) view.findViewById(R.id.listViewDentryName);
        mListView.setOnItemClickListener(this);
        mListView.setOnCreateContextMenuListener(this);
        registerForContextMenu(mListView);
        if (mAdapter == null) {
            listDirContents(mPWD);
        } else {
            showDirContents();
        }

        return view;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            mListener = (IFragmentListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                + " must implement IFragmentListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }


    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (null != mListener) {
            Model item = (Model) parent.getItemAtPosition(position);
            if (item.isDirectory()) {
                mPWD += item.getName() + "/";
                listDirContents(mPWD);
            }
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view,
                                    ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, view, menuInfo);
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        Model model = mAdapter.getItem(info.position);
        if (model.isDirectory())
            return;

        MenuInflater inflater = getActivity().getMenuInflater();
        inflater.inflate(R.menu.menu_dentry, menu);
        String name = model.getName();
        int len = name.length();
        String surfix = name.substring(len-3, len).toLowerCase();
        if (!surfix.equals("jpg") && !surfix.equals("mp4")) {
            menu.removeItem(R.id.item_dentry_info);
            menu.removeItem(R.id.item_dentry_view);
            menu.removeItem(R.id.item_dentry_thumb);
        }
        if (!surfix.equals("bin")) {
            menu.removeItem(R.id.item_dentry_burning_fw);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info =
                (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        Model model = mAdapter.getItem(info.position);
        String path = mPWD + model.getName();
        switch (item.getItemId()) {
            case R.id.item_dentry_delete:
                mListener.onFragmentAction(IFragmentListener.ACTION_FS_DELETE, path);
                return true;
            case R.id.item_dentry_download:
                mListener.onFragmentAction(IFragmentListener.ACTION_FS_DOWNLOAD, path);
                return true;
            case R.id.item_dentry_info:
                mListener.onFragmentAction(IFragmentListener.ACTION_FS_INFO, path);
                return true;
            case R.id.item_dentry_view:
                mListener.onFragmentAction(IFragmentListener.ACTION_FS_VIEW, path);
                return true;
            case R.id.item_dentry_set_RO:
                mListener.onFragmentAction(IFragmentListener.ACTION_FS_SET_RO, path);
                return true;
            case R.id.item_dentry_set_WR:
                mListener.onFragmentAction(IFragmentListener.ACTION_FS_SET_WR, path);
                return true;
            case R.id.item_dentry_burning_fw:
                mListener.onFragmentAction(IFragmentListener.ACTION_FS_BURN_FW, path);
                break;
            case R.id.item_dentry_thumb:
                mListener.onFragmentAction(IFragmentListener.ACTION_FS_GET_THUMB, path);
                break;
        }
        return super.onContextItemSelected(item);
    }

    public void setEmptyText(CharSequence emptyText) {
        View emptyView = mListView.getEmptyView();

        if (emptyText instanceof TextView) {
            ((TextView) emptyView).setText(emptyText);
        }
    }

    public void refreshDirContents() {
        listDirContents(mPWD);
    }

    public void goParentDir() {
        int index = mPWD.lastIndexOf('/');
        if (index > 0) {
            index = mPWD.lastIndexOf('/', index - 1);
            mPWD = mPWD.substring(0, index + 1);
            listDirContents(mPWD);
        }        
    }
    
    public void formatSD() {
        mListener.onFragmentAction(IFragmentListener.ACTION_FS_FORMAT_SD, SD_SLOT);
    }
    
    public void showSD() {
        mPWD = SD_DIR;
        refreshDirContents();
    }
    
    public String getPWD() {
        return mPWD;
    }
    
    private void listDirContents(String path) {
        mListener.onFragmentAction(IFragmentListener.ACTION_FS_LS, path);
    }

    private void showDirContents() {
        mTextViewPath.setText("Directory: " + mPWD);
        mListView.setAdapter(mAdapter);
    }

    public void updateDirContents(JSONObject parser) {
        ArrayList<Model> models = new ArrayList<Model>();

        try {
            JSONArray contents = parser.getJSONArray("listing");

            for (int i = 0; i < contents.length(); i++) {
                models.add(new Model(contents.getJSONObject(i).toString()));
            }
        } catch (JSONException e) {
            Log.e(TAG, e.getMessage());
        }

        mAdapter = new DentryAdapter(models);
        showDirContents();
    }

    private class Model {
        private boolean isDirectory;
        private int size;
        private String name;
        private String time;

        public Model(String descriptor) {
            descriptor = descriptor.replaceAll("[{}\"]", "");

            // parse the name
            int index = descriptor.indexOf(':');
            name = descriptor.substring(0, index).trim();

            // figure out if this is file or directory
            isDirectory = name.endsWith("/");
            if (isDirectory)
                name = name.substring(0, name.length()-2);

            if (descriptor.contains("|")) {
                // get the size
                descriptor = descriptor.substring(index+1).trim();
                index = descriptor.indexOf(" ");
                size = Integer.parseInt(descriptor.substring(0, index));
                // get the time
                time = descriptor.substring(descriptor.indexOf('|')+1).trim();
            } else if (descriptor.contains("bytes")) {
                index = descriptor.indexOf(" ");
                size = Integer.parseInt(descriptor.substring(0, index));
                time = null;
            } else {
                size = -1;
                time = descriptor.substring(index+1).trim();
            }
        }

        public String getName() {
            return name;
        }

        public int getSize() {
            return size;
        }

        public String getTime() {
            return time;
        }

        public boolean isDirectory() {
            return isDirectory;
        }
    }

    private class DentryAdapter extends ArrayAdapter<Model> {
        final private ArrayList<Model> mArrayList;

        public DentryAdapter(ArrayList<Model> arrayList) {
            super(getActivity(), R.layout.listview_dentry, arrayList);
            mArrayList = arrayList;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LayoutInflater inflater = (LayoutInflater) getActivity()
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View view = inflater.inflate(R.layout.listview_dentry, parent, false);
            Model model = mArrayList.get(position);

            TextView nameView = (TextView) view.findViewById(R.id.textViewDentryName);
            nameView.setText(model.getName());

            TextView timeView = (TextView) view.findViewById(R.id.textViewDentryTime);
            timeView.setText(model.getTime());

            ImageView imageView = (ImageView) view.findViewById(R.id.imageViewDentryType);
            TextView sizeView = (TextView) view.findViewById(R.id.textViewDentrySize);
            if (model.isDirectory()) {
                imageView.setImageResource(R.drawable.ic_folder);
                sizeView.setVisibility(View.INVISIBLE);
            } else {
                imageView.setImageResource(R.drawable.ic_file);
                int size = model.getSize();
                if (size > 0)
                    sizeView.setText(Integer.toString(model.getSize()) + " bytes");
                else 
                    sizeView.setVisibility(View.INVISIBLE);
            }

            return view;
        }
    }
}
