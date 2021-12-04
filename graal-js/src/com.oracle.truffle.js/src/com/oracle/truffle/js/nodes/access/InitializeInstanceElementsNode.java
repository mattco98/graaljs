/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.js.nodes.access;

import java.util.Set;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Executed;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.instrumentation.Tag;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.ExplodeLoop.LoopExplosionKind;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.HiddenKey;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.js.nodes.JavaScriptBaseNode;
import com.oracle.truffle.js.nodes.JavaScriptNode;
import com.oracle.truffle.js.nodes.decorators.ClassElementList;
import com.oracle.truffle.js.nodes.decorators.ElementDescriptor;
import com.oracle.truffle.js.nodes.decorators.PrivateName;
import com.oracle.truffle.js.nodes.function.JSFunctionCallNode;
import com.oracle.truffle.js.runtime.Errors;
import com.oracle.truffle.js.runtime.JSArguments;
import com.oracle.truffle.js.runtime.JSContext;
import com.oracle.truffle.js.runtime.JSRuntime;
import com.oracle.truffle.js.runtime.builtins.JSFunction;
import com.oracle.truffle.js.runtime.objects.PropertyDescriptor;
import com.oracle.truffle.js.runtime.objects.Undefined;

/**
 * InitializeInstanceElements (O, constructor).
 *
 * Defines class instance fields using the provided field records from the constructor. For fields
 * with an initializer, the initializer function is called to obtain the initial value and, if it's
 * an anonymous function definition, its function name is set to the (computed) field name. Also
 * performs PrivateBrandAdd(O, constructor.[[PrivateBrand]]) if the brand is not undefined.
 *
 * Relies on the following per-class invariants:
 * <ul>
 * <li>The number of instance fields is constant.
 * <li>For each field index, the key will either always or never be a private name.
 * <li>For each field index, an initializer will either always or never be present.
 * <li>For each field index, [[IsAnonymousFunctionDefinition]] will never change.
 * <li>The [[Fields]] slot will either always or never be present.
 * <li>The [[PrivateBrand]] slot will either always or never be present.
 * </ul>
 *
 * This node is also used to define static fields ({@link #executeStaticElements}).
 */
public abstract class InitializeInstanceElementsNode extends JavaScriptNode {
    @Child @Executed protected JavaScriptNode targetNode;
    @Child @Executed protected JavaScriptNode constructorNode;
    @Child @Executed(with = "constructorNode") protected JSTargetableNode fieldsNode;
    @Child @Executed(with = "constructorNode") protected JSTargetableNode brandNode;
    @Child @Executed(with = "constructorNode") protected JSTargetableNode elementsNode;

    protected final JSContext context;

    protected InitializeInstanceElementsNode(JSContext context, JavaScriptNode targetNode, JavaScriptNode constructorNode) {
        this.context = context;
        this.targetNode = targetNode;
        this.constructorNode = constructorNode;
        if (constructorNode != null) {
            this.fieldsNode = PropertyNode.createGetHidden(context, null, JSFunction.CLASS_FIELDS_ID);
            this.elementsNode = PropertyNode.createGetHidden(context, null, JSFunction.ELEMENTS_ID);
            this.brandNode = PropertyNode.createGetHidden(context, null, JSFunction.PRIVATE_BRAND_ID);
        }
    }

    public static JavaScriptNode create(JSContext context, JavaScriptNode targetNode, JavaScriptNode constructorNode) {
        return InitializeInstanceElementsNodeGen.create(context, targetNode, constructorNode);
    }

    public static InitializeInstanceElementsNode create(JSContext context) {
        return InitializeInstanceElementsNodeGen.create(context, null, null);
    }

    public final Object executeFields(Object proto, Object constructor, ElementDescriptor[] fields) {
        return executeEvaluated(proto, constructor, fields, Undefined.instance, null);
    }

    public final Object executeStaticElements(Object targetConstructor, Object[][] staticElements) {
        return executeEvaluated(targetConstructor, Undefined.instance, staticElements, Undefined.instance, null);
    }

    protected abstract Object executeEvaluated(Object target, Object constructor, Object fields, Object brand, ClassElementList elements);

    @ExplodeLoop(kind = LoopExplosionKind.FULL_UNROLL)
    @Specialization
    protected static Object withStaticAndPrototypeField(Object target, Object constructor, ElementDescriptor[] fields, Object brand, Object elements,
                                                        @Cached("createStaticAndPrototypeFieldNodes(fields, context)") DefineFieldNode[] fieldNodes) {
        int i = 0;
        for(ElementDescriptor element: fields) {
            if(element.isField()) {
                Object receiver = element.isStatic() ? constructor : target;
                fieldNodes[i++].defineDecoratorField(receiver, element);
            }
        }
        return target;
    }
    @ExplodeLoop(kind = LoopExplosionKind.FULL_UNROLL)
    @Specialization
    protected static Object withOwnFields(Object target, Object constructor, Object fields, Object brand, ClassElementList elements,
                                          @Cached("createBrandAddNode(brand, context)") @Shared("privateBrandAdd") PrivateFieldAddNode privateBrandAddNode,
                                          @Cached("createOwnFieldNodes(elements, context)") DefineFieldNode[] fieldNodes, @Cached("createStartHookNodes(elements)") ExecuteStartHookNode[] startHookNodes) {
        if(elements.setInstanceBand()) {
            privateBrandAdd(target, constructor, fields, brand, elements, privateBrandAddNode);
        }

        int fieldIndex = 0;
        int startHookIndex = 0;
        for (ElementDescriptor element: elements.getOwnElements()) {
            if((element.isMethod() || element.isAccessor()) && JSRuntime.isPropertyKey(element.getKey())) {
                JSRuntime.definePropertyOrThrow((DynamicObject) target, element.getKey(), element.getDescriptor());
            }
        }
        for(ElementDescriptor element: elements.getOwnElements()) {
            if (element.isField()) {
                fieldNodes[fieldIndex++].defineDecoratorField(target, element);
            }
            if (element.isHook()) {
                startHookNodes[startHookIndex++].execute(target, element);
            }
        }
        return target;
    }


    @ExplodeLoop(kind = LoopExplosionKind.FULL_UNROLL)
    @Specialization
    protected static Object withFields(Object target, Object constructor, Object[][] fields, Object brand, Object elements,
                    @Cached("createBrandAddNode(brand, context)") @Shared("privateBrandAdd") PrivateFieldAddNode privateBrandAddNode,
                    @Cached("createFieldNodes(fields, context)") DefineFieldNode[] fieldNodes) {
        privateBrandAdd(target, constructor, fields, brand, elements, privateBrandAddNode);

        int size = fieldNodes.length;
        assert size == fields.length;
        for (int i = 0; i < size; i++) {
            Object[] field = fields[i];
            Object key = field[0];
            Object initializer = field[1];
            fieldNodes[i].defineField(target, key, initializer);
        }
        return target;
    }

    @Specialization
    protected static Object privateBrandAdd(Object target, Object constructor, @SuppressWarnings("unused") Object fields, Object brand, Object elements,
                    @Cached("createBrandAddNode(brand, context)") @Shared("privateBrandAdd") PrivateFieldAddNode privateBrandAddNode) {
        // If constructor.[[PrivateBrand]] is not undefined,
        // Perform ? PrivateBrandAdd(O, constructor.[[PrivateBrand]]).
        assert (privateBrandAddNode != null) == (brand != Undefined.instance);
        if (privateBrandAddNode != null) {
            privateBrandAddNode.execute(target, brand, constructor);
        }
        return target;
    }

    @Override
    protected JavaScriptNode copyUninitialized(Set<Class<? extends Tag>> materializedTags) {
        return create(context, cloneUninitialized(targetNode, materializedTags), cloneUninitialized(constructorNode, materializedTags));
    }

    static PrivateFieldAddNode createBrandAddNode(Object brand, JSContext context) {
        CompilerAsserts.neverPartOfCompilation();
        if (brand != Undefined.instance) {
            return PrivateFieldAddNode.create(context);
        } else {
            return null;
        }
    }

    static DefineFieldNode[] createFieldNodes(Object[][] fields, JSContext context) {
        CompilerAsserts.neverPartOfCompilation();
        int size = fields.length;
        DefineFieldNode[] fieldNodes = new DefineFieldNode[size];
        for (int i = 0; i < size; i++) {
            Object[] field = fields[i];
            Object key = field[0];
            Object initializer = field[1];
            boolean isAnonymousFunctionDefinition = (boolean) field[2];
            JavaScriptBaseNode writeNode = null;
            if (key instanceof HiddenKey) {
                writeNode = PrivateFieldAddNode.create(context);
            } else if (key != null) {
                writeNode = WriteElementNode.create(context, true, true);
            }
            JSFunctionCallNode callNode = null;
            if (initializer != Undefined.instance) {
                callNode = JSFunctionCallNode.createCall();
            }
            fieldNodes[i] = new DefineFieldNode(writeNode, callNode, isAnonymousFunctionDefinition);
        }
        return fieldNodes;
    }

    static DefineFieldNode[] createOwnFieldNodes(ClassElementList elements, JSContext context) {
        CompilerAsserts.neverPartOfCompilation();
        int size = elements.getOwnFieldCount();
        return createDecoratorFieldNodes(elements.getOwnElementsArray(), context, size);
    }

    static DefineFieldNode[] createStaticAndPrototypeFieldNodes(ElementDescriptor[] elements, JSContext context) {
        CompilerAsserts.neverPartOfCompilation();
        return createDecoratorFieldNodes(elements, context, elements.length);
    }

    static DefineFieldNode[] createDecoratorFieldNodes(ElementDescriptor[] elements, JSContext context, int size) {
        CompilerAsserts.neverPartOfCompilation();
        DefineFieldNode[] fieldNodes = new DefineFieldNode[size];
        int fieldNodeCount = 0;
        for(ElementDescriptor element : elements) {
            if(element.isField()) {
                Object key = element.getKey();
                Object initializer = element.getInitialize();
                JavaScriptBaseNode writeNode = null;
                if (key instanceof PrivateName) {
                    writeNode = PrivateFieldAddNode.create(context);
                }
                JSFunctionCallNode callNode = null;
                if (initializer != Undefined.instance) {
                    callNode = JSFunctionCallNode.createCall();
                }
                fieldNodes[fieldNodeCount++] = new DefineFieldNode(writeNode, callNode, false);
            }
        }
        return fieldNodes;
    }

    static ExecuteStartHookNode[] createStartHookNodes(ClassElementList elements) {
        CompilerAsserts.neverPartOfCompilation();
        int size = elements.getOwnHookStartCount();
        ExecuteStartHookNode[] startHookNodes = new ExecuteStartHookNode[size];
        int i = 0;
        for(ElementDescriptor element: elements.getOwnElements()) {
            if(element.isHook()) {
                assert element.hasStart();
                startHookNodes[i++] = new ExecuteStartHookNode();
            }
        }
        return startHookNodes;
    }

    static final class DefineFieldNode extends JavaScriptBaseNode {
        @Child JavaScriptBaseNode writeNode;
        @Child JSFunctionCallNode callNode;
        private final boolean isAnonymousFunctionDefinition;

        DefineFieldNode(JavaScriptBaseNode writeNode, JSFunctionCallNode callNode, boolean isAnonymousFunctionDefinition) {
            this.writeNode = writeNode;
            this.callNode = callNode;
            this.isAnonymousFunctionDefinition = isAnonymousFunctionDefinition;
        }

        void defineField(Object target, Object key, Object initializer) {
            assert (callNode != null) == (initializer != Undefined.instance);
            Object value = Undefined.instance;
            if (callNode != null) {
                value = callNode.executeCall(isAnonymousFunctionDefinition ? JSArguments.createOneArg(target, initializer, key) : JSArguments.createZeroArg(target, initializer));
            }
            if (writeNode instanceof PrivateFieldAddNode) {
                assert key instanceof HiddenKey : key;
                ((PrivateFieldAddNode) writeNode).execute(target, key, value);
            } else if (writeNode != null) {
                assert JSRuntime.isPropertyKey(key) : key;
                ((WriteElementNode) writeNode).executeWithTargetAndIndexAndValue(target, key, value);
            }
        }

        void defineDecoratorField(Object target, ElementDescriptor desc) {
            Object key = desc.getKey();
            Object initValue = Undefined.instance;
            if(callNode != null && desc.hasInitialize()) {
                initValue = callNode.executeCall(JSArguments.createZeroArg(target, desc.getInitialize()));
            }
            PropertyDescriptor dataDescriptor = desc.getDescriptor();
            assert dataDescriptor.isDataDescriptor();
            dataDescriptor.setValue(initValue);
            if(writeNode instanceof PrivateFieldAddNode) {
                assert key instanceof PrivateName : key;
                ((PrivateFieldAddNode) writeNode).execute(target, ((PrivateName) key).getHiddenKey(), initValue);
            } else {
                assert JSRuntime.isPropertyKey(key) : key;
                JSRuntime.definePropertyOrThrow((DynamicObject) target, key, dataDescriptor);
            }
        }
    }

    static final class ExecuteStartHookNode extends JavaScriptBaseNode {
        private final BranchProfile errorBranch = BranchProfile.create();
        @Child JSFunctionCallNode callNode;

        ExecuteStartHookNode() {
            callNode = JSFunctionCallNode.createCall();
        }

        void execute(Object target, ElementDescriptor desc) {
            assert desc.hasStart();
            Object res = callNode.executeCall(JSArguments.createZeroArg(target, desc.getStart()));
            if(res != Undefined.instance) {
                errorBranch.enter();
                throw Errors.createTypeErrorHookReturnValue("Start",this);
            }
        }
    }
}
