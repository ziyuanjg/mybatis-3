/**
 *    Copyright 2009-2017 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.reflection;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.ReflectPermission;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.ibatis.reflection.invoker.GetFieldInvoker;
import org.apache.ibatis.reflection.invoker.Invoker;
import org.apache.ibatis.reflection.invoker.MethodInvoker;
import org.apache.ibatis.reflection.invoker.SetFieldInvoker;
import org.apache.ibatis.reflection.property.PropertyNamer;

/**
 * This class represents a cached set of class definition information that
 * allows for easy mapping between property names and getter/setter methods.
 *
 * @author Clinton Begin
 */
public class Reflector {

  private static final String[] EMPTY_STRING_ARRAY = new String[0];

 
  private String[] readablePropertyNames = EMPTY_STRING_ARRAY;
  private String[] writeablePropertyNames = EMPTY_STRING_ARRAY;
  //反射容器对应的类
  private Class<?> type;
  //setter方法调用器缓存
  private Map<String, Invoker> setMethods = new HashMap<String, Invoker>();
  //getter方法调用器缓存
  private Map<String, Invoker> getMethods = new HashMap<String, Invoker>();
  //setter方法对应属性类型缓存
  private Map<String, Class<?>> setTypes = new HashMap<String, Class<?>>();
  //getter方法对应属性类型缓存
  private Map<String, Class<?>> getTypes = new HashMap<String, Class<?>>();
  //默认构造器
  private Constructor<?> defaultConstructor;
  //全大写属性名集合，为了兼容传入参数的大小写
  private Map<String, String> caseInsensitivePropertyMap = new HashMap<String, String>();

  public Reflector(Class<?> clazz) {
    type = clazz;
    //注册默认构造器
    addDefaultConstructor(clazz);
    //注册getter方法
    addGetMethods(clazz);
    //注册setter方法
    addSetMethods(clazz);
    //注册属性
    addFields(clazz);
    //可读属性名集合
    readablePropertyNames = getMethods.keySet().toArray(new String[getMethods.keySet().size()]);
    //可写属性名集合
    writeablePropertyNames = setMethods.keySet().toArray(new String[setMethods.keySet().size()]);
    //将属性名改为大写，为做大小写兼容做准备
    for (String propName : readablePropertyNames) {
      caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName);
    }
    for (String propName : writeablePropertyNames) {
      caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName);
    }
  }

  /**
   * 注册无参构造方法
   */
  private void addDefaultConstructor(Class<?> clazz) {
	//获取clazz的所有构造器
    Constructor<?>[] consts = clazz.getDeclaredConstructors();
    for (Constructor<?> constructor : consts) {
      if (constructor.getParameterTypes().length == 0) {
    	//无参构造方法
        if (canAccessPrivateMethods()) {
          try {
        	//关闭java安全检查，提高反射性能
            constructor.setAccessible(true);
          } catch (Exception e) {
          }
        }
        //上边取消了无参构造方法的安全检查，此处将其赋值给defaultConstructor
        if (constructor.isAccessible()) {
          this.defaultConstructor = constructor;
        }
      }
    }
  }

  /**
   * 注册getter方法
   * @param cls
   */
  private void addGetMethods(Class<?> cls) {
    Map<String, List<Method>> conflictingGetters = new HashMap<String, List<Method>>();
    //获取cls中的所有方法，包括父类和接口中的方法
    Method[] methods = getClassMethods(cls);
    for (Method method : methods) {
      //getter方法不应包含参数
      if (method.getParameterTypes().length > 0) {
        continue;
      }
      String name = method.getName();
      //判断方法名是否符合get或者布尔值get命名
      if ((name.startsWith("get") && name.length() > 3)
          || (name.startsWith("is") && name.length() > 2)) {
        name = PropertyNamer.methodToProperty(name);
        //将get方法实例添加至conflictingGetters
        addMethodConflict(conflictingGetters, name, method);
      }
    }
    resolveGetterConflicts(conflictingGetters);
  }

  /**
   * 过滤掉同一名称的getter方法，返回值为同一继承树的类型以子类型为主
   * @param conflictingGetters
   */
  private void resolveGetterConflicts(Map<String, List<Method>> conflictingGetters) {
    for (Entry<String, List<Method>> entry : conflictingGetters.entrySet()) {
      Method winner = null;
      String propName = entry.getKey();
      for (Method candidate : entry.getValue()) {
        if (winner == null) {
          winner = candidate;
          continue;
        }
        Class<?> winnerType = winner.getReturnType();
        Class<?> candidateType = candidate.getReturnType();
        //因为方法对应属性名相同（不论前缀是is或get），所以如果两者返回值类型相同时必为布尔类型（非布尔类型默认不使用is前缀）。
        if (candidateType.equals(winnerType)) {
          if (!boolean.class.equals(candidateType)) {
            throw new ReflectionException(
                "Illegal overloaded getter method with ambiguous type for property "
                    + propName + " in class " + winner.getDeclaringClass()
                    + ". This breaks the JavaBeans specification and can cause unpredictable results.");
          } else if (candidate.getName().startsWith("is")) {
            winner = candidate;
          }
        } else if (candidateType.isAssignableFrom(winnerType)) {
          // OK getter type is descendant
        } else if (winnerType.isAssignableFrom(candidateType)) {
          //winnerType是candidateType的父类或接口或两者相同，此时以子类类型为主。
          winner = candidate;
        } else {
          throw new ReflectionException(
              "Illegal overloaded getter method with ambiguous type for property "
                  + propName + " in class " + winner.getDeclaringClass()
                  + ". This breaks the JavaBeans specification and can cause unpredictable results.");
        }
      }
      //将method及返回类型添加至缓存区
      addGetMethod(propName, winner);
    }
  }

  /**
   * 将propName的get方法和get类型放入缓存
   * @param name
   * @param method
   */
  private void addGetMethod(String name, Method method) {
    if (isValidPropertyName(name)) {
      //在resolveGetterConflicts已经过滤了一次，此处无需再次过滤
      getMethods.put(name, new MethodInvoker(method));
      Type returnType = TypeParameterResolver.resolveReturnType(method, type);
      getTypes.put(name, typeToClass(returnType));
    }
  }
  
  /**
   * 注册setter方法
   * @param cls
   */
  private void addSetMethods(Class<?> cls) {
    Map<String, List<Method>> conflictingSetters = new HashMap<String, List<Method>>();
    Method[] methods = getClassMethods(cls);
    for (Method method : methods) {
      String name = method.getName();
      if (name.startsWith("set") && name.length() > 3) {
        if (method.getParameterTypes().length == 1) {
          //以set开头且只有一个参数的判定为setter方法
          name = PropertyNamer.methodToProperty(name);
          //将set方法实例添加至conflictingSetters
          addMethodConflict(conflictingSetters, name, method);
        }
      }
    }
    resolveSetterConflicts(conflictingSetters);
  }

  /**
   * 将method添加值conflictingMethods集合,可以支持同一属性多个方法
   * @param conflictingMethods
   * @param name
   * @param method
   */
  private void addMethodConflict(Map<String, List<Method>> conflictingMethods, String name, Method method) {
    List<Method> list = conflictingMethods.get(name);
    if (list == null) {
      list = new ArrayList<Method>();
      conflictingMethods.put(name, list);
    }
    list.add(method);
  }

  /**
   * 过滤掉同一名称的setter方法，返回值为同一继承树的类型以子类型为主
   * @param conflictingGetters
   */
  private void resolveSetterConflicts(Map<String, List<Method>> conflictingSetters) {
    for (String propName : conflictingSetters.keySet()) {
      List<Method> setters = conflictingSetters.get(propName);
      //从缓存集合中获取propName属性对应的get类型
      Class<?> getterType = getTypes.get(propName);
      Method match = null;
      ReflectionException exception = null;
      for (Method setter : setters) {
        Class<?> paramType = setter.getParameterTypes()[0];
        if (paramType.equals(getterType)) {
          // should be the best match
          match = setter;
          break;
        }
        if (exception == null) {
          try {
        	//取match与setter中返回类型为子类的方法
            match = pickBetterSetter(match, setter, propName);
          } catch (ReflectionException e) {
            match = null;
            exception = e;
          }
        }
      }
      if (match == null) {
        throw exception;
      } else {
    	//将propName的set方法和set类型放入缓存
        addSetMethod(propName, match);
      }
    }
  }

  /**
   * 取setter1和setter2中返回值为子类的方法对象
   * @param setter1
   * @param setter2
   * @param property
   * @return
   */
  private Method pickBetterSetter(Method setter1, Method setter2, String property) {
    if (setter1 == null) {
      return setter2;
    }
    Class<?> paramType1 = setter1.getParameterTypes()[0];
    Class<?> paramType2 = setter2.getParameterTypes()[0];
    if (paramType1.isAssignableFrom(paramType2)) {
      return setter2;
    } else if (paramType2.isAssignableFrom(paramType1)) {
      return setter1;
    }
    throw new ReflectionException("Ambiguous setters defined for property '" + property + "' in class '"
        + setter2.getDeclaringClass() + "' with types '" + paramType1.getName() + "' and '"
        + paramType2.getName() + "'.");
  }

  /**
   * 将propName的set方法和set类型放入缓存
   * @param name
   * @param method
   */
  private void addSetMethod(String name, Method method) {
    if (isValidPropertyName(name)) {
      setMethods.put(name, new MethodInvoker(method));
      Type[] paramTypes = TypeParameterResolver.resolveParamTypes(method, type);
      setTypes.put(name, typeToClass(paramTypes[0]));
    }
  }

  /**
   * 获取src类型的对应java类
   * @param src
   * @return
   */
  private Class<?> typeToClass(Type src) {
    Class<?> result = null;
    if (src instanceof Class) {
      result = (Class<?>) src;
    } else if (src instanceof ParameterizedType) {
      result = (Class<?>) ((ParameterizedType) src).getRawType();
    } else if (src instanceof GenericArrayType) {
      Type componentType = ((GenericArrayType) src).getGenericComponentType();
      if (componentType instanceof Class) {
        result = Array.newInstance((Class<?>) componentType, 0).getClass();
      } else {
        Class<?> componentClass = typeToClass(componentType);
        result = Array.newInstance((Class<?>) componentClass, 0).getClass();
      }
    }
    if (result == null) {
      result = Object.class;
    }
    return result;
  }

  /**
   * 注册属性
   * @param clazz
   */
  private void addFields(Class<?> clazz) {
    Field[] fields = clazz.getDeclaredFields();
    for (Field field : fields) {
      //检查是否能够访问类中的字段和调用方法。注意，这不仅包括 public、而且还包括 protected 和 private 字段和方法。
      if (canAccessPrivateMethods()) {
        try {
          //关闭属性的安全检查
          field.setAccessible(true);
        } catch (Exception e) {
        }
      }
      if (field.isAccessible()) {
    	//检查缓存中是否包含field的get、set方法，如果没有则创建新的get、set方法放入缓存区
        if (!setMethods.containsKey(field.getName())) {
          int modifiers = field.getModifiers();
          //同时使用static和final修饰的变量不能使用反射获取
          if (!(Modifier.isFinal(modifiers) && Modifier.isStatic(modifiers))) {
            addSetField(field);
          }
        }
        if (!getMethods.containsKey(field.getName())) {
          addGetField(field);
        }
      }
    }
    if (clazz.getSuperclass() != null) {
      //注册父类的属性
      addFields(clazz.getSuperclass());
    }
  }

  /**
   * 创建field的set方法调用器，放入缓存区
   * @param field
   */
  private void addSetField(Field field) {
    if (isValidPropertyName(field.getName())) {
      setMethods.put(field.getName(), new SetFieldInvoker(field));
      Type fieldType = TypeParameterResolver.resolveFieldType(field, type);
      setTypes.put(field.getName(), typeToClass(fieldType));
    }
  }

  /**
   * 创建field的get方法调用器，放入缓存区
   * @param field
   */
  private void addGetField(Field field) {
    if (isValidPropertyName(field.getName())) {
      getMethods.put(field.getName(), new GetFieldInvoker(field));
      Type fieldType = TypeParameterResolver.resolveFieldType(field, type);
      getTypes.put(field.getName(), typeToClass(fieldType));
    }
  }

  /**
   * 检查方法名是否合规
   * @param name
   * @return
   */
  private boolean isValidPropertyName(String name) {
    return !(name.startsWith("$") || "serialVersionUID".equals(name) || "class".equals(name));
  }

  /*
   * This method returns an array containing all methods
   * declared in this class and any superclass.
   * We use this method, instead of the simpler Class.getMethods(),
   * because we want to look for private methods as well.
   *
   * @param cls The class
   * @return An array containing all methods in this class
   */
  private Method[] getClassMethods(Class<?> cls) {
    Map<String, Method> uniqueMethods = new HashMap<String, Method>();
    Class<?> currentClass = cls;
    while (currentClass != null) {
      addUniqueMethods(uniqueMethods, currentClass.getDeclaredMethods());

      // we also need to look for interface methods -
      // because the class may be abstract
      Class<?>[] interfaces = currentClass.getInterfaces();
      for (Class<?> anInterface : interfaces) {
        addUniqueMethods(uniqueMethods, anInterface.getMethods());
      }

      currentClass = currentClass.getSuperclass();
    }

    Collection<Method> methods = uniqueMethods.values();

    return methods.toArray(new Method[methods.size()]);
  }

  private void addUniqueMethods(Map<String, Method> uniqueMethods, Method[] methods) {
    for (Method currentMethod : methods) {
      if (!currentMethod.isBridge()) {
        String signature = getSignature(currentMethod);
        // check to see if the method is already known
        // if it is known, then an extended class must have
        // overridden a method
        if (!uniqueMethods.containsKey(signature)) {
          if (canAccessPrivateMethods()) {
            try {
              currentMethod.setAccessible(true);
            } catch (Exception e) {
              // Ignored. This is only a final precaution, nothing we can do.
            }
          }

          uniqueMethods.put(signature, currentMethod);
        }
      }
    }
  }

  private String getSignature(Method method) {
    StringBuilder sb = new StringBuilder();
    Class<?> returnType = method.getReturnType();
    if (returnType != null) {
      sb.append(returnType.getName()).append('#');
    }
    sb.append(method.getName());
    Class<?>[] parameters = method.getParameterTypes();
    for (int i = 0; i < parameters.length; i++) {
      if (i == 0) {
        sb.append(':');
      } else {
        sb.append(',');
      }
      sb.append(parameters[i].getName());
    }
    return sb.toString();
  }

  /**
   * 检查是否能够访问类中的字段和调用方法。注意，这不仅包括 public、而且还包括 protected 和 private 字段和方法。
   * @return
   */
  private static boolean canAccessPrivateMethods() {
    try {
      //安全检查器
      SecurityManager securityManager = System.getSecurityManager();
      if (null != securityManager) {
    	//suppressAccessChecks权限表示能够访问类中的字段和调用方法。注意，这不仅包括 public、而且还包括 protected 和 private 字段和方法。
        securityManager.checkPermission(new ReflectPermission("suppressAccessChecks"));
      }
    } catch (SecurityException e) {
      return false;
    }
    return true;
  }

  /*
   * Gets the name of the class the instance provides information for
   *
   * @return The class name
   */
  public Class<?> getType() {
    return type;
  }

  public Constructor<?> getDefaultConstructor() {
    if (defaultConstructor != null) {
      return defaultConstructor;
    } else {
      throw new ReflectionException("There is no default constructor for " + type);
    }
  }

  public boolean hasDefaultConstructor() {
    return defaultConstructor != null;
  }

  public Invoker getSetInvoker(String propertyName) {
    Invoker method = setMethods.get(propertyName);
    if (method == null) {
      throw new ReflectionException("There is no setter for property named '" + propertyName + "' in '" + type + "'");
    }
    return method;
  }

  public Invoker getGetInvoker(String propertyName) {
    Invoker method = getMethods.get(propertyName);
    if (method == null) {
      throw new ReflectionException("There is no getter for property named '" + propertyName + "' in '" + type + "'");
    }
    return method;
  }

  /*
   * Gets the type for a property setter
   *
   * @param propertyName - the name of the property
   * @return The Class of the propery setter
   */
  public Class<?> getSetterType(String propertyName) {
    Class<?> clazz = setTypes.get(propertyName);
    if (clazz == null) {
      throw new ReflectionException("There is no setter for property named '" + propertyName + "' in '" + type + "'");
    }
    return clazz;
  }

  /*
   * Gets the type for a property getter
   *
   * @param propertyName - the name of the property
   * @return The Class of the propery getter
   */
  public Class<?> getGetterType(String propertyName) {
    Class<?> clazz = getTypes.get(propertyName);
    if (clazz == null) {
      throw new ReflectionException("There is no getter for property named '" + propertyName + "' in '" + type + "'");
    }
    return clazz;
  }

  /*
   * Gets an array of the readable properties for an object
   *
   * @return The array
   */
  public String[] getGetablePropertyNames() {
    return readablePropertyNames;
  }

  /*
   * Gets an array of the writeable properties for an object
   *
   * @return The array
   */
  public String[] getSetablePropertyNames() {
    return writeablePropertyNames;
  }

  /*
   * Check to see if a class has a writeable property by name
   *
   * @param propertyName - the name of the property to check
   * @return True if the object has a writeable property by the name
   */
  public boolean hasSetter(String propertyName) {
    return setMethods.keySet().contains(propertyName);
  }

  /*
   * Check to see if a class has a readable property by name
   *
   * @param propertyName - the name of the property to check
   * @return True if the object has a readable property by the name
   */
  public boolean hasGetter(String propertyName) {
    return getMethods.keySet().contains(propertyName);
  }

  public String findPropertyName(String name) {
    return caseInsensitivePropertyMap.get(name.toUpperCase(Locale.ENGLISH));
  }
}
