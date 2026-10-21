package com.mansoority.shadow.client;

import com.mansoority.shadow.ShadowConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleFunction;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;

/** Mods list -> Mansoority -> Config: everything that used to need the toml file */
public class ShadowConfigScreen extends Screen {
    private final Screen parent;
    private final List<Runnable> apply = new ArrayList<>();

    public ShadowConfigScreen(Screen parent) {
        super(Component.literal("Mansoority").withStyle(ChatFormatting.DARK_RED));
        this.parent = parent;
    }

    @Override
    protected void init() {
        apply.clear();
        int w = 150, gap = 6, colL = this.width / 2 - w - gap / 2, colR = this.width / 2 + gap / 2;
        int y = 42;
        toggle(colL, y, w, "Encounters", ShadowConfig.ENABLED);
        slider(colR, y, w, "Minutes between", ShadowConfig.MIN_MINUTES.get(), 5, 600, v -> String.valueOf((int) v),
                v -> ShadowConfig.MIN_MINUTES.set((int) Math.round(v)));
        y += 24;
        slider(colL, y, w, "Chance / minute", ShadowConfig.CHANCE.get() * 100, 0, 10, v -> String.format("%.1f%%", v),
                v -> ShadowConfig.CHANCE.set(v / 100.0));
        slider(colR, y, w, "Max players near", ShadowConfig.MAX_WITNESSES.get(), 1, 10, v -> String.valueOf((int) v),
                v -> ShadowConfig.MAX_WITNESSES.set((int) Math.round(v)));
        y += 24;
        slider(colL, y, w, "Catch damage", ShadowConfig.CATCH_DAMAGE.get(), 0, 10, v -> String.format("%.1f hearts", v / 2),
                v -> ShadowConfig.CATCH_DAMAGE.set(Math.round(v * 2) / 2.0));
        y += 34;
        Object[][] events = {{"Stalking", ShadowConfig.STALK}, {"Mimic", ShadowConfig.MIMIC}, {"Grab", ShadowConfig.GRAB},
                {"Small scares", ShadowConfig.SCARES}, {"Jumpscares", ShadowConfig.JUMPSCARE}, {"Insomnia visit", ShadowConfig.BEDSIDE},
                {"Lair encounter", ShadowConfig.LAIR}, {"End boss", ShadowConfig.BOSS}};
        for (int i = 0; i < events.length; i++) {
            @SuppressWarnings("unchecked") ForgeConfigSpec.BooleanValue v = (ForgeConfigSpec.BooleanValue) events[i][1];
            toggle(i % 2 == 0 ? colL : colR, y, w, (String) events[i][0], v);
            if (i % 2 == 1) y += 24;
        }
        this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(this.width / 2 - 100, this.height - 28, 200, 20).build());
    }

    private void toggle(int x, int y, int w, String label, ForgeConfigSpec.BooleanValue val) {
        final boolean[] cur = {val.get()};
        this.addRenderableWidget(CycleButton.onOffBuilder(cur[0]).create(x, y, w, 20, Component.literal(label), (b, v) -> cur[0] = v));
        apply.add(() -> val.set(cur[0]));
    }

    private void slider(int x, int y, int w, String label, double value, double min, double max, DoubleFunction<String> fmt,
                        java.util.function.DoubleConsumer set) {
        final double[] cur = {value};
        AbstractSliderButton s = new AbstractSliderButton(x, y, w, 20, Component.empty(), (value - min) / (max - min)) {
            { updateMessage(); }
            @Override protected void updateMessage() { setMessage(Component.literal(label + ": " + fmt.apply(min + this.value * (max - min)))); }
            @Override protected void applyValue() { cur[0] = min + this.value * (max - min); }
        };
        this.addRenderableWidget(s);
        apply.add(() -> set.accept(cur[0]));
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        this.renderBackground(g);
        g.drawCenteredString(this.font, this.title, this.width / 2, 14, 0xFFFFFF);
        g.drawCenteredString(this.font, Component.literal("It could be anyone.").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), this.width / 2, 26, 0xFFFFFF);
        g.drawCenteredString(this.font, Component.literal("Events").withStyle(ChatFormatting.GRAY), this.width / 2, 42 + 24 * 3 - 4, 0xFFFFFF);
        g.drawCenteredString(this.font, Component.literal("On a server these come from the server's config/theshadow-common.toml").withStyle(ChatFormatting.DARK_GRAY),
                this.width / 2, this.height - 42, 0xFFFFFF);
        super.render(g, mx, my, pt);
    }

    @Override
    public void onClose() {
        for (Runnable r : apply) r.run();
        ShadowConfig.SPEC.save();
        this.minecraft.setScreen(parent);
    }
}
