package dev.ofrostt.frostduels;

import java.util.*;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.event.*;

/** Test-only provider: retains callbacks so tests exercise the actual guarded button actions. */
public final class TestClickCallbacks implements ClickCallback.Provider {
    private static final Map<String, ClickCallback<Audience>> callbacks = new HashMap<>();
    @Override public ClickEvent create(ClickCallback<Audience> callback, ClickCallback.Options options) {
        String id = UUID.randomUUID().toString(); callbacks.put(id, callback);
        return ClickEvent.suggestCommand(id);
    }
    static void click(ClickEvent event, Audience actor) { callbacks.get(event.value()).accept(actor); }
    static void clear() { callbacks.clear(); }
}
