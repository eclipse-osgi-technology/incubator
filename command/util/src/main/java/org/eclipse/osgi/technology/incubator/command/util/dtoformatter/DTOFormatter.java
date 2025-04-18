/**
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
 * All rights reserved. 
 * 
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * 
 * Contributors:
 *     Stefan Bischof - initial
 */
package org.eclipse.osgi.technology.incubator.command.util.dtoformatter;

import java.beans.Introspector;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.osgi.technology.incubator.command.util.GlobFilter;
import org.osgi.dto.DTO;

public class DTOFormatter implements ObjectFormatter {

    private static final Cell NULL_CELL = Table.EMPTY;
    final Map<Class<?>, DTODescription> descriptors = new LinkedHashMap<>();
    public static boolean boxes = true;

    public interface ItemBuilder<T> extends GroupBuilder<T> {

        ItemDescription zitem();

        default ItemBuilder<T> method(Method readMethod) {
            if (readMethod == null) {
                System.out.println("?");
                return null;
            }
            zitem().member = o -> {
                try {
                    return readMethod.invoke(o);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            };
            return this;
        }

        default ItemBuilder<T> field(Field field) {
            zitem().member = o -> {
                try {
                    return field.get(o);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            };
            return this;
        }

        default ItemBuilder<T> minWidth(int w) {
            zitem().minWidth = w;
            return this;
        }

        default ItemBuilder<T> maxWidth(int w) {
            zitem().maxWidth = w;
            return this;
        }

        default ItemBuilder<T> width(int w) {
            zitem().maxWidth = w;
            zitem().minWidth = w;
            return this;
        }

        default ItemBuilder<T> label(String label) {
            zitem().label = label.toUpperCase();
            return this;
        }

        default DTOFormatterBuilder<T> count() {
            zitem().format = base -> {

                var o = zitem().member.apply(base);

                if (o instanceof Collection) {
                    return "" + ((Collection<?>) o).size();
                } else if (o.getClass().isArray()) {
                    return "" + Array.getLength(o);
                }

                return "?";
            };
            return this;
        }

    }

    public interface GroupBuilder<T> extends DTOFormatterBuilder<T> {
        GroupDescription zgroup();

        default GroupBuilder<T> title(String title) {
            zgroup().title = title;
            return this;
        }

        default ItemBuilder<T> item(String label) {
            var g = zgroup();
            var d = zdto();
            var i = g.items.computeIfAbsent(label, ItemDescription::new);

            ItemBuilder<T> itemBuilder = new ItemBuilder<>() {

                @Override
                public GroupDescription zgroup() {
                    return g;
                }

                @Override
                public DTODescription zdto() {
                    return d;
                }

                @Override
                public ItemDescription zitem() {
                    return i;
                }

            };

            return itemBuilder;
        }

        default ItemBuilder<T> field(String field) {
            try {
                var b = item(field);
                var f = zdto().clazz.getField(field);

                b.zitem().member = o -> {
                    try {
                        return f.get(o);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                };
                return b;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        default ItemBuilder<T> optionalField(String field) {
            try {
                return field(field);
            } catch (Exception e) {
                var b = item(field);
                zgroup().items.remove(field);
                return b;
            }
        }

        default ItemBuilder<T> optionalMethod(String method) {
            try {
                return method(method);
            } catch (Exception e) {
                var b = item(method);
                zgroup().items.remove(method);
                return b;
            }
        }

        default ItemBuilder<T> method(String method) {
            try {
                var b = item(method);
                var properties = Introspector.getBeanInfo(zdto().clazz).getPropertyDescriptors();

                Stream.of(properties).filter(property -> property.getName().equals(method))
                        .filter(property -> property.getReadMethod() != null).forEach(property -> {
                            b.method(property.getReadMethod()).label(property.getDisplayName());
                        });
                return b;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        default GroupBuilder<T> separator(String separator) {
            zgroup().separator = separator;
            return this;
        }

        default GroupBuilder<T> fields(String string) {
            var fields = zdto().clazz.getFields();
            Stream.of(fields).filter(field -> !Modifier.isStatic(field.getModifiers()))
                    .filter(field -> new GlobFilter(string).matches(field.getName())).forEach(field -> {
                        item(field.getName()).field(field);
                    });
            return this;
        }

        default GroupBuilder<T> methods(String string) {
            try {
                var properties = Introspector.getBeanInfo(zdto().clazz).getPropertyDescriptors();
                Stream.of(properties).filter(property -> new GlobFilter(string).matches(property.getName()))
                        .filter(property -> property.getReadMethod() != null).forEach(property -> {
                            item(property.getDisplayName()).method(property.getReadMethod());
                        });
                return this;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @SuppressWarnings("unchecked")
        default GroupBuilder<T> as(Function<T, String> map) {
            zgroup().format = (Function<Object, String>) map;
            return this;
        }

        @SuppressWarnings("unchecked")
        default ItemBuilder<T> format(String label, Function<T, Object> format) {
            var b = item(label);
            var item = b.zitem();
            item.format = (Function<Object, Object>) format;
            return b;
        }

        default GroupBuilder<T> remove(String string) {
            zgroup().items.remove(string);
            return this;
        }
    }

    public interface DTOFormatterBuilder<T> {

        DTODescription zdto();

        default GroupBuilder<T> inspect() {
            var d = zdto();

            return new GroupBuilder<>() {

                @Override
                public DTODescription zdto() {
                    return d;
                }

                @Override
                public GroupDescription zgroup() {
                    return zdto().inspect;
                }

            };
        }

        default GroupBuilder<T> line() {
            var d = zdto();

            return new GroupBuilder<>() {

                @Override
                public DTODescription zdto() {
                    return d;
                }

                @Override
                public GroupDescription zgroup() {
                    return zdto().line;
                }

            };
        }

        default GroupBuilder<T> part() {
            var d = zdto();

            return new GroupBuilder<>() {

                @Override
                public DTODescription zdto() {
                    return d;
                }

                @Override
                public GroupDescription zgroup() {
                    return zdto().part;
                }

            };
        }
    }

    public <T> DTOFormatterBuilder<T> build(Class<T> clazz) {
        var dto = getDescriptor(clazz, new DTODescription());

        dto.clazz = clazz;
        descriptors.put(clazz, dto);

        return () -> dto;
    }

    /*********************************************************************************************/

    private Cell inspect(Object object, DTODescription descriptor, ObjectFormatter formatter) {

        var table = new Table(descriptor.inspect.items.size(), 2, 0);
        var row = 0;

        for (ItemDescription item : descriptor.inspect.items.values()) {
            var o = getValue(object, item);
            var cell = cell(o, formatter);
            table.set(row, 0, item.label);
            table.set(row, 1, cell);
            row++;
        }
        return table;
    }

    private Object getValue(Object object, ItemDescription item) {
        if (item.format != null) {
            return item.format.apply(object);
        }

        var target = object;
        if (!item.self) {
            if (item.member == null) {
                System.out.println("? item " + item.label);
                return "? " + item.label;
            }
            target = item.member.apply(object);
        }

        return target;
    }

    @SuppressWarnings("unchecked")
    public Cell cell(Object o, ObjectFormatter formatter) {
        if (o == null) {
            return NULL_CELL;
        }

        try {
            if (o instanceof Collection) {
                return list((Collection<Object>) o, formatter);
            } else if (o.getClass().isArray()) {
                List<Object> list = new ArrayList<>();
                for (var i = 0; i < Array.getLength(o); i++) {
                    list.add(Array.get(o, i));
                }
                return list(list, formatter);
            } else if (o instanceof Map) {
                return map((Map<Object, Object>) o, formatter);
            } else if (o instanceof Dictionary) {
                Map<Object, Object> map = new HashMap<>();
                var dictionary = (Dictionary<Object, Object>) o;
                var e = dictionary.keys();
                while (e.hasMoreElements()) {
                    var key = e.nextElement();
                    var value = dictionary.get(key);
                    map.put(key, value);
                }
                return map(map, formatter);
            } else {
                var descriptor = getDescriptor(o.getClass());
                if (descriptor == null) {
                    var string = formatter.format(o, ObjectFormatter.PART, formatter);
                    if (string == null) {
                        string = Objects.toString(o);
                    }

                    if (string.length() > 25) {
                        String formatted = string.toString();

                        String[] sarr = formatted.split(",");
                        if (sarr.length > 5) {
                            formatted = String.join("," + System.lineSeparator(), sarr);
                        }
                        string = formatted;
                    }

                    return new StringCell(string.toString(), o);
                }
                return part(o, descriptor, formatter);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new StringCell(e.toString(), e);
        }
    }

    private Cell map(Map<Object, Object> map, ObjectFormatter formatter) {
        var table = new Table(map.size(), 2, 0);
        var row = 0;
        var tm = new TreeMap<Object, Object>(Comparator.comparing(Object::toString));
        tm.putAll(map);
        for (Map.Entry<Object, Object> e : tm.entrySet()) {

            var key = cell(e.getKey(), formatter);
            var value = cell(e.getValue(), formatter);
            table.set(row, 0, key);
            table.set(row, 1, value);
            row++;
        }
        return table;
    }

    private Cell list(Collection<Object> list, ObjectFormatter formatter) {

        Class<?> type = null;
        var maxwidth = 0;
        for (Object o : list) {
            if (o == null) {
                continue;
            }
            type = commonType(o.getClass(), type);
            if (type == String.class) {
                maxwidth = Math.max(o.toString().length(), maxwidth);
            }
        }

        if (type == null) {
            return new StringCell("", null);
        }

        var descriptor = getDescriptor(type);
        if (descriptor == null) {

            if (type == String.class && maxwidth < 100) {

                return new StringCell(list.toArray(new String[0]), list);
            }

            if (Number.class.isAssignableFrom(type) || type.isPrimitive() || type == Character.class
                    || type == Boolean.class) {

                var s = list.stream().map(Object::toString).collect(Collectors.joining(", "));
                return new StringCell(s, list);
            }

            var table = new Table(list.size(), 1, 0);
            var r = 0;
            for (Object o : list) {
                var c = cell(o, ObjectFormatter.LINE, formatter);
                if (c == null) {
                    c = new StringCell(formatter.format(o, ObjectFormatter.LINE, null).toString(), o);
                }
                table.set(r, 0, c);
                r++;
            }
            return table;
        }

        Table table;
        var group = descriptor.line;
        if (group.format != null) {
            table = new Table(list.size(), 1, 0);
            var r = 0;
            for (Object o : list) {
                Cell c = new StringCell(group.format.apply(o), 0);
                table.set(r, 0, c);
                r++;
            }
        } else {
            var cols = group.items.size();
            if (cols == 0) {
                // no columns defined
                var row = 0;
                table = new Table(list.size(), 1, 0);
                for (Object member : list) {
                    table.set(row, 0, member.toString());
                }
            } else {
                var col = 0;
                table = new Table(list.size() + 1, cols, 1);
                for (ItemDescription item : group.items.values()) {
                    table.set(0, col, item.label);
                    col++;
                }
                var row = 1;
                for (Object member : list) {
                    col = 0;
                    for (ItemDescription item : group.items.values()) {
                        var o = getValue(member, item);
                        var cell = cell(o, formatter);
                        table.set(row, col, cell);
                        col++;
                    }
                    row++;
                }
            }
        }
        return table;
    }

    private Class<?> commonType(Class<? extends Object> class1, Class<?> type) {
        if ((type == null) || class1.isAssignableFrom(type)) {
            return class1;
        }

        if (type.isAssignableFrom(class1)) {
            return type;
        }

        return Object.class;
    }

    private Cell line(Object object, DTODescription description, ObjectFormatter formatter) {
        var table = new Table(1, description.line.items.size(), 0);
        line(object, description, 0, table, formatter);
        return table;
    }

    private void line(Object object, DTODescription description, int row, Table table, ObjectFormatter formatter) {
        var col = 0;
        for (ItemDescription item : description.line.items.values()) {
            var o = getValue(object, item);
            var cell = cell(o, formatter);
            table.set(row, col, cell);
            col++;
        }
    }

    private Cell part(Object object, DTODescription descriptor, ObjectFormatter formatter) {
        if (descriptor == null) {
            var string = formatter.format(object, ObjectFormatter.PART, null).toString();
            return new StringCell(string, object);
        }

        var col = 0;
        if (descriptor.part.format != null) {
            return new StringCell(descriptor.part.format.apply(object), object);
        }
        var sb = new StringBuilder();
        sb.append(descriptor.part.prefix);
        var del = "";
        for (ItemDescription item : descriptor.part.items.values()) {
            var o = getValue(object, item);
            var cell = cell(o, formatter);
            sb.append(del).append(cell);
            del = descriptor.part.separator;
        }
        sb.append(descriptor.part.suffix);
        return new StringCell(sb.toString(), object);
    }

    @Override
    public CharSequence format(Object o, int level, ObjectFormatter formatter) {
        while (o instanceof Wrapper) {
            o = ((Wrapper) o).whatever;
        }

        var c = cell(o, level, formatter);

        return toString(c);
    }

    Cell cell(Object o, int level, ObjectFormatter formatter) {

        if (o == null) {
            return new StringCell("null", null);
        }

        if (isSpecial(o)) {
            return cell(o, formatter);
        }

        var descriptor = getDescriptor(o.getClass());
        if (descriptor == null) {
            if (!(o instanceof DTO)) {
                return null;
            }
            try {
                return switch (level) {
                case ObjectFormatter.INSPECT -> inspect(o, formatter);
                case ObjectFormatter.LINE -> line(o, formatter);
                case ObjectFormatter.PART -> part(o, formatter);
                default -> null;
                };
            } catch (Exception e) {
                // TODO LOG
                return null;
            }
        }

        return switch (level) {
        case ObjectFormatter.INSPECT -> inspect(o, descriptor, formatter);
        case ObjectFormatter.LINE -> line(o, descriptor, formatter);
        case ObjectFormatter.PART -> part(o, descriptor, formatter);
        default -> null;
        };
    }

    final static String[] IDNAMES = { "id", "key", "name", "title" };

    private Cell part(Object o, ObjectFormatter formatter) throws Exception {
        Field primary = null;
        var priority = IDNAMES.length;

        for (Field f : o.getClass().getFields()) {
            if (isDTOField(f, o)) {
                continue;
            }

            for (var i = 0; i < priority; i++) {
                if (IDNAMES[i].equalsIgnoreCase(f.getName())) {
                    priority = i;
                    primary = f;
                }
            }
        }
        if (primary != null) {
            var v = primary.get(o);
            return cell(v, PART, formatter);
        }
        return null;
    }

    private Cell line(Object o, ObjectFormatter formatter) throws IllegalAccessException {
        var form = getCells(o, formatter, ObjectFormatter.PART);
        var table = new Table(1, form.size(), 0);
        var c = 0;
        for (Entry<String, Cell> e : form.entrySet()) {
            table.set(0, c++, e.getValue());
        }

        return table;
    }

    private Cell inspect(Object o, ObjectFormatter formatter) throws Exception {
        var form = getCells(o, formatter, ObjectFormatter.LINE);

        var table = new Table(form.size(), 2, 0);
        var r = 0;
        for (Entry<String, Cell> e : form.entrySet()) {
            table.set(r, 0, new StringCell(e.getKey(), e.getKey()));
            table.set(r++, 1, e.getValue());
        }

        return table;
    }

    private Map<String, Cell> getCells(Object o, ObjectFormatter formatter, int level) throws IllegalAccessException {
        Map<String, Cell> form = new TreeMap<>();
        for (Field f : o.getClass().getFields()) {
            if (!isDTOField(f, o)) {
                continue;
            }

            var value = f.get(o);
            var cell = cell(value, level, formatter);
            if (cell == null) {
                cell = new StringCell(Objects.toString(value), value);
            }
            form.put(f.getName(), cell);
        }
        return form;
    }

    private boolean isDTOField(Field f, Object o) {
        return Modifier.isStatic(f.getModifiers()) || f.isSynthetic() || !f.canAccess(o) || f.isEnumConstant();
    }

    private CharSequence toString(Cell cell) {
        if (cell == null) {
            return null;
        }

        var render = cell.render(cell.width(), cell.height());
        if (!boxes) {
            render = render.removeBoxes();
        }
        return render.toString();
    }

    private boolean isSpecial(Object o) {
        return o instanceof Map || o instanceof Dictionary || o instanceof Collection || o.getClass().isArray();
    }

    private DTODescription getDescriptor(Class<?> clazz, DTODescription defaultDescriptor) {
        var descriptor = getDescriptor(clazz);
        if (descriptor != null) {
            return descriptor;
        }
        return defaultDescriptor;
    }

    private DTODescription getDescriptor(Class<?> clazz) {
        var description = descriptors.get(clazz);
        if (description != null) {
            return description;
        }

        description = descriptors.entrySet().stream().filter(e -> e.getKey().isAssignableFrom(clazz))
                .map(Map.Entry::getValue).findAny().orElse(null);

        return description;
    }
}
