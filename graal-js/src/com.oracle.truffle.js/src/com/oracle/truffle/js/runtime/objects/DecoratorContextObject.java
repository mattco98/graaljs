package com.oracle.truffle.js.runtime.objects;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.DynamicObjectFactory;
import com.oracle.truffle.api.object.DynamicObjectLibrary;
import com.oracle.truffle.js.nodes.access.PrivateFieldGetNode;
import com.oracle.truffle.js.nodes.decorators.PrivateName;
import com.oracle.truffle.js.nodes.function.JSBuiltin;
import com.oracle.truffle.js.nodes.function.JSBuiltinNode;
import com.oracle.truffle.js.runtime.Errors;
import com.oracle.truffle.js.runtime.JSContext;
import com.oracle.truffle.js.runtime.JSRealm;
import com.oracle.truffle.js.runtime.JSRuntime;
import com.oracle.truffle.js.runtime.JavaScriptRootNode;
import com.oracle.truffle.js.runtime.builtins.JSObjectFactory;
import com.oracle.truffle.js.runtime.builtins.JSOrdinary;
import com.oracle.truffle.js.runtime.builtins.JSSymbol;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class DecoratorContextObjectFactory {
    public static DynamicObject create(JSContext context, JSRealm realm, Kind kind, Object name, List<MetadataRecord> metadataList, boolean isStatic) {
        DynamicObject contextObj = JSOrdinary.create(context, realm);
        JSRuntime.createDataPropertyOrThrow(contextObj, "kind", kind.getJsStringValue());
        if (name instanceof PrivateName) {
            assert kind != Kind.Class;

            JSRuntime.createDataPropertyOrThrow(contextObj, "name", ((PrivateName) name).getName());
            JSRuntime.createDataPropertyOrThrow(contextObj, "isPrivate", true);
            JSRuntime.createDecorator
            JSRuntime.createDataPropertyOrThrow(contextObj, )
        }
    }

    private static DynamicObject createDecoratorAccessObject(JSContext context, JSRealm realm, Kind kind, Object name) {
        DynamicObject accessObj = JSOrdinary.create(context, realm);
        if (kind == Kind.Field || kind == Kind.Accessor || kind == Kind.Method || kind == Kind.Getter) {
//            CallTarget callTarget = new JavaScriptRootNode() {
//                @Child private
//                @Child private PrivateFieldGetNode getPrivateNameNode = PrivateFieldGetNode.create(accessObj, )
//
//                @Override
//                public Object execute(VirtualFrame frame) {
//                    return null;
//                }
//            }
        }
    }

    public abstract static class PrivateValueGetterBuiltin extends JSBuiltinNode {
        public PrivateValueGetterBuiltin(JSContext context, JSBuiltin builtin) {
            super(context, builtin);
        }

        @Specialization
        protected Object execute(Object thisObj) {

        }
    }

    public static class MetadataRecord {
        private JSSymbol key;
        private Object constructor;
        private Map<JSSymbol, Object> publicMetadata;
        private Map<JSSymbol, Object> privateMetadata;

        public MetadataRecord(JSSymbol key, Object constructor, Map<JSSymbol, Object> publicMetadata, Map<JSSymbol, Object> privateMetadata) {
            this.key = key;
            this.constructor = constructor;
            this.publicMetadata = publicMetadata;
            this.privateMetadata = privateMetadata;
        }


        public JSSymbol getKey() {
            return key;
        }

        public Object getConstructor() {
            return constructor;
        }

        public Map<JSSymbol, Object> getPublicMetadata() {
            return publicMetadata;
        }

        public Map<JSSymbol, Object> getPrivateMetadata() {
            return privateMetadata;
        }
    }

    public enum Kind {
        Class("class"),
        Field("field"),
        Accessor("auto-accessor"),
        Method("method"),
        Getter("getter"),
        Setter("setter");

        private String jsStringValue;

        Kind(String string) {
            this.jsStringValue = string;
        }

        public String getJsStringValue() {
            return jsStringValue;
        }

        public static Kind fromString(String kindString) {
            for (Kind kind : values()) {
                if (Objects.equals(kindString, kind.jsStringValue))
                    return kind;
            }

            throw Errors.shouldNotReachHere("Invalid kind \"" + kindString + "\"");
        }
    }
}
