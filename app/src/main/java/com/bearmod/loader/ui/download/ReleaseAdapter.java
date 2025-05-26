package com.bearmod.loader.ui.download;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.bearmod.loader.R;
import com.bearmod.loader.model.PatchRelease;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter for displaying patch releases in a RecyclerView
 */
public class ReleaseAdapter extends RecyclerView.Adapter<ReleaseAdapter.ReleaseViewHolder> {

    private final Context context;
    private final List<PatchRelease> releases;
    private final ReleaseClickListener listener;

    /**
     * Constructor
     * @param context Context
     * @param listener Release click listener
     */
    public ReleaseAdapter(Context context, ReleaseClickListener listener) {
        this.context = context;
        this.releases = new ArrayList<>();
        this.listener = listener;
    }

    @NonNull
    @Override
    public ReleaseViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_release, parent, false);
        return new ReleaseViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ReleaseViewHolder holder, int position) {
        PatchRelease release = releases.get(position);
        holder.bind(release);
    }

    @Override
    public int getItemCount() {
        return releases.size();
    }

    /**
     * Update releases list using DiffUtil for efficient updates
     * @param newReleases New releases list
     */
    public void updateReleases(List<PatchRelease> newReleases) {
        // Create a copy of the current list
        List<PatchRelease> oldReleases = new ArrayList<>(releases);

        // Calculate the difference between old and new lists
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(
                new ReleaseDiffCallback(oldReleases, newReleases));

        // Update data
        releases.clear();
        releases.addAll(newReleases);

        // Dispatch updates to adapter
        diffResult.dispatchUpdatesTo(this);
    }

    /**
     * DiffUtil callback for comparing release lists
     */
    private static class ReleaseDiffCallback extends DiffUtil.Callback {
        private final List<PatchRelease> oldList;
        private final List<PatchRelease> newList;

        ReleaseDiffCallback(List<PatchRelease> oldList, List<PatchRelease> newList) {
            this.oldList = oldList;
            this.newList = newList;
        }

        @Override
        public int getOldListSize() {
            return oldList.size();
        }

        @Override
        public int getNewListSize() {
            return newList.size();
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            // Items are the same if they have the same ID
            return oldList.get(oldItemPosition).getId().equals(
                    newList.get(newItemPosition).getId());
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            // Compare all relevant fields to determine if contents are the same
            PatchRelease oldRelease = oldList.get(oldItemPosition);
            PatchRelease newRelease = newList.get(newItemPosition);

            return oldRelease.getName().equals(newRelease.getName()) &&
                   oldRelease.getDescription().equals(newRelease.getDescription()) &&
                   oldRelease.getGameVersion().equals(newRelease.getGameVersion()) &&
                   oldRelease.getReleaseDate().equals(newRelease.getReleaseDate()) &&
                   oldRelease.getDownloadUrl().equals(newRelease.getDownloadUrl());
        }
    }

    /**
     * ViewHolder for release items
     */
    class ReleaseViewHolder extends RecyclerView.ViewHolder {

        private final MaterialCardView cardRelease;
        private final TextView tvReleaseName;
        private final TextView tvReleaseVersion;
        private final TextView tvReleaseDescription;
        private final com.google.android.material.chip.Chip chipGameVersion;
        private final com.google.android.material.chip.Chip chipReleaseDate;
        private final com.google.android.material.chip.Chip chipFileSize;
        private final com.google.android.material.chip.Chip chipStatus;
        private final View rippleEffect;

        /**
         * Constructor
         * @param itemView Item view
         */
        ReleaseViewHolder(@NonNull View itemView) {
            super(itemView);

            cardRelease = itemView.findViewById(R.id.card_release);
            tvReleaseName = itemView.findViewById(R.id.tv_release_name);
            tvReleaseVersion = itemView.findViewById(R.id.tv_release_version);
            tvReleaseDescription = itemView.findViewById(R.id.tv_release_description);
            chipGameVersion = itemView.findViewById(R.id.chip_game_version);
            chipReleaseDate = itemView.findViewById(R.id.chip_release_date);
            chipFileSize = itemView.findViewById(R.id.chip_file_size);
            chipStatus = itemView.findViewById(R.id.chip_status);
            rippleEffect = itemView.findViewById(R.id.ripple_effect);
        }

        /**
         * Bind release data to views
         * @param release Release to bind
         */
        void bind(PatchRelease release) {
            // Set main text values
            tvReleaseName.setText(release.getName());
            tvReleaseVersion.setText("Version " + release.getGameVersion());
            tvReleaseDescription.setText(release.getDescription());

            // Set chip values with modern styling
            chipGameVersion.setText("Game v" + release.getGameVersion());
            chipReleaseDate.setText(release.getReleaseDate());

            // Set file size chip (you may need to add size info to PatchRelease)
            chipFileSize.setText("Size: N/A"); // Placeholder - update when size info is available

            // Set status chip based on download state
            chipStatus.setText(context.getString(R.string.available));
            chipStatus.setChipBackgroundColorResource(R.color.primary_light);
            chipStatus.setTextColor(context.getResources().getColor(R.color.primary, null));

            // Set click listener with ripple effect and haptic feedback
            rippleEffect.setOnClickListener(v -> {
                if (listener != null) {
                    // Provide haptic feedback for better UX
                    v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);

                    // Add subtle animation
                    cardRelease.animate()
                            .scaleX(0.95f)
                            .scaleY(0.95f)
                            .setDuration(100)
                            .withEndAction(() -> {
                                cardRelease.animate()
                                        .scaleX(1.0f)
                                        .scaleY(1.0f)
                                        .setDuration(100)
                                        .start();
                            })
                            .start();

                    // Notify listener
                    listener.onReleaseClick(release);
                }
            });

            // Also set card click listener for backward compatibility
            cardRelease.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onReleaseClick(release);
                }
            });
        }
    }

    /**
     * Release click listener interface
     */
    public interface ReleaseClickListener {
        void onReleaseClick(PatchRelease release);
    }
}
