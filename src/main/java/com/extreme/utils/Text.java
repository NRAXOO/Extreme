package com.extreme.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** Conversión de texto con códigos &amp; y colores hex (&#RRGGBB) a Components. */
public final class Text {

    private static final LegacyComponentSerializer AMP =
            LegacyComponentSerializer.builder().character('&').hexColors().build();
    private static final LegacyComponentSerializer SECTION = LegacyComponentSerializer.legacySection();

    private Text() {}

    public static Component color(String s) {
        return AMP.deserialize(s == null ? "" : s);
    }

    /** Para nombres y lore de items: sin cursiva por defecto. */
    public static Component item(String s) {
        return color(s).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /** Texto con códigos § (para razones de ban, etc.). */
    public static String section(String amp) {
        return SECTION.serialize(color(amp));
    }

    public static String plain(String amp) {
        return PlainTextComponentSerializer.plainText().serialize(color(amp));
    }
}
