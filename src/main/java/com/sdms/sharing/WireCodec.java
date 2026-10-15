package com.sdms.sharing;

import com.google.gson.*;
import com.sdms.model.SearchResult;
import java.lang.reflect.*;
import java.time.LocalDate;
import java.util.*;

/** Explicit JSON values for the application's model beans; never Java deserialization. */
public final class WireCodec {
  private WireCodec() {}

  public static JsonElement encode(Object value) {
    if (value == null) return JsonNull.INSTANCE;
    if (value instanceof String s) return new JsonPrimitive(s);
    if (value instanceof Number n) return new JsonPrimitive(n);
    if (value instanceof Boolean b) return new JsonPrimitive(b);
    if (value instanceof Enum<?> || value instanceof LocalDate)
      return new JsonPrimitive(value.toString());
    if (value instanceof Iterable<?> list) {
      JsonArray a = new JsonArray();
      list.forEach(v -> a.add(encode(v)));
      return a;
    }
    if (value.getClass().isArray()) {
      JsonArray a = new JsonArray();
      for (int i = 0; i < Array.getLength(value); i++) a.add(encode(Array.get(value, i)));
      return a;
    }
    if (!value.getClass().getPackageName().equals("com.sdms.model"))
      throw new IllegalArgumentException("Unsupported wire value");
    JsonObject object = new JsonObject();
    try {
      for (Method getter : value.getClass().getMethods()) {
        String key = beanKey(getter);
        if (key != null) object.add(key, encode(getter.invoke(value)));
      }
      for (Field field : value.getClass().getFields())
        if (!Modifier.isStatic(field.getModifiers()))
          object.add(field.getName(), encode(field.get(value)));
    } catch (ReflectiveOperationException e) {
      throw new IllegalArgumentException("Cannot encode model", e);
    }
    return object;
  }

  public static Object decode(JsonElement value, Type type) {
    if (value == null || value.isJsonNull()) return null;
    if (type instanceof ParameterizedType p && p.getRawType() == List.class) {
      List<Object> result = new ArrayList<>();
      for (JsonElement item : value.getAsJsonArray())
        result.add(decode(item, p.getActualTypeArguments()[0]));
      return result;
    }
    if (!(type instanceof Class<?> c)) throw new IllegalArgumentException("Unsupported wire type");
    if (c == void.class) return null;
    if (c == String.class) return value.getAsString();
    if (c == int.class || c == Integer.class) return value.getAsInt();
    if (c == boolean.class || c == Boolean.class) return value.getAsBoolean();
    if (c == LocalDate.class) return LocalDate.parse(value.getAsString());
    if (c.isEnum()) return Enum.valueOf(c.asSubclass(Enum.class), value.getAsString());
    if (c.isArray()) {
      JsonArray a = value.getAsJsonArray();
      Object result = Array.newInstance(c.getComponentType(), a.size());
      for (int i = 0; i < a.size(); i++)
        Array.set(result, i, decode(a.get(i), c.getComponentType()));
      return result;
    }
    if (!c.getPackageName().equals("com.sdms.model"))
      throw new IllegalArgumentException("Unsupported model");
    JsonObject object = value.getAsJsonObject();
    if (c == SearchResult.class)
      return new SearchResult(
          SearchResult.Module.valueOf(object.get("module").getAsString()),
          object.get("dbId").getAsInt(),
          string(object, "id"),
          string(object, "name"),
          string(object, "detail"));
    try {
      Object result = c.getConstructor().newInstance();
      fill(result, object);
      return result;
    } catch (ReflectiveOperationException e) {
      throw new IllegalArgumentException("Cannot decode model", e);
    }
  }

  public static void fill(Object target, JsonObject object) throws ReflectiveOperationException {
    for (Method setter : target.getClass().getMethods()) {
      if (!setter.getName().startsWith("set") || setter.getParameterCount() != 1) continue;
      String key =
          Character.toLowerCase(setter.getName().charAt(3)) + setter.getName().substring(4);
      if (object.has(key))
        setter.invoke(target, decode(object.get(key), setter.getGenericParameterTypes()[0]));
    }
    for (Field field : target.getClass().getFields())
      if (!Modifier.isStatic(field.getModifiers()) && object.has(field.getName()))
        field.set(target, decode(object.get(field.getName()), field.getGenericType()));
  }

  private static String beanKey(Method method) {
    if (method.getParameterCount() != 0 || method.getName().equals("getClass")) return null;
    String name = method.getName();
    int prefix =
        name.startsWith("get") && name.length() > 3 && Character.isUpperCase(name.charAt(3))
            ? 3
            : name.startsWith("is") && name.length() > 2 && Character.isUpperCase(name.charAt(2))
                ? 2
                : 0;
    if (prefix == 0 || name.length() <= prefix) return null;
    return Character.toLowerCase(name.charAt(prefix)) + name.substring(prefix + 1);
  }

  private static String string(JsonObject object, String key) {
    return object.get(key) == null || object.get(key).isJsonNull()
        ? null
        : object.get(key).getAsString();
  }
}
