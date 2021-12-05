/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.
 */

/**
 * Test for checking the order of properties in AggregateError.
 *
 * @option ecmascript-version=2022
 */

const META_KEY = Symbol();

function defineMetadata(metadata) {
    return (value, ctx) => {
        ctx.setMetadata(META_KEY, metadata);
    };
    // return function(element) {
    //     element.elements.push({
    //         kind: 'field',
    //         placement: 'own',
    //         key: 'metadata',
    //         initialize: function() {
    //             return value;
    //         }
    //     });
    //     return element;
    // }
}

@defineMetadata({ test: 5 })
class C {}

5 === C[Symbol.metadata][META_KEY].constructor.test