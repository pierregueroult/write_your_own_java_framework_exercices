package com.github.forax.framework.injector;

import java.beans.PropertyDescriptor;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public final class InjectorRegistry {
  private final HashMap<Class<?>, Supplier<?>> instances;

  public InjectorRegistry() {
    instances = new HashMap<>();
    super();
  }

  public <T> void registerInstance(Class<T> type, T instance) {
    Objects.requireNonNull(type);
    Objects.requireNonNull(instance);

    registerProvider(type, () -> instance);
  }

  public <T> void registerProvider(Class<T> type, Supplier<? extends T> supplier) {
    Objects.requireNonNull(type);
    Objects.requireNonNull(supplier);

    var result = instances.putIfAbsent(type, supplier);
    if (result != null) {
      throw new IllegalStateException("already a recipe for: " + type.getSimpleName());
    }
  }

  public <T> T lookupInstance(Class<T> type) {
    Objects.requireNonNull(type);
    var result = instances.get(type);
    if (result == null) {
      throw new IllegalStateException("no recipe for: " + type.getSimpleName());
    }
    return type.cast(result.get());
  }

  public static List<PropertyDescriptor> findInjectableProperties(Class<?> type) {
    return Arrays.stream(Utils.beanInfo(type).getPropertyDescriptors()).filter(property -> {
      var method = property.getWriteMethod();
      return method != null && method.isAnnotationPresent(Inject.class);
    }).toList();
  }

  private <T> void registerProviderClassInternal(Class<T> providerClass) {
    registerProviderClass(providerClass, providerClass);
  }

  public void registerProviderClass(Class<?> providerClass) {
    Objects.requireNonNull(providerClass);
    registerProviderClassInternal(providerClass);
  }

  public <T> void registerProviderClass(Class<T> type, Class<? extends T> providerClass) {
    Objects.requireNonNull(type);
    Objects.requireNonNull(providerClass);

    var injectedConstructors = Arrays.stream(providerClass.getConstructors()).filter(c -> c.isAnnotationPresent(Inject.class)).toList();

    var constructor = switch (injectedConstructors.size()) {
      case 0 -> Utils.defaultConstructor(providerClass);
      case 1 -> injectedConstructors.getFirst();
      default -> throw new IllegalStateException("too much constructors: " + type.getSimpleName());
    };

    registerProvider(type, () -> {
      var args = Arrays.stream(constructor.getParameterTypes()).map(this::lookupInstance).toArray();
      var instance = Utils.newInstance(constructor, args);
      for (var property : findInjectableProperties(type)) {
        var setter = property.getWriteMethod();
        var properties = lookupInstance(property.getPropertyType());
        Utils.invokeMethod(instance, setter, properties);
      }
      return type.cast(instance);
    });
  }
}