package ru.strange.client.event;

import ru.strange.client.Strange;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class EventManager {
    private static final Map<Class<? extends Event>, CopyOnWriteArrayList<MethodData>> REGISTRY_MAP = new ConcurrentHashMap<>();


    public static void register(Object object) {
        if (object == null) {
            return;
        }
        for (final Method method : object.getClass().getDeclaredMethods()) {
            if (isMethodBad(method)) continue;
            register(method, object);
        }
    }

    public static void unregister(Object object) {
        if (object == null) {
            return;
        }
        for (final CopyOnWriteArrayList<MethodData> dataList : REGISTRY_MAP.values()) {
            dataList.removeIf(data -> data.getSource() == object);
        }
        removeEmptyEntries();
    }

    @SuppressWarnings("unchecked")
    private static void register(Method method, Object object) {
        try {
            Class<? extends Event> indexClass = (Class<? extends Event>) method.getParameterTypes()[0].asSubclass(Event.class);
            MethodData data = new MethodData(object, method, method.getAnnotation(EventInit.class).value());

            if (!data.getTarget().canAccess(object)) {
                data.getTarget().setAccessible(true);
            }

            CopyOnWriteArrayList<MethodData> handlers = REGISTRY_MAP.computeIfAbsent(indexClass, key -> new CopyOnWriteArrayList<>());
            if (handlers.contains(data)) {
                return;
            }

            handlers.add(data);
            sortHandlers(indexClass, handlers);

            Strange.LOGGER.debug("Registered event handler {}.{}({})",
                    object.getClass().getSimpleName(),
                    method.getName(),
                    indexClass.getSimpleName());
        } catch (RuntimeException exception) {
            Strange.LOGGER.warn("Failed to register event handler {} in {}", method.getName(), object.getClass().getName(), exception);
        }
    }

    private static void removeEmptyEntries() {
        REGISTRY_MAP.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    private static void sortHandlers(Class<? extends Event> indexClass, List<MethodData> handlers) {
        if (handlers.size() <= 1) {
            return;
        }

        CopyOnWriteArrayList<MethodData> sortedList = new CopyOnWriteArrayList<>();

        for (final byte priority : Priority.VALUE_ARRAY) {
            for (final MethodData data : handlers) {
                if (data.getPriority() == priority) {
                    sortedList.add(data);
                }
            }
        }

        REGISTRY_MAP.put(indexClass, sortedList);
    }

    private static boolean isMethodBad(Method method) {
        return method.getParameterTypes().length != 1
                || !method.isAnnotationPresent(EventInit.class)
                || !Event.class.isAssignableFrom(method.getParameterTypes()[0]);
    }

    public static Event call(Event event) {
        List<MethodData> dataList = REGISTRY_MAP.get(event.getClass());

        if (dataList == null) {
            return event;
        }

        if (event instanceof EventStoppable stoppable) {
            for (final MethodData data : dataList) {
                invoke(data, event);
                if (stoppable.isStopped()) {
                    break;
                }
            }
        } else {
            for (final MethodData data : dataList) {
                invoke(data, event);
            }
        }

        return event;
    }

    private static void invoke(MethodData data, Event argument) {
        try {
            data.getTarget().invoke(data.getSource(), argument);
        } catch (IllegalAccessException | IllegalArgumentException e) {
            Strange.LOGGER.warn("Failed to invoke {} on {}",
                    data.getTarget().getName(),
                    data.getSource().getClass().getSimpleName(),
                    e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            Strange.LOGGER.warn("Exception in handler {} on {}",
                    data.getTarget().getName(),
                    data.getSource().getClass().getSimpleName(),
                    cause != null ? cause : e);
        }
    }



    private static final class MethodData {
        private final Object source;
        private final Method target;
        private final byte priority;

        private MethodData(Object source, Method target, byte priority) {
            this.source = source;
            this.target = target;
            this.priority = priority;
        }

        public Object getSource() {
            return source;
        }

        public Method getTarget() {
            return target;
        }

        public byte getPriority() {
            return priority;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MethodData that = (MethodData) o;
            return priority == that.priority && source == that.source && target.equals(that.target);
        }

        @Override
        public int hashCode() {
            int result = System.identityHashCode(source);
            result = 31 * result + target.hashCode();
            result = 31 * result + Byte.hashCode(priority);
            return result;
        }

    }
}
