package com.example.guardiantrack;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

public class SoundAdapter extends RecyclerView.Adapter<SoundAdapter.SoundViewHolder> {

    private int[] soundResources;
    private OnItemClickListener listener;

    public SoundAdapter(int[] soundResources) {
        this.soundResources = soundResources;
    }

    public interface OnItemClickListener {
        void onItemClick(int position);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public SoundViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_sound, parent, false);
        return new SoundViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SoundViewHolder holder, int position) {
        holder.textViewSoundName.setText("Sound " + (position + 1));
    }

    @Override
    public int getItemCount() {
        return soundResources.length;
    }

    public class SoundViewHolder extends RecyclerView.ViewHolder {

        public TextView textViewSoundName;

        public SoundViewHolder(@NonNull View itemView) {
            super(itemView);
            textViewSoundName = itemView.findViewById(R.id.textViewSoundName);

            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listener != null) {
                        int position = getAdapterPosition();
                        if (position != RecyclerView.NO_POSITION) {
                            listener.onItemClick(position);
                        }
                    }
                }
            });
        }
    }
}


