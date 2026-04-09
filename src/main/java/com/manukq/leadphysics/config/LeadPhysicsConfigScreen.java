package com.manukq.leadphysics.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

public final class LeadPhysicsConfigScreen {

    private static final Pattern MARKDOWN_LINK = Pattern.compile("\\[(.+?)]\\((.+?)\\)");

    private LeadPhysicsConfigScreen() {}

    public static Screen create(Screen parent) {
        LeadPhysicsConfig config = LeadPhysicsConfig.getInstance();
        ConfigBuilder builder = ConfigBuilder.create()
            .setParentScreen(parent)
            .setTitle(Component.literal("LeadPhysics"))
            .setSavingRunnable(config::save)
            .setTransparentBackground(true);

        ConfigEntryBuilder entries = builder.entryBuilder();
        ConfigCategory general = builder.getOrCreateCategory(Component.literal("General"));
        general.addEntry(entries.startBooleanToggle(Component.literal("Enabled"), config.isEnabled())
            .setDefaultValue(true)
            .setSaveConsumer(config::setEnabled)
            .build());
        general.addEntry(entries.startEnumSelector(Component.literal("Lead Render"), LeadPhysicsConfig.RenderMode.class, config.getRenderMode())
            .setDefaultValue(LeadPhysicsConfig.RenderMode.THREE_D)
            .setEnumNameProvider(mode -> switch ((LeadPhysicsConfig.RenderMode) mode) {
                case TWO_D -> Component.literal("2D");
                case THREE_D -> Component.literal("3D");
            })
            .setSaveConsumer(config::setRenderMode)
            .build());
        general.addEntry(entries.startDoubleField(Component.literal("Physics Amplitude"), config.getPhysicsAmplitude())
            .setDefaultValue(1.0)
            .setMin(0.0)
            .setMax(3.0)
            .setSaveConsumer(config::setPhysicsAmplitude)
            .build());

        ConfigCategory credits = builder.getOrCreateCategory(Component.literal("Credits"));
        for (Component line : readCredits()) {
            credits.addEntry(entries.startTextDescription(line).build());
        }

        return builder.build();
    }

    private static List<Component> readCredits() {
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

    private static Component parseMarkdown(String line) {
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

    private static MutableComponent parseInlineMarkdown(String text) {
        Matcher matcher = MARKDOWN_LINK.matcher(text);
        int cursor = 0;
        MutableComponent component = Component.empty();
        while (matcher.find()) {
            if (matcher.start() > cursor) {
                component.append(Component.literal(text.substring(cursor, matcher.start())).withStyle(ChatFormatting.WHITE));
            }
            MutableComponent link = Component.literal(matcher.group(1))
                .withStyle(Style.EMPTY.withColor(ChatFormatting.AQUA).withUnderlined(true));
            component.append(link);
            component.append(Component.literal(" (" + matcher.group(2) + ")").withStyle(ChatFormatting.DARK_GRAY));
            cursor = matcher.end();
        }
        if (cursor < text.length()) {
            component.append(Component.literal(text.substring(cursor)).withStyle(ChatFormatting.WHITE));
        }
        return component;
    }
}
