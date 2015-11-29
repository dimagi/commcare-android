package org.commcare.android.adapters;

import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.commcare.android.view.SquareImageView;
import org.commcare.dalvik.R;

/**
 * @author Phillip Mates (pmates@dimagi.com).
 */
public class SquareButtonViewHolder extends RecyclerView.ViewHolder {
    public final SquareImageView imageView;
    public final RelativeLayout cardView;
    public final TextView textView;
    public final TextView subTextView;

    public SquareButtonViewHolder(View view) {
        super(view);

        cardView = (RelativeLayout)view.findViewById(R.id.card);
        imageView = (SquareImageView)view.findViewById(R.id.card_image);
        textView = (TextView)view.findViewById(R.id.card_text);
        subTextView = (TextView)view.findViewById(R.id.card_subtext);
    }
}
