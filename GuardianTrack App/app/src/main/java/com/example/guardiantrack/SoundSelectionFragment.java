package com.example.guardiantrack;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class SoundSelectionFragment extends DialogFragment {

    private static final int[] SOUND_RESOURCES = {R.raw.sound1, R.raw.sound2, R.raw.sound3};

    public interface OnSoundSelectedListener {
        void onSoundSelected(int soundResourceId);

        void onSoundSelected(Uri soundUri);
    }

    private OnSoundSelectedListener listener;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        try {
            listener = (OnSoundSelectedListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString() + " must implement OnSoundSelectedListener");
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
        builder.setTitle("Choose Alarm Sound");

        // Inflate layout containing RecyclerView
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.fragment_sound_selection, null);
        RecyclerView recyclerView = view.findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        SoundAdapter soundAdapter = new SoundAdapter(SOUND_RESOURCES); // Replace SOUND_RESOURCES with your list of sound resources
        recyclerView.setAdapter(soundAdapter);

        // Set up item click listener
        soundAdapter.setOnItemClickListener(new SoundAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(int position) {
                // Notify the listener (SettingsFragment) of the selected sound
                if (listener != null) {
                    listener.onSoundSelected(SOUND_RESOURCES[position]); // Replace SOUND_RESOURCES with your list of sound resources
                }
                dismiss();
            }
        });

        builder.setView(view);
        return builder.create();
    }
}
