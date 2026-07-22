package com.tiertagger.config;

import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.Screen;

public class AdvancedConfigScreen extends AdvancedConfigScreenBase {
    public AdvancedConfigScreen(Screen parent) {
        super(parent);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        if (handleScrollbarDragged(click.y())) {
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        finishScrollbarDrag();
        return super.mouseReleased(click);
    }
}
