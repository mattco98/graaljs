package com.oracle.truffle.js.test.decorators;

import com.oracle.truffle.js.lang.JavaScriptLanguage;
import com.oracle.truffle.js.runtime.JSContextOptions;
import com.oracle.truffle.js.test.JSTest;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class DummyTest extends DecoratorTest{
    @Test
    public void test() throws IOException  {
        File file = new File("/home/matthew/code/graaljs/graal-js/src/com.oracle.truffle.js.test/js/decorators/add_metadata.js");
        String sourceCode = new String(Files.readAllBytes(file.toPath()));

        try (Context context = JSTest.newContextBuilder().option(JSContextOptions.ECMASCRIPT_VERSION_NAME, "2022").build()) {
            Value result = context.eval(JavaScriptLanguage.ID, sourceCode);
            assert result.asBoolean();
        } catch (Exception ex) {
            Assert.fail("should not have thrown: " + ex.getMessage());
        }
    }
}
