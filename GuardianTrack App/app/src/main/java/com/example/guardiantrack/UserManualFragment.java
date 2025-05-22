package com.example.guardiantrack;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.github.barteksc.pdfviewer.PDFView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class UserManualFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_user_manual, container, false);

        PDFView pdfView = rootView.findViewById(R.id.pdfView);

        // Load PDF from assets
        try {
            pdfView.fromAsset("usermanual.pdf").load();
        } catch (Exception e) {
            e.printStackTrace();
            // Handle exception
            Toast.makeText(requireContext(), "Failed to load PDF", Toast.LENGTH_SHORT).show();
        }

        return rootView;
    }
}

