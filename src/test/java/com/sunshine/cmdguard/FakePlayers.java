package com.sunshine.cmdguard;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;

/**
 * Dynamic-proxy {@link Player}s for tests: scripted permissions, a fixed
 * UUID and safe defaults for everything else. Lets unit tests exercise
 * resolve/snapshot/invalidation logic without a server.
 */
public final class FakePlayers {

    /** Mutable script behind one fake player. */
    public static final class Script {
        public final UUID id = UUID.randomUUID();
        public String name = "TestPlayer";
        public boolean op;
        public final Map<String, Boolean> permissions = new HashMap<>();
        /** When true, any permission query throws (proves async-safe paths). */
        public boolean throwOnPermission;
        /** Everything sent to this player, in order. */
        public final java.util.List<Object> outbox = new java.util.concurrent.CopyOnWriteArrayList<>();
    }

    private FakePlayers() {}

    /**
     * Generic interface stub: scripted methods by name, safe defaults otherwise.
     * Values may be plain objects or {@link java.util.function.Supplier}s
     * (invoked with the proxy method arguments).
     */
    @SuppressWarnings("unchecked")
    public static <T> T stub(Class<T> iface, java.util.Map<String, Object> scripted) {
        InvocationHandler handler = (proxy, method, args) -> {
            if (scripted.containsKey(method.getName())) {
                Object v = scripted.get(method.getName());
                if (v instanceof java.util.function.Function) {
                    return ((java.util.function.Function<Object[], Object>) v).apply(args);
                }
                return v;
            }
            switch (method.getName()) {
                case "toString":
                    return "Stub(" + iface.getSimpleName() + ")";
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return args != null && args.length == 1 && proxy == args[0];
                default:
                    return defaultValue(method.getReturnType());
            }
        };
        return (T) Proxy.newProxyInstance(FakePlayers.class.getClassLoader(),
                new Class<?>[]{iface}, handler);
    }

    /** Creates a Player proxy driven by the given script. */
    public static Player player(Script script) {
        InvocationHandler handler = (proxy, method, args) -> {            switch (method.getName()) {
                case "getUniqueId":
                    return script.id;
                case "getName":
                    return script.name;
                case "isOp":
                    return script.op;
                case "hasPermission":
                    if (script.throwOnPermission) {
                        throw new AssertionError(
                                "must not query Bukkit permissions on this path");
                    }
                    if (args != null && args.length == 1 && args[0] instanceof String node) {
                        Boolean v = script.permissions.get(node);
                        return v != null && v;
                    }
                    return false;
                case "toString":
                    return "FakePlayer(" + script.name + ")";
                case "sendMessage":
                    if (args != null && args.length >= 1 && args[0] != null) {
                        script.outbox.add(args[0]);
                    }
                    return null;
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return args != null && args.length == 1 && proxy == args[0];
                default:
                    return defaultValue(method.getReturnType());
            }
        };
        return (Player) Proxy.newProxyInstance(FakePlayers.class.getClassLoader(),
                new Class<?>[]{Player.class}, handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == double.class) {
            return 0.0;
        }
        if (type == float.class) {
            return 0.0f;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == char.class) {
            return (char) 0;
        }
        if (type == void.class) {
            return null;
        }
        return null;
    }
}
