/**
 *    Copyright 2009-2016 the original author or authors.
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
package org.apache.ibatis.reflection.property;

import java.util.Iterator;

/**
 * @author Clinton Begin
 */
public class PropertyTokenizer implements Iterator<PropertyTokenizer> {
  private String name;
  private String indexedName;
  private String index;
  private String children;

  public PropertyTokenizer(String fullname) {
    int delim = fullname.indexOf('.');
    if (delim > -1) {
      //如果传入的是多级属性名，则name为一级属性，children为次级属性
      name = fullname.substring(0, delim);
      children = fullname.substring(delim + 1);
    } else {
      //如果传入的是单级属性，name为属性名
      name = fullname;
      children = null;
    }
    //此处先备份name为了处理属性类型为集合的情况
    indexedName = name;
    delim = name.indexOf('[');
    if (delim > -1) {
      //如果name代表的属性为集合类型，MyBatis默认用[]表示，如user.books[math]
      //index表示集合中的指定属性名
      index = name.substring(delim + 1, name.length() - 1);
      //name表示集合名
      name = name.substring(0, delim);
    }
  }

  public String getName() {
    return name;
  }

  public String getIndex() {
    return index;
  }

  public String getIndexedName() {
    return indexedName;
  }

  public String getChildren() {
    return children;
  }

  @Override
  public boolean hasNext() {
    return children != null;
  }

  @Override
  public PropertyTokenizer next() {
    return new PropertyTokenizer(children);
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException("Remove is not supported, as it has no meaning in the context of properties.");
  }
}
