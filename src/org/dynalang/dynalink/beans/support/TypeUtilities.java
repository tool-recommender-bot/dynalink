/*
   Copyright 2009 Attila Szegedi

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
package org.dynalang.dynalink.beans.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Various static utility methods for testing type relationships.
 * @author Attila Szegedi
 * @version $Id: $
 */
public class TypeUtilities
{
    static final Class<Object> OBJECT_CLASS = Object.class;

    /**
     * Given two types represented by c1 and c2, returns a type that is their
     * most specific common superclass or superinterface.
     * @param c1 one type
     * @param c2 another type
     * @return their most common superclass or superinterface. If they have
     * several unrelated superinterfaces as their most specific common type, or
     * the types themselves are completely unrelated interfaces,
     * {@link java.lang.Object} is returned.
     */
    public static Class<?> getMostSpecificCommonType(Class<?> c1, Class<?> c2) {
        if(c1 == c2) {
            return c1;
        }
        if(c2.isPrimitive()) {
            if(c2 == Byte.TYPE) c2 = Byte.class;
            else if(c2 == Short.TYPE) c2 = Short.class;
            else if(c2 == Character.TYPE) c2 = Character.class;
            else if(c2 == Integer.TYPE) c2 = Integer.class;
            else if(c2 == Float.TYPE) c2 = Float.class;
            else if(c2 == Long.TYPE) c2 = Long.class;
            else if(c2 == Double.TYPE) c2 = Double.class;
        }
        Set<Class<?>> a1 = getAssignables(c1, c2);
        Set<Class<?>> a2 = getAssignables(c2, c1);
        a1.retainAll(a2);
        if(a1.isEmpty()) {
            // Can happen when at least one of the arguments is an interface,
            // as they don't have Object at the root of their hierarchy.
            return Object.class;
        }
        // Gather maximally specific elements. Yes, there can be more than one
        // thank to interfaces. I.e., if you call this method for String.class
        // and Number.class, you'll have Comparable, Serializable, and Object
        // as maximal elements.
        List<Class<?>> max = new ArrayList<Class<?>>();
outer:  for (Class<?> clazz : a1) {
            for (Iterator<Class<?>> maxiter = max.iterator(); maxiter.hasNext();) {
                Class<?> maxClazz = maxiter.next();
                if(isSubtype(maxClazz, clazz)) {
                    // It can't be maximal, if there's already a more specific
                    // maximal than it.
                    continue outer;
                }
                if(isSubtype(clazz, maxClazz)) {
                    // If it's more specific than a currently maximal element,
                    // that currently maximal is no longer a maximal.
                    maxiter.remove();
                }
            }
            // If we get here, no current maximal is more specific than the
            // current class, so it is considered maximal as well
            max.add(clazz);
        }
        if(max.size() > 1) {
            return OBJECT_CLASS;
        }
        return max.get(0);
    }

    private static Set<Class<?>> getAssignables(Class<?> c1, Class<?> c2) {
        Set<Class<?>> s = new HashSet<Class<?>>();
        collectAssignables(c1, c2, s);
        return s;
    }

    private static void collectAssignables(Class<?> c1, Class<?> c2, Set<Class<?>> s) {
        if(c1.isAssignableFrom(c2)) {
            s.add(c1);
        }
        Class<?> sc = c1.getSuperclass();
        if(sc != null) {
            collectAssignables(sc, c2, s);
        }
        Class<?>[] itf = c1.getInterfaces();
        for(int i = 0; i < itf.length; ++i) {
            collectAssignables(itf[i], c2, s);
        }
    }

    private static final Map<Class<?>, Class<?>> WRAPPER_TYPES = createWrapperTypes();
    private static final Map<Class<?>, Class<?>> PRIMITIVE_TYPES = invertMap(WRAPPER_TYPES);

    private static Map<Class<?>, Class<?>> createWrapperTypes() {
        final Map<Class<?>, Class<?>> wrapperTypes =
            new IdentityHashMap<Class<?>, Class<?>>(8);
        wrapperTypes.put(Boolean.TYPE, Boolean.class);
        wrapperTypes.put(Byte.TYPE, Byte.class);
        wrapperTypes.put(Character.TYPE, Character.class);
        wrapperTypes.put(Short.TYPE, Short.class);
        wrapperTypes.put(Integer.TYPE, Integer.class);
        wrapperTypes.put(Long.TYPE, Long.class);
        wrapperTypes.put(Float.TYPE, Float.class);
        wrapperTypes.put(Double.TYPE, Double.class);
        return Collections.unmodifiableMap(wrapperTypes);
    }

    private static <K, V> Map<V, K> invertMap(Map<K, V> map) {
        final Map<V, K> inverted = new IdentityHashMap<V, K>(map.size());
        for (Map.Entry<K, V> entry : map.entrySet()) {
            inverted.put(entry.getValue(), entry.getKey());
        }
        return Collections.unmodifiableMap(inverted);
    }

    /**
     * Determines whether one type can be converted to another type using a
     * method invocation conversion, as per JLS 5.3 "Method Invocation
     * Conversion". This is basically all conversions allowed by subtyping (see
     * {@link #isSubtype(Class, Class)}) as well as boxing conversion
     * (JLS 5.1.7) optionally followed by widening reference conversion and
     * unboxing conversion (JLS 5.1.8) optionally followed by widening
     * primitive conversion.
     * @param callSiteType the parameter type at the call site
     * @param methodType the parameter type in the method declaration
     * @return true if callSiteType is method invocation convertible to the
     * methodType.
     */
    public static boolean isMethodInvocationConvertible(Class<?> callSiteType,
            Class<?> methodType)
    {
        if(methodType.isAssignableFrom(callSiteType)) {
            return true;
        }
        if(callSiteType.isPrimitive()) {
            if(methodType.isPrimitive()) {
                return isProperPrimitiveSubtype(callSiteType, methodType);
            }
            // Boxing + widening reference conversion
            return methodType.isAssignableFrom(WRAPPER_TYPES.get(callSiteType));
        }
        if(methodType.isPrimitive()) {
            final Class<?> unboxedCallSiteType = PRIMITIVE_TYPES.get(
                    callSiteType);
            return unboxedCallSiteType != null &&
            (unboxedCallSiteType == methodType || isProperPrimitiveSubtype(
                    unboxedCallSiteType, methodType));
        }
        return false;
    }

    /**
     * Determines whether one type can be potentially converted to another
     * type at runtime. Allows a conversion between any subtype and supertype
     * in either direction, and also allows a conversion between any two
     * primitive types, as well as between any primitive type and any reference
     * type that can hold a boxed primitive.
     * @param callSiteType the parameter type at the call site
     * @param methodType the parameter type in the method declaration
     * @return true if callSiteType is potentially convertible to the
     * methodType.
     */
    public static boolean isPotentiallyConvertible(
            Class<?> callSiteType, Class<?> methodType)
    {
        // Widening or narrowing reference conversion
        if(methodType.isAssignableFrom(callSiteType) ||
                callSiteType.isAssignableFrom(methodType)) {
            return true;
        }
        if(callSiteType.isPrimitive()) {
            // Allow any conversion among primitives, as well as from any
            // primitive to any type that can receive a boxed primitive.
            // TODO: narrow this a bit, i.e. allow, say, boolean to Character?
            // MethodHandles.convertArguments() allows it, so we might need to
            // too.
            return methodType.isPrimitive() || isAssignableFromBoxedPrimitive(
                    methodType);
        }
        if(methodType.isPrimitive()) {
            // Allow conversion from any reference type that can contain a
            // boxed primitive to any primitive.
            // TODO: narrow this a bit too?
            return isAssignableFromBoxedPrimitive(callSiteType);
        }
        return false;
    }

    /**
     * Determines whether one type is a subtype of another type, as per JLS
     * 4.10 "Subtyping". Note: this is not strict or proper subtype, therefore
     * true is also returned for identical types; to be completely precise, it
     * allows identity conversion (JLS 5.1.1), widening primitive conversion
     * (JLS 5.1.2) and widening reference conversion (JLS 5.1.5).
     * @param subType the supposed subtype
     * @param superType the supposed supertype of the subtype
     * @return true if subType can be converted by identity conversion,
     * widening primitive conversion, or widening reference conversion to
     * superType.
     */
    public static boolean isSubtype(Class<?> subType, Class<?> superType) {
        // Covers both JLS 4.10.2 "Subtyping among Class and Interface Types"
        // and JLS 4.10.3 "Subtyping among Array Types", as well as primitive
        // type identity.
        if(superType.isAssignableFrom(subType)) {
            return true;
        }
        // JLS 4.10.1 "Subtyping among Primitive Types". Note we don't test for
        // identity, as identical types were taken care of in the
        // isAssignableFrom test. As per 4.10.1, the supertype relation is as
        // follows:
        // double > float
        // float > long
        // long > int
        // int > short
        // int > char
        // short > byte
        if(superType.isPrimitive() && subType.isPrimitive()) {
            return isProperPrimitiveSubtype(subType, superType);
        }
        return false;
    }

    /**
     * Returns true if a supposed primitive subtype is a proper subtype (
     * meaning, subtype and not identical) of the supposed primitive supertype
     * @param subType the supposed subtype
     * @param superType the supposed supertype
     * @return true if subType is a proper (not identical to) primitive subtype
     * of the superType
     */
    public static boolean isProperPrimitiveSubtype(Class<?> subType, Class<?> superType) {
        if(superType == boolean.class || subType == boolean.class) {
            return false;
        }
        if(subType == byte.class) {
            return superType != char.class;
        }
        if(subType == char.class) {
            return superType != short.class && superType != byte.class;
        }
        if(subType == short.class) {
            return superType != char.class && superType != byte.class;
        }
        if(subType == int.class) {
            return superType == long.class || superType == float.class ||
            superType == double.class;
        }
        if(subType == long.class) {
            return superType == float.class || superType == double.class;
        }
        if(subType == float.class) {
            return superType == double.class;
        }
        return false;
    }

    private static final Set<Class<?>> PRIMITIVE_WRAPPER_TYPES = createPrimitiveWrapperTypes();

    private static Set<Class<?>> createPrimitiveWrapperTypes() {
        final Map<Class<?>, Class<?>> classes = new IdentityHashMap<Class<?>, Class<?>>();
        addClassHierarchy(classes, Boolean.class);
        addClassHierarchy(classes, Byte.class);
        addClassHierarchy(classes, Character.class);
        addClassHierarchy(classes, Short.class);
        addClassHierarchy(classes, Integer.class);
        addClassHierarchy(classes, Float.class);
        addClassHierarchy(classes, Long.class);
        addClassHierarchy(classes, Double.class);
        return classes.keySet();
    }

    private static void addClassHierarchy(Map<Class<?>, Class<?>> map, Class<?> clazz) {
        if(clazz == null) {
            return;
        }
        map.put(clazz, clazz);
        addClassHierarchy(map, clazz.getSuperclass());
        for (Class<?> itf : clazz.getInterfaces()) {
            addClassHierarchy(map, itf);
        }
    }

    /**
     * Returns true if the class can be assigned from any boxed primitive.
     * @param clazz the class
     * @return true if the class can be assigned from any boxed primitive.
     * Basically, it is true if the class is any primitive wrapper class, or
     * a superclass or superinterface of any primitive wrapper class.
     */
    public static boolean isAssignableFromBoxedPrimitive(Class<?> clazz) {
        return PRIMITIVE_WRAPPER_TYPES.contains(clazz);
    }
}