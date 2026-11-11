package org.commcare.fragments;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import org.commcare.activities.CommCareWiFiDirectActivity;
import org.commcare.dalvik.R;

@SuppressLint("NewApi")
public class FileServerFragment extends Fragment {

    private TextView mStatusText;
    private View mView;

    private FileServerViewModel viewModel;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(FileServerViewModel.class);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View contentView;
        contentView = inflater.inflate(R.layout.file_server, null);

        mStatusText = contentView.findViewById(R.id.file_server_status_text);

        mView = contentView.findViewById(R.id.file_server_view);

        return contentView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mView.setVisibility(viewModel.isRunning() ? View.VISIBLE : View.GONE);

        viewModel.getStatusText().observe(getViewLifecycleOwner(), mStatusText::setText);
        viewModel.getReceivedZipPaths().observe(getViewLifecycleOwner(), paths -> {
            CommCareWiFiDirectActivity activity = (CommCareWiFiDirectActivity)requireActivity();
            for (String path : paths) {
                viewModel.onReceivedZipHandled(path);
                activity.onFormsCopied(path);
            }
        });
    }

    public interface FileServerListener {
        void onFormsCopied(String result);
    }

    public void startServer(String receiveZipDirectory) {
        mView.setVisibility(View.VISIBLE);
        viewModel.startServer(receiveZipDirectory);
    }
}
