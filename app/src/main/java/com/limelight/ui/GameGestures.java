package com.limelight.ui;

import com.limelight.binding.input.GameInputDevice;

public interface GameGestures {
    void toggleKeyboard();

    default void showGameMenu(GameInputDevice device){};

    // Show or hide the performance overlay in the requested mode (lite or full)
    default void togglePerformanceOverlay(boolean lite){};
}
