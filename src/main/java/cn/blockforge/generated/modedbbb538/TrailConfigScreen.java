package cn.blockforge.generated.modedbbb538;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * 游戏内配置界面：左侧分类页签，右侧选项行（开关 / 滑条 / 循环按钮）。
 * 数值改动即时生效并写回配置文件；联机时"触发条件"页只读。
 * 游戏内按 K（可改键）打开。
 */
public final class TrailConfigScreen extends Screen {
    private static final int OVERLAY = 0x9004090F;
    private static final int PANEL_BG = 0xF20D1524;
    private static final int PANEL_BORDER = 0xFF27496E;
    private static final int ACCENT = 0xFF66CCFF;
    private static final int ACCENT_SOFT = 0xFF9FDCFF;
    private static final int ACCENT_DARK = 0xFF2E7FB8;
    private static final int TEXT = 0xFFE9F3FC;
    private static final int TEXT_DIM = 0xFF8FA6BF;
    private static final int WARN = 0xFFFFB86B;
    private static final int ROW_HOVER = 0x12FFFFFF;
    private static final int SLOT_BG = 0xFF18263A;
    private static final int SLOT_BG_HOVER = 0xFF223550;
    private static final int TRACK = 0xFF2A3B58;
    private static final int DISABLED_BG = 0xFF232D3E;

    private static final int ROW_HEIGHT = 26;
    private static final int SLIDER_W = 124;
    private static final int VALUE_W = 42;
    private static final int DOT_COUNT = 26;

    private enum Tab { VISUALS, AUDIO, PERFORMANCE, ACTIVATION }

    private static final class Row {
        final Component label;
        final Component tip;
        final OptionWidget control;
        final Supplier<String> valueText;
        int y;

        Row(Component label, Component tip, OptionWidget control, Supplier<String> valueText) {
            this.label = label;
            this.tip = tip;
            this.control = control;
            this.valueText = valueText;
        }
    }

    private Tab tab = Tab.VISUALS;
    private final List<Row> rows = new ArrayList<>();
    private final List<TabButton> tabButtons = new ArrayList<>();
    private final List<FooterButton> footerButtons = new ArrayList<>();
    private int panelX, panelY, panelW, panelH;
    private int contentX, contentRight, contentTop, contentBottom;
    private int scrollY, maxScroll;
    private boolean activationUnloaded;
    private boolean activationReadOnly;

    private final float[] dotSeedX = new float[DOT_COUNT];
    private final float[] dotSeedY = new float[DOT_COUNT];
    private final float[] dotSpeed = new float[DOT_COUNT];
    private final float[] dotPhase = new float[DOT_COUNT];

    private final Screen parent;

    public TrailConfigScreen() {
        this(null);
    }

    public TrailConfigScreen(Screen parent) {
        super(Component.translatable("config.mod_edbbb538.title"));
        this.parent = parent;
        Random random = new Random(20261016L);
        for (int i = 0; i < DOT_COUNT; i++) {
            dotSeedX[i] = random.nextFloat();
            dotSeedY[i] = random.nextFloat();
            dotSpeed[i] = 0.010F + random.nextFloat() * 0.022F;
            dotPhase[i] = random.nextFloat() * 6.2832F;
        }
    }

    private static Component tr(String key) {
        return Component.translatable("config.mod_edbbb538." + key);
    }

    @Override
    protected void init() {
        clearWidgets();
        rows.clear();

        panelW = Math.min(width - 24, 470);
        panelH = Math.min(height - 16, 240);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        contentX = panelX + 108;
        contentRight = panelX + panelW - 14;
        contentTop = panelY + 40;
        contentBottom = panelY + panelH - 30;
        scrollY = 0;

        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            tabButtons.add(new TabButton(panelX + 8, panelY + 36 + i * 21, 92, 18, tabs[i]));
        }
        for (TabButton button : tabButtons) {
            addRenderableWidget(button);
        }
        footerButtons.clear();
        footerButtons.add(new FooterButton(panelX + 12, panelY + panelH - 24, 78, 16, tr("reset"), false, this::resetAll));
        footerButtons.add(new FooterButton(contentRight - 56, panelY + panelH - 24, 56, 16, tr("done"), true, this::onClose));
        for (FooterButton button : footerButtons) {
            addRenderableWidget(button);
        }

        buildRows();
    }

    private void selectTab(Tab target) {
        if (tab == target) {
            return;
        }
        tab = target;
        buildRows();
    }

    private void buildRows() {
        for (Row row : rows) {
            removeWidget(row.control);
        }
        rows.clear();
        scrollY = 0;

        if (tab == Tab.ACTIVATION) {
            activationUnloaded = !TrailConfig.SERVER_SPEC.isLoaded();
            activationReadOnly = !activationUnloaded && (minecraft == null || !minecraft.hasSingleplayerServer());
        } else {
            activationUnloaded = false;
            activationReadOnly = false;
        }

        switch (tab) {
            case VISUALS -> buildVisualRows();
            case AUDIO -> buildAudioRows();
            case PERFORMANCE -> buildPerformanceRows();
            case ACTIVATION -> buildActivationRows();
        }
        layoutRows();
    }

    private void buildVisualRows() {
        List<TrailConfig.TrailShape> shapes = List.of(TrailConfig.TrailShape.STREAMER,
                TrailConfig.TrailShape.WING_TIPS, TrailConfig.TrailShape.BOTH);
        List<Component> shapeLabels = List.of(tr("enum.shape.streamer"), tr("enum.shape.wing_tips"), tr("enum.shape.both"));
        addRow(tr("opt.shape.name"), tr("opt.shape.tip"),
                new CycleControl(cycleX(), 0, 116, shapeLabels,
                        () -> shapes.indexOf(TrailConfig.TRAIL_SHAPE.get()),
                        index -> apply(TrailConfig.TRAIL_SHAPE, shapes.get(index)), this::persistClient), null);
        List<TrailConfig.TrailStyle> styles = List.of(TrailConfig.TrailStyle.values());
        List<Component> styleLabels = styles.stream()
                .map(style -> tr("enum.style." + style.name().toLowerCase(Locale.ROOT)))
                .toList();
        addRow(tr("opt.style.name"), tr("opt.style.tip"),
                new CycleControl(cycleX(), 0, 116, styleLabels,
                        () -> styles.indexOf(TrailConfig.TRAIL_STYLE.get()),
                        index -> apply(TrailConfig.TRAIL_STYLE, styles.get(index)), this::persistClient), null);
        addRow(tr("opt.stardust.name"), tr("opt.stardust.tip"),
                new PillToggle(toggleX(), 0, TrailConfig.STARDUST::get,
                        value -> apply(TrailConfig.STARDUST, value), this::persistClient), null);
        addRow(tr("opt.stardust_density.name"), tr("opt.stardust_density.tip"),
                new AccentSlider(sliderX(), 0, SLIDER_W, 0.0D, 1.0D, 0.01D,
                        TrailConfig.STARDUST_DENSITY::get, value -> apply(TrailConfig.STARDUST_DENSITY, value), this::persistClient),
                () -> Math.round(TrailConfig.STARDUST_DENSITY.get() * 100.0D) + "%");
        addRow(tr("opt.water_drops.name"), tr("opt.water_drops.tip"),
                new PillToggle(toggleX(), 0, TrailConfig.WATER_DROPS::get,
                        value -> apply(TrailConfig.WATER_DROPS, value), this::persistClient), null);
        addRow(tr("opt.afterimages.name"), tr("opt.afterimages.tip"),
                new PillToggle(toggleX(), 0, TrailConfig.AFTERIMAGES::get,
                        value -> apply(TrailConfig.AFTERIMAGES, value), this::persistClient), null);
    }

    private void buildAudioRows() {
        addRow(tr("opt.wind_sound.name"), tr("opt.wind_sound.tip"),
                new PillToggle(toggleX(), 0, TrailConfig.WIND_SOUND::get,
                        value -> apply(TrailConfig.WIND_SOUND, value), this::persistClient), null);
        addRow(tr("opt.rocket_sound.name"), tr("opt.rocket_sound.tip"),
                new PillToggle(toggleX(), 0, TrailConfig.ROCKET_SOUND::get,
                        value -> apply(TrailConfig.ROCKET_SOUND, value), this::persistClient), null);
    }

    private void buildPerformanceRows() {
        addRow(tr("opt.smart_shedding.name"), tr("opt.smart_shedding.tip"),
                new PillToggle(toggleX(), 0, TrailConfig.SMART_LOAD_SHEDDING::get,
                        value -> apply(TrailConfig.SMART_LOAD_SHEDDING, value), this::persistClient), null);
        addRow(tr("opt.particle_limit.name"), tr("opt.particle_limit.tip"),
                new AccentSlider(sliderX(), 0, SLIDER_W, 20.0D, 200.0D, 5.0D,
                        () -> TrailConfig.PARTICLE_LIMIT.get(), value -> apply(TrailConfig.PARTICLE_LIMIT, (int) Math.round(value)),
                        this::persistClient),
                () -> Integer.toString(TrailConfig.PARTICLE_LIMIT.get()));
        addRow(tr("opt.low_fps.name"), tr("opt.low_fps.tip"),
                new AccentSlider(sliderX(), 0, SLIDER_W, 20.0D, 120.0D, 5.0D,
                        () -> TrailConfig.LOW_FPS_THRESHOLD.get(), value -> apply(TrailConfig.LOW_FPS_THRESHOLD, (int) Math.round(value)),
                        this::persistClient),
                () -> Integer.toString(TrailConfig.LOW_FPS_THRESHOLD.get()));
    }

    private void buildActivationRows() {
        if (activationUnloaded) {
            return;
        }
        List<TrailConfig.ActivationMode> modes = List.of(TrailConfig.ActivationMode.GLIDE,
                TrailConfig.ActivationMode.HIGH_SPEED, TrailConfig.ActivationMode.ROCKET_ONLY,
                TrailConfig.ActivationMode.CUSTOM);
        List<Component> modeLabels = List.of(tr("enum.mode.glide"), tr("enum.mode.high_speed"),
                tr("enum.mode.rocket_only"), tr("enum.mode.custom"));
        boolean editable = !activationReadOnly;
        addRow(tr("opt.mode.name"), tr("opt.mode.tip"),
                new CycleControl(cycleX(), 0, 116, modeLabels,
                        () -> modes.indexOf(TrailConfig.ACTIVATION_MODE.get()),
                        index -> apply(TrailConfig.ACTIVATION_MODE, modes.get(index)), this::persistServer), null)
                .control.active = editable;
        addRow(tr("opt.glide_speed.name"), tr("opt.glide_speed.tip"),
                speedSlider(TrailConfig.GLIDE_SPEED::get, value -> apply(TrailConfig.GLIDE_SPEED, value)),
                () -> String.format(Locale.ROOT, "%.1f", TrailConfig.GLIDE_SPEED.get())).control.active = editable;
        addRow(tr("opt.high_speed.name"), tr("opt.high_speed.tip"),
                speedSlider(TrailConfig.HIGH_SPEED::get, value -> apply(TrailConfig.HIGH_SPEED, value)),
                () -> String.format(Locale.ROOT, "%.1f", TrailConfig.HIGH_SPEED.get())).control.active = editable;
        addRow(tr("opt.custom_speed.name"), tr("opt.custom_speed.tip"),
                speedSlider(TrailConfig.CUSTOM_SPEED::get, value -> apply(TrailConfig.CUSTOM_SPEED, value)),
                () -> String.format(Locale.ROOT, "%.1f", TrailConfig.CUSTOM_SPEED.get())).control.active = editable;
        addRow(tr("opt.custom_requires_rocket.name"), tr("opt.custom_requires_rocket.tip"),
                new PillToggle(toggleX(), 0, TrailConfig.CUSTOM_REQUIRES_ROCKET::get,
                        value -> apply(TrailConfig.CUSTOM_REQUIRES_ROCKET, value), this::persistServer), null)
                .control.active = editable;
        addRow(tr("opt.rocket_memory.name"), tr("opt.rocket_memory.tip"),
                new AccentSlider(sliderX(), 0, SLIDER_W, 5.0D, 100.0D, 5.0D,
                        () -> TrailConfig.ROCKET_MEMORY_TICKS.get(),
                        value -> apply(TrailConfig.ROCKET_MEMORY_TICKS, (int) Math.round(value)), this::persistServer),
                () -> Integer.toString(TrailConfig.ROCKET_MEMORY_TICKS.get())).control.active = editable;
    }

    private AccentSlider speedSlider(DoubleSupplier getter, DoubleConsumer setter) {
        return new AccentSlider(sliderX(), 0, SLIDER_W, 0.0D, 20.0D, 0.2D, getter, setter, this::persistServer);
    }

    private Row addRow(Component label, Component tip, OptionWidget control, Supplier<String> valueText) {
        Row row = new Row(label, tip, control, valueText);
        rows.add(row);
        addRenderableWidget(control);
        return row;
    }

    private int sliderX() {
        return contentRight - VALUE_W - 6 - SLIDER_W;
    }

    private int toggleX() {
        return contentRight - 36;
    }

    private int cycleX() {
        return contentRight - 116;
    }

    private void layoutRows() {
        int y = contentTop + noteOffset() - scrollY;
        for (Row row : rows) {
            row.y = y;
            row.control.setY(y + 6);
            y += ROW_HEIGHT;
        }
        maxScroll = Math.max(0, contentTop + noteOffset() + rows.size() * ROW_HEIGHT - contentBottom);
    }

    private int noteOffset() {
        return tab == Tab.ACTIVATION && activationReadOnly ? 22 : 0;
    }

    private static <T> void apply(ForgeConfigSpec.ConfigValue<T> value, T newValue) {
        value.set(newValue);
        value.clearCache();
    }

    private void persistClient() {
        if (TrailConfig.CLIENT_SPEC.isLoaded()) {
            TrailConfig.CLIENT_SPEC.save();
        }
    }

    private void persistServer() {
        if (TrailConfig.SERVER_SPEC.isLoaded()) {
            TrailConfig.SERVER_SPEC.save();
        }
    }

    private void resetAll() {
        apply(TrailConfig.TRAIL_SHAPE, TrailConfig.TRAIL_SHAPE.getDefault());
        apply(TrailConfig.STARDUST, TrailConfig.STARDUST.getDefault());
        apply(TrailConfig.STARDUST_DENSITY, TrailConfig.STARDUST_DENSITY.getDefault());
        apply(TrailConfig.WATER_DROPS, TrailConfig.WATER_DROPS.getDefault());
        apply(TrailConfig.AFTERIMAGES, TrailConfig.AFTERIMAGES.getDefault());
        apply(TrailConfig.WIND_SOUND, TrailConfig.WIND_SOUND.getDefault());
        apply(TrailConfig.ROCKET_SOUND, TrailConfig.ROCKET_SOUND.getDefault());
        apply(TrailConfig.SMART_LOAD_SHEDDING, TrailConfig.SMART_LOAD_SHEDDING.getDefault());
        apply(TrailConfig.PARTICLE_LIMIT, TrailConfig.PARTICLE_LIMIT.getDefault());
        apply(TrailConfig.LOW_FPS_THRESHOLD, TrailConfig.LOW_FPS_THRESHOLD.getDefault());
        persistClient();
        if (TrailConfig.SERVER_SPEC.isLoaded() && !activationReadOnly) {
            apply(TrailConfig.ACTIVATION_MODE, TrailConfig.ACTIVATION_MODE.getDefault());
            apply(TrailConfig.GLIDE_SPEED, TrailConfig.GLIDE_SPEED.getDefault());
            apply(TrailConfig.HIGH_SPEED, TrailConfig.HIGH_SPEED.getDefault());
            apply(TrailConfig.CUSTOM_SPEED, TrailConfig.CUSTOM_SPEED.getDefault());
            apply(TrailConfig.CUSTOM_REQUIRES_ROCKET, TrailConfig.CUSTOM_REQUIRES_ROCKET.getDefault());
            apply(TrailConfig.ROCKET_MEMORY_TICKS, TrailConfig.ROCKET_MEMORY_TICKS.getDefault());
            persistServer();
        }
    }

    @Override
    public void onClose() {
        persistClient();
        persistServer();
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (maxScroll > 0 && mouseX >= contentX - 6 && mouseX <= contentRight
                && mouseY >= contentTop && mouseY < contentBottom) {
            scrollY = Mth.clamp(scrollY - (int) (delta * 18), 0, maxScroll);
            layoutRows();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        double time = Util.getMillis() / 1000.0D;

        graphics.fill(0, 0, width, height, OVERLAY);
        graphics.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, PANEL_BORDER);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, PANEL_BG);
        graphics.fill(panelX - 1, panelY - 1, panelX + 1, panelY + 1, OVERLAY);
        graphics.fill(panelX + panelW - 1, panelY - 1, panelX + panelW + 1, panelY + 1, OVERLAY);
        graphics.fill(panelX - 1, panelY + panelH - 1, panelX + 1, panelY + panelH + 1, OVERLAY);
        graphics.fill(panelX + panelW - 1, panelY + panelH - 1, panelX + panelW + 1, panelY + panelH + 1, OVERLAY);

        renderDots(graphics, time);
        renderHeader(graphics, time);
        for (TabButton button : tabButtons) {
            button.render(graphics, mouseX, mouseY, partialTick);
        }

        graphics.enableScissor(contentX - 6, contentTop, contentRight + 2, contentBottom);
        if (tab == Tab.ACTIVATION && activationUnloaded) {
            renderCenteredNote(graphics, tr("note.activation.unloaded"));
        } else {
            if (tab == Tab.ACTIVATION && activationReadOnly) {
                renderLines(graphics, tr("note.activation.mp"), WARN, contentTop + 2);
            }
            renderRows(graphics, mouseX, mouseY, partialTick);
            renderTabNote(graphics);
        }
        graphics.disableScissor();

        renderScrollbar(graphics);
        for (FooterButton button : footerButtons) {
            button.render(graphics, mouseX, mouseY, partialTick);
        }

        renderTooltip(graphics, mouseX, mouseY);
    }

    private void renderDots(GuiGraphics graphics, double time) {
        for (int i = 0; i < DOT_COUNT; i++) {
            float drift = (float) (time * dotSpeed[i]);
            float yy = ((dotSeedY[i] - drift) % 1.0F + 1.0F) % 1.0F;
            int x = panelX + 6 + (int) (dotSeedX[i] * (panelW - 12)) + (int) (Math.sin(time * 0.6 + dotPhase[i]) * 3.0D);
            int y = panelY + 6 + (int) (yy * (panelH - 12));
            int alpha = 0x0C + (int) (0x14 * (0.5D + 0.5D * Math.sin(time * 0.8 + dotPhase[i])));
            graphics.fill(x, y, x + 2, y + 2, (alpha << 24) | 0x66CCFF);
        }
    }

    private void renderHeader(GuiGraphics graphics, double time) {
        graphics.pose().pushPose();
        graphics.pose().scale(1.25F, 1.25F, 1.0F);
        graphics.drawString(font, title.getVisualOrderText(), (panelX + 12) / 1.25F, (panelY + 9) / 1.25F, ACCENT, false);
        graphics.pose().popPose();

        int titleW = (int) (font.width(title) * 1.25F);
        int lineY = panelY + 24;
        graphics.fill(panelX + 12, lineY, panelX + 12 + titleW, lineY + 1, ACCENT);
        graphics.fill(panelX + 12 + titleW, lineY, panelX + 12 + titleW + 26, lineY + 1, ACCENT_DARK);

        int waveEnd = panelX + panelW - 14;
        int waveStart = Math.max(panelX + 12 + titleW + 34, waveEnd - 150);
        int centerY = panelY + 14;
        for (int x = waveStart; x < waveEnd; x += 3) {
            float progress = (x - waveStart) / (float) (waveEnd - waveStart);
            int y = centerY + (int) (Math.sin(x * 0.09 + time * 2.2) * 3.5D * progress);
            int alpha = 0x18 + (int) (0x78 * progress);
            graphics.fill(x, y, x + 2, y + 2, (alpha << 24) | 0x66CCFF);
        }
    }

    private void renderRows(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Row hovered = hoveredRow(mouseX, mouseY);
        if (hovered != null) {
            graphics.fill(contentX - 6, hovered.y - 1, contentRight + 1, hovered.y + ROW_HEIGHT - 3, ROW_HOVER);
        }
        for (Row row : rows) {
            if (row.y + ROW_HEIGHT < contentTop || row.y > contentBottom) {
                continue;
            }
            row.control.render(graphics, mouseX, mouseY, partialTick);
            int labelY = row.y + 8;
            graphics.drawString(font, row.label, contentX, labelY, row.control.active ? TEXT : TEXT_DIM);
            if (row.valueText != null) {
                String value = row.valueText.get();
                graphics.drawString(font, value, contentRight - font.width(value), labelY,
                        row.control.active ? ACCENT_SOFT : TEXT_DIM);
            }
        }
    }

    private void renderTabNote(GuiGraphics graphics) {
        Component note = switch (tab) {
            case AUDIO -> tr("note.audio");
            case PERFORMANCE -> tr("note.performance");
            default -> null;
        };
        if (note == null) {
            return;
        }
        int y = contentTop + noteOffset() + rows.size() * ROW_HEIGHT - scrollY + 2;
        renderLines(graphics, note, TEXT_DIM, y);
    }

    private void renderLines(GuiGraphics graphics, Component note, int color, int startY) {
        int y = startY;
        for (FormattedCharSequence line : font.split(note, contentRight - contentX)) {
            graphics.drawString(font, line, contentX, y, color);
            y += 10;
        }
    }

    private void renderCenteredNote(GuiGraphics graphics, Component note) {
        List<FormattedCharSequence> lines = font.split(note, contentRight - contentX - 20);
        int centerX = (contentX + contentRight) / 2;
        int y = (contentTop + contentBottom) / 2 - lines.size() * 5;
        for (FormattedCharSequence line : lines) {
            graphics.drawCenteredString(font, line, centerX, y, TEXT_DIM);
            y += 10;
        }
    }

    private void renderScrollbar(GuiGraphics graphics) {
        if (maxScroll <= 0) {
            return;
        }
        int trackX = contentRight + 4;
        int trackH = contentBottom - contentTop;
        graphics.fill(trackX, contentTop, trackX + 2, contentBottom, 0xFF1C2A40);
        int thumbH = Math.max(12, trackH * trackH / (trackH + maxScroll));
        int thumbY = contentTop + (trackH - thumbH) * scrollY / maxScroll;
        graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, ACCENT_DARK);
    }

    private void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        Row hovered = hoveredRow(mouseX, mouseY);
        if (hovered != null && hovered.tip != null) {
            graphics.renderTooltip(font, font.split(hovered.tip, 220), mouseX, mouseY);
        }
    }

    private Row hoveredRow(int mouseX, int mouseY) {
        if (mouseX < contentX - 6 || mouseX > contentRight || mouseY < contentTop || mouseY >= contentBottom) {
            return null;
        }
        for (Row row : rows) {
            if (mouseY >= row.y && mouseY < row.y + ROW_HEIGHT - 2) {
                return row;
            }
        }
        return null;
    }

    private static Component tabLabel(Tab tab) {
        return switch (tab) {
            case VISUALS -> tr("tab.visuals");
            case AUDIO -> tr("tab.audio");
            case PERFORMANCE -> tr("tab.performance");
            case ACTIVATION -> tr("tab.activation");
        };
    }

    private abstract class OptionWidget extends AbstractWidget {
        private final boolean clipToContent;

        OptionWidget(int x, int y, int width, int height, Component message, boolean clipToContent) {
            super(x, y, width, height, message);
            this.clipToContent = clipToContent;
        }

        protected boolean withinContent(double mouseY) {
            return !clipToContent || (mouseY >= contentTop && mouseY < contentBottom);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!withinContent(mouseY)) {
                return false;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    private final class TabButton extends OptionWidget {
        private final Tab target;

        TabButton(int x, int y, int width, int height, Tab target) {
            super(x, y, width, height, tabLabel(target), false);
            this.target = target;
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            selectTab(target);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean selected = tab == target;
            if (selected) {
                graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xFF16283F);
                graphics.fill(getX(), getY(), getX() + 2, getY() + getHeight(), ACCENT);
            } else if (isHoveredOrFocused()) {
                graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xFF131F30);
            }
            graphics.drawString(font, getMessage(), getX() + 8, getY() + 5, selected ? ACCENT : TEXT_DIM);
        }
    }

    private final class FooterButton extends OptionWidget {
        private final boolean primary;
        private final Runnable action;

        FooterButton(int x, int y, int width, int height, Component label, boolean primary, Runnable action) {
            super(x, y, width, height, label, false);
            this.primary = primary;
            this.action = action;
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            action.run();
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean hovered = isHoveredOrFocused();
            int bg = primary ? (hovered ? ACCENT : ACCENT_DARK) : (hovered ? SLOT_BG_HOVER : SLOT_BG);
            int textColor = primary ? 0xFF0B2233 : TEXT;
            graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bg);
            graphics.fill(getX(), getY(), getX() + getWidth(), getY() + 1, primary ? 0x44FFFFFF : 0x22FFFFFF);
            graphics.drawCenteredString(font, getMessage(), getX() + getWidth() / 2, getY() + 4, textColor);
        }
    }

    private final class PillToggle extends OptionWidget {
        private final BooleanSupplier getter;
        private final Consumer<Boolean> setter;
        private final Runnable persist;
        private float knob;
        private long lastFrame;

        PillToggle(int x, int y, BooleanSupplier getter, Consumer<Boolean> setter, Runnable persist) {
            super(x, y, 36, 14, Component.empty(), true);
            this.getter = getter;
            this.setter = setter;
            this.persist = persist;
            this.knob = getter.getAsBoolean() ? 1.0F : 0.0F;
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            setter.accept(!getter.getAsBoolean());
            persist.run();
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            boolean on = getter.getAsBoolean();
            long now = Util.getMillis();
            if (lastFrame != 0L) {
                float dt = Math.min(0.1F, (now - lastFrame) / 1000.0F);
                knob += ((on ? 1.0F : 0.0F) - knob) * Math.min(1.0F, dt * 14.0F);
            }
            lastFrame = now;

            int x = getX();
            int y = getY();
            int w = getWidth();
            int h = getHeight();
            int bg = !active ? DISABLED_BG : on ? ACCENT_DARK : isHoveredOrFocused() ? 0xFF3A4C6B : 0xFF2C3A52;
            graphics.fill(x + 1, y, x + w - 1, y + h, bg);
            graphics.fill(x, y + 1, x + w, y + h - 1, bg);
            if (on && active) {
                graphics.fill(x + 1, y + 1, x + w - 1, y + 2, 0x30FFFFFF);
            }
            int knobW = 8;
            int knobX = x + 2 + Math.round(knob * (w - 4 - knobW));
            int knobColor = active ? 0xFFF2F9FF : 0xFF8A97A8;
            graphics.fill(knobX + 1, y + 2, knobX + knobW - 1, y + h - 2, knobColor);
            graphics.fill(knobX, y + 3, knobX + knobW, y + h - 3, knobColor);
        }
    }

    private final class AccentSlider extends OptionWidget {
        private final double min;
        private final double max;
        private final double step;
        private final DoubleSupplier getter;
        private final DoubleConsumer setter;
        private final Runnable persist;
        private boolean dragging;

        AccentSlider(int x, int y, int width, double min, double max, double step,
                     DoubleSupplier getter, DoubleConsumer setter, Runnable persist) {
            super(x, y, width, 14, Component.empty(), true);
            this.min = min;
            this.max = max;
            this.step = step;
            this.getter = getter;
            this.setter = setter;
            this.persist = persist;
        }

        private void setFromMouse(double mouseX) {
            double t = Mth.clamp((mouseX - getX()) / (double) getWidth(), 0.0D, 1.0D);
            double value = min + t * (max - min);
            value = Math.round(value / step) * step;
            value = Mth.clamp(value, min, max);
            if (Math.abs(value - getter.getAsDouble()) > 1.0E-9D) {
                setter.accept(value);
            }
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            dragging = true;
            setFromMouse(mouseX);
        }

        @Override
        protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
            if (dragging) {
                setFromMouse(mouseX);
            }
        }

        @Override
        public boolean mouseReleased(double mouseX, double mouseY, int button) {
            if (dragging && button == 0) {
                dragging = false;
                persist.run();
                return true;
            }
            return super.mouseReleased(mouseX, mouseY, button);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int x = getX();
            int y = getY();
            int w = getWidth();
            double value = Mth.clamp(getter.getAsDouble(), min, max);
            int fillW = (int) Math.round((value - min) / (max - min) * w);
            int trackColor = !active ? DISABLED_BG
                    : isHoveredOrFocused() || dragging ? 0xFF33486A : TRACK;
            graphics.fill(x, y + 5, x + w, y + 9, trackColor);
            graphics.fill(x, y + 6, x + w, y + 8, 0xFF1B2942);
            if (fillW > 0 && active) {
                graphics.fill(x, y + 5, x + fillW, y + 9, ACCENT_DARK);
                graphics.fill(x, y + 6, x + fillW, y + 8, ACCENT);
            }
            int knobX = x + Mth.clamp(fillW, 1, w - 5);
            int knobColor = active ? 0xFFF2F9FF : 0xFF8A97A8;
            graphics.fill(knobX, y + 2, knobX + 4, y + 12, knobColor);
            graphics.fill(knobX + 1, y + 3, knobX + 3, y + 11, active ? ACCENT_DARK : 0xFF5A6B80);
        }
    }

    private final class CycleControl extends OptionWidget {
        private final List<Component> labels;
        private final IntSupplier getter;
        private final IntConsumer setter;
        private final Runnable persist;

        CycleControl(int x, int y, int width, List<Component> labels, IntSupplier getter,
                     IntConsumer setter, Runnable persist) {
            super(x, y, width, 14, Component.empty(), true);
            this.labels = labels;
            this.getter = getter;
            this.setter = setter;
            this.persist = persist;
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            setter.accept((getter.getAsInt() + 1) % labels.size());
            persist.run();
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (active && visible && button == 1 && withinContent(mouseY) && clicked(mouseX, mouseY)) {
                playDownSound(Minecraft.getInstance().getSoundManager());
                setter.accept((getter.getAsInt() + labels.size() - 1) % labels.size());
                persist.run();
                return true;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int x = getX();
            int y = getY();
            int w = getWidth();
            int h = getHeight();
            graphics.fill(x, y, x + w, y + h, active ? (isHoveredOrFocused() ? SLOT_BG_HOVER : SLOT_BG) : 0xFF1A2230);
            graphics.fill(x, y + h - 1, x + w, y + h, active ? ACCENT_DARK : 0xFF2A3648);
            Component label = labels.get(Mth.clamp(getter.getAsInt(), 0, labels.size() - 1));
            graphics.drawCenteredString(font, label, x + w / 2, y + 3, active ? TEXT : TEXT_DIM);
            int arrow = active ? ACCENT : 0xFF4A5B72;
            graphics.drawString(font, "<", x + 4, y + 3, arrow);
            graphics.drawString(font, ">", x + w - 8, y + 3, arrow);
        }
    }
}
