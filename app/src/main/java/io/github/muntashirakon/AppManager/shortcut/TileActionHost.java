// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.AppManager.shortcut;

import android.content.Intent;

import androidx.annotation.NonNull;

/**
 * What a Quick Settings tile's action needs from whatever runs it: the tile itself on an unlocked phone, or
 * {@link TileActionActivity} once a locked phone has been unlocked.
 */
public interface TileActionHost {
    /** Shows one of the {@link android.service.quicksettings.Tile} states on the tile. */
    void showTileState(int state);

    /** Opens a screen, closing the Quick Settings panel if it's open. */
    void openScreen(@NonNull Intent intent);
}
