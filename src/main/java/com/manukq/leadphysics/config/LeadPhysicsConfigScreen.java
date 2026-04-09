package com.manukq.leadphysics.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

public final class LeadPhysicsConfigScreen extends Screen {

    private static final Pattern MARKDOWN_LINK = Pattern.compile("\\[(.+?)]\\((.+?)\\)");

    private final Screen parent;
    private CycleButton<Boolean> enabledButton;
    private CycleButton<LeadPhysicsConfig.RenderMode> renderModeButton;
    private CycleButton<Double> amplitudeButton;
    private Button saveButton;
    private List<Component> credits;

    private static final List<Double> AMPLITUDES = List.of(0.5, 0.75, 1.0, 1.25, 1.5, 2.0, 2.5, 3.0);

    public LeadPhysicsConfigScreen(Screen parent) {
        super(Component.literal("LeadPhysics"));
        this.parent = parent;
    }

    public static Screen create(Screen parent) {
        return new LeadPhysicsConfigScreen(parent);
    }

    @Override
    protected void init() {
        LeadPhysicsConfig config = LeadPhysicsConfig.getInstance();
        int panelWidth = 320;
        int left = (width - panelWidth) / 2;
        int y = 46;

        enabledButton = addRenderableWidget(CycleButton.onOffBuilder(config.isEnabled())
            .create(left, y, panelWidth, 20, Component.literal("Mod Enabled"), (button, value) -> {}));
        y += 28;

        renderModeButton = addRenderableWidget(CycleButton.builder(this::modeName)
            .withValues(LeadPhysicsConfig.RenderMode.values())
            .withInitialValue(config.getRenderMode())
            .create(left, y, panelWidth, 20, Component.literal("Lead Render"), (button, value) -> {}));
        y += 28;

        amplitudeButton = addRenderableWidget(CycleButton.builder(this::amplitudeName)
            .withValues(AMPLITUDES)
            .withInitialValue(closestAmplitude(config.getPhysicsAmplitude()))
            .create(left, y, panelWidth, 20, Component.literal("Physics Strength"), (button, value) -> {}));
        y += 28;

        int buttonWidth = (panelWidth - 8) / 2;
        saveButton = addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> saveAndClose())
            .bounds(left, y, buttonWidth, 20)
            .build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
            .bounds(left + buttonWidth + 8, y, buttonWidth, 20)
            .build());

        credits = readCredits();
        setInitialFocus(amplitudeButton);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fillGradient(0, 0, width, height, 0xC0101018, 0xD0101018);
        int panelLeft = (width - 344) / 2;
        int panelTop = 16;
        int panelBottom = Math.min(height - 16, 16 + 260);
        guiGraphics.fill(panelLeft, panelTop, panelLeft + 344, panelBottom, 0xB018181C);
        guiGraphics.renderOutline(panelLeft, panelTop, 344, panelBottom - panelTop, 0xFF3A2A1E);
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        int left = (width - 320) / 2;
        int y = 20;
        guiGraphics.drawCenteredString(font, title, width / 2, y, 0xFFFFFF);
        guiGraphics.drawString(font, Component.literal("Choose the overall swing strength of the lead."), left, 102, 0xA0A0A0);

        int creditsTop = 136;
        guiGraphics.drawString(font, Component.literal("Credits").withStyle(ChatFormatting.GOLD), left, creditsTop, 0xFFD76A);
        int textY = creditsTop + 14;
        for (Component line : credits) {
            guiGraphics.drawWordWrap(font, line, left, textY, 320, 0xE0E0E0);
            textY += Math.max(12, font.wordWrapHeight(line, 320) + 2);
            if (textY > height - 12) {
                break;
            }
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) {
            saveAndClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    private void saveAndClose() {
        LeadPhysicsConfig config = LeadPhysicsConfig.getInstance();
        config.setEnabled(enabledButton.getValue());
        config.setRenderMode(renderModeButton.getValue());
        config.setPhysicsAmplitude(amplitudeButton.getValue());
        config.save();
        onClose();
    }

    private String formatAmplitude(double value) {
        String text = String.format(java.util.Locale.ROOT, "%.2f", value);
        return text.indexOf('.') < 0 ? text : text.replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private Double closestAmplitude(double value) {
        Double closest = AMPLITUDES.getFirst();
        double bestDistance = Math.abs(closest - value);
        for (Double amplitude : AMPLITUDES) {
            double distance = Math.abs(amplitude - value);
            if (distance < bestDistance) {
                closest = amplitude;
                bestDistance = distance;
            }
        }
        return closest;
    }

    private Component amplitudeName(Double value) {
        String label = formatAmplitude(value) + "x";
        if (Math.abs(value - 0.5) < 0.001) {
            label += " Soft";
        } else if (Math.abs(value - 1.0) < 0.001) {
            label += " Default";
        } else if (Math.abs(value - 1.5) < 0.001) {
            label += " Strong";
        } else if (value >= 2.0) {
            label += " Extreme";
        }
        return Component.literal(label);
    }

    private Component modeName(LeadPhysicsConfig.RenderMode mode) {
        return switch (mode) {
            case TWO_D -> Component.literal("2D");
            case THREE_D -> Component.literal("3D");
        };
    }

    private List<Component> readCredits() {
        List<Component> lines = new ArrayList<>();
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("leadphysics", "credits.md");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Minecraft.getInstance().getResourceManager().open(id), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    lines.add(Component.empty());
                    continue;
                }
                lines.add(parseMarkdown(line));
            }
        } catch (IOException ignored) {
            lines.add(Component.literal("Credits file is unavailable.").withStyle(ChatFormatting.RED));
        }
        return lines;
    }

    private Component parseMarkdown(String line) {
        String trimmed = line.trim();
        if (trimmed.startsWith("### ")) {
            return Component.literal(trimmed.substring(4)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        }
        if (trimmed.startsWith("## ")) {
            return Component.literal(trimmed.substring(3)).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        }
        if (trimmed.startsWith("# ")) {
            return Component.literal(trimmed.substring(2)).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD);
        }
        if (trimmed.startsWith("- ")) {
            return Component.literal("• ").withStyle(ChatFormatting.GRAY).append(parseInlineMarkdown(trimmed.substring(2)));
        }
        return parseInlineMarkdown(trimmed);
    }

    private MutableComponent parseInlineMarkdown(String text) {
        Matcher matcher = MARKDOWN_LINK.matcher(text);
        int cursor = 0;
        MutableComponent component = Component.empty();
        while (matcher.find()) {
            if (matcher.start() > cursor) {
                component.append(Component.literal(text.substring(cursor, matcher.start())).withStyle(ChatFormatting.WHITE));
            }
            component.append(Component.literal(matcher.group(1)).withStyle(Style.EMPTY.withColor(ChatFormatting.AQUA).withUnderlined(true)));
            component.append(Component.literal(" (" + matcher.group(2) + ")").withStyle(ChatFormatting.DARK_GRAY));
            cursor = matcher.end();
        }
        if (cursor < text.length()) {
            component.append(Component.literal(text.substring(cursor)).withStyle(ChatFormatting.WHITE));
        }
        return component;
    }
}
