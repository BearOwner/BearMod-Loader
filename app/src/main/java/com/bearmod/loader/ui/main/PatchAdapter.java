package com.bearmod.loader.ui.main;

import android.content.Context;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.bearmod.loader.R;
import com.bearmod.loader.databinding.ItemPatchBinding;
import com.bearmod.loader.model.Patch;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Adapter for displaying patches in a RecyclerView
 */
public class PatchAdapter extends RecyclerView.Adapter<PatchAdapter.PatchViewHolder> {

    private final List<Patch> patches;
    private final Context context;
    private final Consumer<Patch> onPatchClick;
    private final Consumer<Patch> onApplyClick;

    /**
     * Constructor
     * @param context Context
     * @param onPatchClick Consumer for patch click events
     * @param onApplyClick Consumer for apply patch click events
     */
    public PatchAdapter(Context context, Consumer<Patch> onPatchClick, Consumer<Patch> onApplyClick) {
        this.context = context;
        this.patches = new ArrayList<>();
        this.onPatchClick = onPatchClick;
        this.onApplyClick = onApplyClick;
    }

    @NonNull
    @Override
    public PatchViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemPatchBinding binding = ItemPatchBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new PatchViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull PatchViewHolder holder, int position) {
        Patch patch = patches.get(position);
        holder.bind(patch);
    }

    @Override
    public int getItemCount() {
        return patches.size();
    }

    /**
     * Update patches list using DiffUtil for efficient updates
     * @param newPatches New patches list
     */
    public void updatePatches(List<Patch> newPatches) {
        // Create a copy of the current list
        List<Patch> oldPatches = new ArrayList<>(patches);

        // Calculate the difference between old and new lists
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(
                new PatchDiffCallback(oldPatches, newPatches));

        // Update data
        patches.clear();
        patches.addAll(newPatches);

        // Dispatch updates to adapter
        diffResult.dispatchUpdatesTo(this);
    }

    /**
     * DiffUtil callback for comparing patch lists
     */
    private static class PatchDiffCallback extends DiffUtil.Callback {
        private final List<Patch> oldList;
        private final List<Patch> newList;

        PatchDiffCallback(List<Patch> oldList, List<Patch> newList) {
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
            Patch oldPatch = oldList.get(oldItemPosition);
            Patch newPatch = newList.get(newItemPosition);

            return oldPatch.getName().equals(newPatch.getName()) &&
                   oldPatch.getDescription().equals(newPatch.getDescription()) &&
                   oldPatch.getGameVersion().equals(newPatch.getGameVersion()) &&
                   oldPatch.getUpdateDate().equals(newPatch.getUpdateDate()) &&
                   oldPatch.getStatus() == newPatch.getStatus();
        }
    }

    /**
     * ViewHolder for patch items
     */
    class PatchViewHolder extends RecyclerView.ViewHolder {

        private final ItemPatchBinding binding;

        /**
         * Constructor
         * @param binding View binding
         */
        PatchViewHolder(ItemPatchBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        /**
         * Bind patch data to views
         * @param patch Patch to bind
         */
        void bind(Patch patch) {
            // Set patch details
            binding.tvPatchName.setText(patch.getName());
            binding.tvPatchDescription.setText(patch.getDescription());
            binding.tvGameVersion.setText(context.getString(R.string.game_version, patch.getGameVersion()));
            binding.tvUpdateDate.setText(context.getString(R.string.updated, patch.getUpdateDate()));

            // Set status
            switch (patch.getStatus()) {
                case UP_TO_DATE:
                    binding.tvStatus.setText(R.string.up_to_date);
                    binding.tvStatus.setBackgroundTintList(
                            ContextCompat.getColorStateList(context, R.color.success));
                    break;
                case UPDATE_AVAILABLE:
                    binding.tvStatus.setText(R.string.update_available);
                    binding.tvStatus.setBackgroundTintList(
                            ContextCompat.getColorStateList(context, R.color.warning));
                    break;
                case NOT_INSTALLED:
                    binding.tvStatus.setText(R.string.not_installed);
                    binding.tvStatus.setBackgroundTintList(
                            ContextCompat.getColorStateList(context, R.color.info));
                    break;
            }

            // Make sure progress bar is initially hidden
            binding.progressBar.setVisibility(View.GONE);

            // Set apply button click listener with improved UI feedback
            binding.btnApplyPatch.setOnClickListener(v -> {
                if (onApplyClick != null) {
                    // Show progress indicator before applying the patch
                    binding.progressBar.setVisibility(View.VISIBLE);
                    binding.btnApplyPatch.setEnabled(false);

                    // Add a small delay to show the progress indicator
                    new Handler().postDelayed(() -> {
                        // Hide progress indicator
                        binding.progressBar.setVisibility(View.GONE);
                        binding.btnApplyPatch.setEnabled(true);

                        // Call the consumer
                        onApplyClick.accept(patch);
                    }, 800); // Short delay for visual feedback
                }
            });

            // Set item click listener
            itemView.setOnClickListener(v -> {
                if (onPatchClick != null) {
                    onPatchClick.accept(patch);
                }
            });
        }
    }

    // PatchClickListener interface removed in favor of Consumer<Patch>
}
