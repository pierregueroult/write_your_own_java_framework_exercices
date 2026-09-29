package com.github.forax.framework.mapper;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class JSONWriter {
  private static final GeneratorCache CACHE = new GeneratorCache();
  private final HashMap<Class<?>, Function<Object, String>> map = new HashMap<>();

  private interface Generator {
    String generate(JSONWriter writer, Object bean);
  }

  private static class GeneratorCache extends ClassValue<Generator> {
    @Override
    protected Generator computeValue(Class<?> type) {
      var properties = type.isRecord() ? recordProperties(type) : beanProperties(type);

      var generators = properties.stream().<Generator>map(property -> {
        var getter = property.getReadMethod();
        var jsonProperty = getter.getAnnotation(JSONProperty.class);
        var name = jsonProperty != null ? jsonProperty.value() : property.getName();
        var prefix = "\"" + name + "\": ";

        return (writer, bean) -> {
          var answer = Utils.invokeMethod(bean, getter);
          return prefix + writer.toJSON(answer);
        };
      }).toList();

      return (writer, bean) -> {
        return generators.stream().map(generator -> generator.generate(writer, bean)).collect(Collectors.joining(", ", "{", "}"));
      };
    }
  }

  private static List<PropertyDescriptor> beanProperties(Class<?> type) {
    return Arrays.stream(Utils.beanInfo(type).getPropertyDescriptors())
            // filtre uniquement sur les beans car les records ont pas ces problèmes de class et d'accesseur
            .filter(property -> !property.getName().equals("class") && property.getReadMethod() != null)
            .toList();
  }

  private static List<PropertyDescriptor> recordProperties(Class<?> type) {
    return Arrays.stream(type.getRecordComponents()).map(component -> {
      var name = component.getName();
      var getter = component.getAccessor();

      try {
        return new PropertyDescriptor(
          name, getter, null
        );
      } catch (IntrospectionException e) {
        throw new IllegalStateException(e);
      }
    }).toList();
  }

  public <T> void configure(Class<T> type, Function<? super T, String> fun) {
    Objects.requireNonNull(type);
    Objects.requireNonNull(fun);

    var existing = map.putIfAbsent(type, o -> fun.apply(type.cast(o)));
    if (existing != null) {
      throw new IllegalStateException("type already configured: " + type.getName());
    }
  }

  public String toJSON(Object o) {
    return switch (o) {
      case null -> "null";
      case Integer _, Double _, Boolean _ -> "" + o;
      case String s -> "\"" + s + "\"";
      case Object obj -> {
        var fun = map.get(obj.getClass());
        if (fun != null) {
          yield fun.apply(obj);
        }
        yield CACHE.get(obj.getClass()).generate(this, obj);
      }
    };
  }
}
